package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.data.PlayerKitData;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 礼包管理器
 * 处理礼包购买、领取、数据管理等功能
 * 
 * @author AngelHell
 */
public class KitManager {
    
    private final SkyPVPKits plugin;
    private final ConfigManager configManager;
    
    // 礼包类型常量
    public static final List<String> KIT_TYPES = Arrays.asList(
        "sky", "mars", "earth", "moon", "sun", "mining", "galaxy"
    );
    
    // 玩家礼包缓存
    private final Map<String, List<PlayerKitData>> playerKitCache = new ConcurrentHashMap<>();
    
    // 线程池管理
    private final ScheduledExecutorService executorService;
    private final int MAX_THREADS = 4; // 最大线程数
    
    // 处理限制 - 防止CPU占用过高
    private final Map<String, AtomicInteger> playerProcessingCount = new ConcurrentHashMap<>();
    private final int MAX_CONCURRENT_OPERATIONS_PER_PLAYER = 3; // 每个玩家最多同时3个操作
    private final AtomicInteger globalProcessingCount = new AtomicInteger(0);
    private final int MAX_GLOBAL_OPERATIONS = 50; // 全局最多50个操作
    
    public KitManager(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        
        // 初始化线程池
        this.executorService = Executors.newScheduledThreadPool(MAX_THREADS, r -> {
            Thread thread = new Thread(r, "SkyPVPKits-AsyncWorker");
            thread.setDaemon(true);
            return thread;
        });
        
        plugin.getLogger().info("KitManager initialized with " + MAX_THREADS + " async worker threads");
    }
    
    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 检查是否可以执行操作（防止CPU占用过高）
     */
    private boolean canProcessOperation(String playerKey) {
        // 检查全局操作数量
        if (globalProcessingCount.get() >= MAX_GLOBAL_OPERATIONS) {
            return false;
        }
        
        // 检查玩家操作数量
        AtomicInteger playerCount = playerProcessingCount.computeIfAbsent(playerKey, k -> new AtomicInteger(0));
        return playerCount.get() < MAX_CONCURRENT_OPERATIONS_PER_PLAYER;
    }
    
    /**
     * 开始操作计数
     */
    private void startOperation(String playerKey) {
        globalProcessingCount.incrementAndGet();
        playerProcessingCount.computeIfAbsent(playerKey, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 结束操作计数
     */
    private void endOperation(String playerKey) {
        globalProcessingCount.decrementAndGet();
        AtomicInteger playerCount = playerProcessingCount.get(playerKey);
        if (playerCount != null) {
            int newCount = playerCount.decrementAndGet();
            if (newCount <= 0) {
                playerProcessingCount.remove(playerKey);
            }
        }
    }
    
    /**
     * 异步执行操作
     */
    private <T> CompletableFuture<T> executeAsync(String playerKey, java.util.function.Supplier<T> operation) {
        if (!canProcessOperation(playerKey)) {
            return CompletableFuture.completedFuture(null);
        }
        
        startOperation(playerKey);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.get();
            } finally {
                endOperation(playerKey);
            }
        }, executorService);
    }
    
    /**
     * 重载管理器
     */
    public void reload() {
        // 清除缓存
        playerKitCache.clear();
        plugin.getLogger().info("KitManager reloaded");
    }
    
    /**
     * 异步获取玩家礼包数据
     */
    public CompletableFuture<List<PlayerKitData>> getPlayerKitDataAsync(Player player) {
        String playerKey = player.getUniqueId().toString();
        
        // 检查缓存
        if (playerKitCache.containsKey(playerKey)) {
            return CompletableFuture.completedFuture(playerKitCache.get(playerKey));
        }
        
        // 使用自定义线程池异步获取
        return executeAsync(playerKey, () -> {
            try {
                List<PlayerKitData> dataList = plugin.getDatabaseManager().getPlayerKitDataAsync(player.getUniqueId()).get();
                // 更新缓存
                playerKitCache.put(playerKey, dataList);
                return dataList;
            } catch (Exception e) {
                plugin.getLogger().warning("获取玩家礼包数据失败: " + e.getMessage());
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * 检查玩家是否拥有指定礼包等级
     */
    public CompletableFuture<Boolean> hasKitLevel(Player player, String kitType, int level) {
        return getPlayerKitDataAsync(player).thenApply(dataList -> {
            return dataList.stream()
                    .anyMatch(data -> data.getKitType().equals(kitType) 
                                   && data.getKitLevel() == level 
                                   && !data.isExpired());
        });
    }
    
    /**
     * 通过UUID检查玩家是否拥有指定礼包等级（支持离线玩家）
     */
    public CompletableFuture<Boolean> hasKitLevelByUUID(java.util.UUID playerUUID, String kitType, int level) {
        return plugin.getDatabaseManager().getPlayerKitDataAsync(playerUUID).thenApply(dataList -> {
            return dataList.stream()
                    .anyMatch(data -> data.getKitType().equals(kitType) 
                                   && data.getKitLevel() == level 
                                   && !data.isExpired());
        });
    }
    
    /**
     * 获取玩家在指定礼包类型中的最高等级
     */
    public CompletableFuture<Integer> getPlayerMaxLevel(Player player, String kitType) {
        return getPlayerKitDataAsync(player).thenApply(dataList -> {
            return dataList.stream()
                    .filter(data -> data.getKitType().equals(kitType) && !data.isExpired())
                    .mapToInt(PlayerKitData::getKitLevel)
                    .max()
                    .orElse(0);
        });
    }
    
    /**
     * 检查玩家是否可以购买指定礼包等级
     */
    public CompletableFuture<Boolean> canPurchaseLevel(Player player, String kitType, int level) {
        return getPlayerMaxLevel(player, kitType).thenApply(maxLevel -> {
            // 检查是否已经拥有该等级或更高等级
            if (maxLevel >= level) {
                return false;
            }
            
            // 检查是否满足前置等级要求（必须按顺序购买）
            // 对于等级1，不需要前置条件 (maxLevel >= 0)
            // 对于等级2，需要拥有等级1 (maxLevel >= 1)
            // 对于等级3，需要拥有等级2 (maxLevel >= 2)
            return maxLevel >= level - 1;
        });
    }
    
    /**
     * 检查是否支持硬币购买（仅天空和火星的1、2级）
     */
    private boolean supportsCoinsPurchase(String kitType, int level) {
        return (kitType.equals("sky") || kitType.equals("mars")) && (level == 1 || level == 2);
    }
    
    /**
     * 检查是否支持月付购买
     */
    public boolean supportsMonthlyPurchase(String kitType, int level) {
        // 太阳礼包不支持月付
        if (kitType.equals("sun")) {
            return false;
        }
        
        // 银河礼包不支持月付
        if (kitType.equals("galaxy")) {
            return false;
        }
        
        // 挖矿礼包不支持月付
        if (kitType.equals("mining")) {
            return false;
        }
        
        // 其他礼包（天空、火星、地球、月球）只支持等级1和等级2的月付
        return level == 1 || level == 2;
    }
    
    /**
     * 获取硬币价格（点券价格×100，仅限天空火星1-2级）
     */
    private int getCoinsPrice(String kitType, int level) {
        if (!supportsCoinsPurchase(kitType, level)) {
            return -1; // 不支持硬币购买
        }
        
        int pointsPrice = getPermanentPrice(kitType, level);
        return pointsPrice * 100;
    }

    /**
     * 购买礼包（永久）- 左键点击，点券购买
     */
    public CompletableFuture<PurchaseResult> purchaseKitPermanent(Player player, String kitType, int level) {
        return canPurchaseLevel(player, kitType, level).thenCompose(canPurchase -> {
            if (!canPurchase) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "cannot_purchase", "无法购买此等级")
                );
            }
            
            // 检查是否支持点券购买
            if (!configManager.isKitSupportPoints(kitType)) {
                String contactQQ = configManager.getContactQQ();
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "not_support_points", "该礼包不支持点券购买，请联系QQ: " + contactQQ)
                );
            }
            
            // 获取价格
            int price = getPermanentPrice(kitType, level);
            if (price < 0) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "invalid_price", "价格配置错误")
                );
            }
            
            // 如果价格为0（免费），直接给予礼包
            if (price == 0) {
                return createAndSaveKitData(player, kitType, level, true, 0, "FREE");
            }
            
            // 检查并扣除点券
            return checkAndDeductPoints(player, price).thenCompose(paid -> {
                if (!paid) {
                    return CompletableFuture.completedFuture(
                        new PurchaseResult(false, "insufficient_points", "点券余额不足", price)
                    );
                }
                
                // 创建礼包数据
                return createAndSaveKitData(player, kitType, level, true, price, "POINTS");
            });
        });
    }
    
    /**
     * 硬币购买礼包（月付）- 硬币购买
     */
    public CompletableFuture<PurchaseResult> purchaseKitMonthlyWithCoins(Player player, String kitType, int level) {
        return canPurchaseLevel(player, kitType, level).thenCompose(canPurchase -> {
            if (!canPurchase) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "cannot_purchase", "无法购买此等级")
                );
            }
            
            // 检查是否支持月付购买
            if (!supportsMonthlyPurchase(kitType, level)) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "monthly_not_supported", "该礼包等级不支持月付购买")
                );
            }
            
            // 硬币月付价格 = 永久价格的点券 × 100 (1块 = 100点券)
            int permanentPrice = getPermanentPrice(kitType, level);
            int coinsPrice = permanentPrice * 100;
            
            // 检查价格是否有效
            if (coinsPrice <= 0) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "invalid_price", "月付价格配置错误")
                );
            }
            
            // 检查硬币余额并扣费
            return checkAndDeductCoins(player, coinsPrice).thenCompose(paid -> {
                if (!paid) {
                    return CompletableFuture.completedFuture(
                        new PurchaseResult(false, "insufficient_money", "硬币余额不足", coinsPrice)
                    );
                }
                
                // 硬币月付购买成功，创建礼包数据
                return createAndSaveKitData(player, kitType, level, false, coinsPrice, "COINS");
            });
        });
    }
    
    /**
     * 领取礼包奖励
     */
    public CompletableFuture<ClaimResult> claimKitReward(Player player, String kitType, int level) {
        String playerKey = player.getUniqueId().toString();
        
        return executeAsync(playerKey, () -> {
            try {
                // 检查是否拥有该礼包
                Boolean hasKit = hasKitLevel(player, kitType, level).get();
                if (!hasKit) {
                    return new ClaimResult(false, "not_owned", "你还没有购买这个礼包");
                }
                
                List<PlayerKitData> dataList = getPlayerKitDataAsync(player).get();
                
                // 检查是否拥有更高等级的礼包（如果有，禁止领取低等级）
                int maxLevel = dataList.stream()
                        .filter(data -> data.getKitType().equals(kitType) && !data.isExpired())
                        .mapToInt(PlayerKitData::getKitLevel)
                        .max()
                        .orElse(0);
                
                if (maxLevel > level) {
                    return new ClaimResult(false, "higher_level_owned", 
                        "你已拥有" + kitType + "礼包等级" + maxLevel + "，无法领取等级" + level + "的奖励");
                }
                
                PlayerKitData kitData = dataList.stream()
                        .filter(data -> data.getKitType().equals(kitType) 
                                     && data.getKitLevel() == level 
                                     && !data.isExpired())
                        .findFirst()
                        .orElse(null);
                
                if (kitData == null) {
                    return new ClaimResult(false, "not_found", "礼包数据未找到");
                }
                
                // 检查是否已经领取过
                if (kitData.hasClaimedToday()) {
                    return new ClaimResult(false, "already_claimed", "今天已经领取过了");
                }
                
                // 标记为已领取
                kitData.markClaimedToday();
                
                // 保存数据
                Boolean saved = plugin.getDatabaseManager().savePlayerKitDataAsync(kitData).get();
                if (saved) {
                    // 在主线程中执行奖励命令
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        executeKitRewards(player, kitType, level);
                    });
                    
                    // 清除缓存
                    playerKitCache.remove(player.getUniqueId().toString());
                    return new ClaimResult(true, "success", "领取成功");
                } else {
                    return new ClaimResult(false, "database_error", "数据库错误");
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning("领取礼包奖励失败: " + e.getMessage());
                return new ClaimResult(false, "error", "处理过程中发生错误");
            }
        }).thenApply(result -> result != null ? result : new ClaimResult(false, "busy", "服务器繁忙，请稍后再试"));
    }
    
    /**
     * 执行礼包奖励
     */
    private void executeKitRewards(Player player, String kitType, int level) {
        String playerName = player.getName();
        
        // 根据礼包类型给予装备
        giveKitEquipment(player, kitType, level);
        
        // 特殊金苹果奖励
        giveSpecialApples(player, kitType, level);
    }
    
    /**
     * 给予礼包装备
     */
    private void giveKitEquipment(Player player, String kitType, int level) {
        String playerName = player.getName();
        
        switch (kitType) {
            case "sky":
                // 天空系列：1天空剑, 1天空头盔, 1天空胸甲, 1天空护腿, 1天空鞋子
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "天空剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "天空头盔");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "天空胸甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "天空护腿");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "天空鞋子");
                break;
                
            case "mars":
                // 火星系列：1火星spark剑, 1火星spark头盔, 等
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "火星spark剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "火星spark头盔");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "火星spark胸甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "火星spark护腿");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "火星spark鞋子");
                break;
                
            case "earth":
                // 地球系列：地球1_剑, 地球1_头, 地球1_甲, 地球1_腿, 地球1_鞋
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 地球" + level + "_剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 地球" + level + "_头");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 地球" + level + "_甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 地球" + level + "_腿");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 地球" + level + "_鞋");
                break;
                
            case "moon":
                // 月球系列：1月球moon剑, 1月球moon头盔, 等
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "月球moon剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "月球moon头盔");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "月球moon胸甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "月球moon护腿");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "月球moon鞋子");
                break;
                
            case "sun":
                // 太阳系列：1太阳sun剑, 1太阳sun头盔, 等
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "太阳sun剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "太阳sun头盔");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "太阳sun胸甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "太阳sun护腿");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "太阳sun鞋子");
                break;
                
            case "mining":
                // 挖矿系列：1挖矿mining稿
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " " + level + "挖矿mining稿");
                break;
                
            case "galaxy":
                // 新银河系列：新银河1_剑, 新银河1_头盔, 等
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 新银河" + level + "_剑");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 新银河" + level + "_头盔");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 新银河" + level + "_胸甲");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 新银河" + level + "_裤");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ezitem give " + playerName + " 新银河" + level + "_鞋");
                break;
        }
    }
    
    /**
     * 给予特殊金苹果
     */
    private void giveSpecialApples(Player player, String kitType, int level) {
        switch (kitType) {
            case "moon":
                // 月球金苹果 - 初始2个，每级+2个
                int moonApples = 2 + (2 * level);
                giveGoldenApples(player, moonApples, true);
                break;
                
            case "sun":
                // 太阳金苹果 - 初始4个，每级+4个
                int sunApples = 4 + (4 * level);
                giveGoldenApples(player, sunApples, true);
                break;
                
            case "galaxy":
                // 银河金苹果 - 使用附魔金苹果
                int galaxyApples = 16 + (5 * level);
                giveGoldenApples(player, galaxyApples, true);
                break;
                
            case "earth":
                // 地球金苹果 - 固定1个
                giveGoldenApples(player, 1, false);
                break;
                
            case "mars":
                // 火星金苹果 - 固定1个
                giveGoldenApples(player, 1, true);
                break;
                
            case "mining":
                // 挖矿金苹果 - 固定1个
                giveGoldenApples(player, 1, true);
                break;
        }
        
        // 给予药水奖励
        givePotionRewards(player, kitType, level);
    }
    
    /**
     * 给予金苹果
     */
    private void giveGoldenApples(Player player, int amount, boolean enchanted) {
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, amount);
        
        if (enchanted) {
            // 设置为附魔金苹果
            apple.setDurability((short) 1);
        }
        
        // 检查玩家背包空间
        if (player.getInventory().firstEmpty() == -1) {
            // 背包满了，掉落在地上
            player.getWorld().dropItemNaturally(player.getLocation(), apple);
            player.sendMessage("§e你的背包已满，金苹果已掉落在地上！");
        } else {
            player.getInventory().addItem(apple);
        }
    }
    
    /**
     * 给予药水奖励
     */
    private void givePotionRewards(Player player, String kitType, int level) {
        switch (kitType) {
            case "mining":
                // 挖矿3级给力量3药水5个（1分30秒）
                if (level >= 3) {
                    giveStrengthPotion(player, 3, 5, 90); // 力量3，5个，90秒
                }
                break;
                
            case "sun":
                // 太阳给力量2药水4个
                giveStrengthPotion(player, 2, 4, 90); // 力量2，4个，90秒
                break;
                
            case "moon":
                // 月球给力量2药水2个
                giveStrengthPotion(player, 2, 2, 90); // 力量2，2个，90秒
                break;
        }
    }
    
    /**
     * 给予力量药水
     */
    private void giveStrengthPotion(Player player, int amplifier, int amount, int durationSeconds) {
        try {
            // 创建药水物品
            ItemStack potion = new ItemStack(Material.POTION, amount);
            
            // 获取PotionMeta
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            
            // 创建力量效果
            PotionEffect strengthEffect = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, durationSeconds * 20, amplifier - 1);
            
            // 添加效果到药水
            meta.addCustomEffect(strengthEffect, true);
            
            // 设置药水名称
            meta.setDisplayName("§b力量" + amplifier + "药水");
            
            // 设置药水描述
            List<String> lore = new ArrayList<>();
            lore.add("§7效果: 力量 " + amplifier);
            lore.add("§7持续时间: " + (durationSeconds / 60) + "分" + (durationSeconds % 60) + "秒");
            meta.setLore(lore);
            
            potion.setItemMeta(meta);
            
            // 给予玩家
            if (player.getInventory().firstEmpty() == -1) {
                // 背包满了，掉落在地上
                player.getWorld().dropItemNaturally(player.getLocation(), potion);
                player.sendMessage("§e你的背包已满，药水已掉落在地上！");
            } else {
                player.getInventory().addItem(potion);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning("创建药水失败: " + e.getMessage());
            // 备用方案：使用简单的give命令
            String playerName = player.getName();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                "give " + playerName + " potion " + amount + " 8201");
        }
    }
    
    /**
     * 获取永久购买价格
     */
    private int getPermanentPrice(String kitType, int level) {
        List<Integer> prices = configManager.getKitPermanentPrices(kitType);
        if (level <= 0 || level > prices.size()) {
            return 0;
        }
        
        int basePrice = prices.get(level - 1);
        double discount = configManager.getKitLevelDiscount(kitType, level);
        
        return (int) Math.ceil(basePrice * discount);
    }
    
    /**
     * 检查并扣除点券
     */
    private CompletableFuture<Boolean> checkAndDeductPoints(Player player, int amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // 在异步线程中检查余额
        CompletableFuture.supplyAsync(() -> {
            // 使用PlaceholderAPI获取点券余额
            String pointsStr = PlaceholderAPI.setPlaceholders(player, "%remiaftvalue_Points%");
            
            try {
                int currentPoints = Integer.parseInt(pointsStr);
                return currentPoints >= amount ? currentPoints : -1;
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("无法解析玩家点券数量: " + pointsStr);
                return -1;
            }
        }).thenAccept(currentPoints -> {
                if (currentPoints >= amount) {
                // 在主线程中执行命令
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        "value take " + player.getName() + " Points " + amount);
                    future.complete(true);
                });
            } else {
                future.complete(false);
            }
        });
            
        return future;
    }
    
    /**
     * 检查并扣除硬币
     */
    private CompletableFuture<Boolean> checkAndDeductCoins(Player player, int amount) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // 在异步线程中检查余额
        CompletableFuture.supplyAsync(() -> {
            // 使用PlaceholderAPI获取硬币余额
            String coinsStr = PlaceholderAPI.setPlaceholders(player, "%vault_eco_balance_fixed%");
            
            try {
                double currentCoins = Double.parseDouble(coinsStr);
                return currentCoins >= amount ? currentCoins : -1.0;
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("无法解析玩家硬币数量: " + coinsStr);
                return -1.0;
            }
        }).thenAccept(currentCoins -> {
                if (currentCoins >= amount) {
                // 在主线程中执行命令
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        "eco take " + player.getName() + " " + amount);
                    future.complete(true);
                });
            } else {
                future.complete(false);
            }
        });
            
        return future;
    }
    
    /**
     * 创建并保存礼包数据
     */
    private CompletableFuture<PurchaseResult> createAndSaveKitData(Player player, String kitType, int level, 
                                                                  boolean isPermanent, int price, String currency) {
        // 创建礼包数据
        PlayerKitData kitData = new PlayerKitData(
            player.getUniqueId(),
            kitType,
            level,
            isPermanent,
            LocalDateTime.now(),
            isPermanent ? null : LocalDateTime.now().plusDays(30),
            null
        );
        
        // 保存到数据库
        return plugin.getDatabaseManager().insertPlayerKitDataAsync(kitData)
                .thenCompose(saved -> {
                    if (saved) {
                        // 记录购买历史
                        plugin.getDatabaseManager().savePurchaseHistoryAsync(
                            player.getUniqueId(), kitType, level,
                            isPermanent, price, currency, null
                        );
                        
                        // 清除缓存
                        playerKitCache.remove(player.getUniqueId().toString());
                        return CompletableFuture.completedFuture(new PurchaseResult(true, "success", "购买成功"));
                    } else {
                        return CompletableFuture.completedFuture(
                            new PurchaseResult(false, "database_error", "数据库错误")
                        );
                    }
                });
    }
    
    /**
     * 删除玩家的礼包等级
     */
    public CompletableFuture<PurchaseResult> removeKitLevel(Player player, String kitType, int level) {
        return getPlayerKitDataAsync(player).thenCompose((List<PlayerKitData> dataList) -> {
            // 查找要删除的礼包数据
            PlayerKitData kitDataToRemove = dataList.stream()
                    .filter(data -> data.getKitType().equals(kitType) 
                                 && data.getKitLevel() == level 
                                 && !data.isExpired())
                    .findFirst()
                    .orElse(null);
            
            if (kitDataToRemove == null) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "not_owned", "玩家没有拥有此礼包等级")
                );
            }
            
            // 从数据库删除
            return plugin.getDatabaseManager().removePlayerKitDataAsync(player.getUniqueId(), kitType, level)
                    .thenApply(removed -> {
                        if (removed) {
                            // 清除缓存
                            playerKitCache.remove(player.getUniqueId().toString());
                            
                            // 记录删除历史
                            plugin.getDatabaseManager().savePurchaseHistoryAsync(
                                player.getUniqueId(), kitType, level,
                                kitDataToRemove.isPermanent(), 0, "REMOVE", 
                                "管理员删除操作"
                            );
                            
                            return new PurchaseResult(true, "success", "删除成功");
                        } else {
                            return new PurchaseResult(false, "database_error", "数据库删除失败");
                        }
                    });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("删除礼包等级时发生错误: " + throwable.getMessage());
            throwable.printStackTrace();
            return new PurchaseResult(false, "error", "删除过程中发生错误");
        });
    }
    
    /**
     * 通过UUID删除玩家的礼包等级（支持离线玩家）
     */
    public CompletableFuture<PurchaseResult> removeKitLevelByUUID(java.util.UUID playerUUID, String kitType, int level) {
        return plugin.getDatabaseManager().getPlayerKitDataAsync(playerUUID).thenCompose((List<PlayerKitData> dataList) -> {
            // 查找要删除的礼包数据
            PlayerKitData kitDataToRemove = dataList.stream()
                    .filter(data -> data.getKitType().equals(kitType) 
                                 && data.getKitLevel() == level 
                                 && !data.isExpired())
                    .findFirst()
                    .orElse(null);
            
            if (kitDataToRemove == null) {
                return CompletableFuture.completedFuture(
                    new PurchaseResult(false, "not_owned", "玩家没有拥有此礼包等级")
                );
            }
            
            // 从数据库删除
            return plugin.getDatabaseManager().removePlayerKitDataAsync(playerUUID, kitType, level)
                    .thenApply(removed -> {
                        if (removed) {
                            // 清除缓存
                            playerKitCache.remove(playerUUID.toString());
                            
                            // 记录删除历史
                            plugin.getDatabaseManager().savePurchaseHistoryAsync(
                                playerUUID, kitType, level,
                                kitDataToRemove.isPermanent(), 0, "REMOVE", 
                                "管理员删除操作"
                            );
                            
                            return new PurchaseResult(true, "success", "删除成功");
                        } else {
                            return new PurchaseResult(false, "database_error", "数据库删除失败");
                        }
                    });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("删除礼包等级时发生错误: " + throwable.getMessage());
            throwable.printStackTrace();
            return new PurchaseResult(false, "error", "删除过程中发生错误");
        });
    }
    
    /**
     * 管理员给予礼包功能
     */
    public CompletableFuture<Boolean> giveKitToPlayer(Player player, String kitType, int level, boolean isPermanent) {
        // 创建礼包数据，价格为0（管理员给予）
        PlayerKitData kitData = new PlayerKitData(
            player.getUniqueId(),
            kitType,
            level,
            isPermanent,
            LocalDateTime.now(),
            isPermanent ? null : LocalDateTime.now().plusDays(30),
            null
        );
        
        // 保存到数据库
        return plugin.getDatabaseManager().insertPlayerKitDataAsync(kitData)
                .thenCompose(saved -> {
                    if (saved) {
                        // 记录给予历史（价格为0，货币为ADMIN）
                        plugin.getDatabaseManager().savePurchaseHistoryAsync(
                            player.getUniqueId(), kitType, level,
                            isPermanent, 0, "ADMIN", null
                        );
                        
                        // 清除缓存
                        playerKitCache.remove(player.getUniqueId().toString());
                        return CompletableFuture.completedFuture(true);
                    } else {
                        return CompletableFuture.completedFuture(false);
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("管理员给予礼包时数据库操作失败: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return false;
                });
    }
    
    /**
     * 购买结果类
     */
    public static class PurchaseResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final int amount;
        
        public PurchaseResult(boolean success, String errorCode, String message) {
            this(success, errorCode, message, 0);
        }
        
        public PurchaseResult(boolean success, String errorCode, String message, int amount) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.amount = amount;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public int getAmount() { return amount; }
    }
    
    /**
     * 领取结果类
     */
    public static class ClaimResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        
        public ClaimResult(boolean success, String errorCode, String message) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
        }
        
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
    }
} 