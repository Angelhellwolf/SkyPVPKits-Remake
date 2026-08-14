package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.bridge.SkyPVPItemBridge;
import com.remiaft.skypvpkits.data.PlayerKitData;
import me.clip.placeholderapi.PlaceholderAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
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
    private Economy economy;
    
    // 礼包类型常量
    public static final List<String> KIT_TYPES = Arrays.asList(
        "sky", "mars", "earth", "moon", "sun", "mining", "galaxy", "zxgy", "ywxc"
    );
    
    // 玩家礼包缓存
    private final Map<String, CachedKitData> playerKitCache = new ConcurrentHashMap<>();
    private final Map<String, List<ItemStack>> rewardTemplateCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRE_MS = 60 * 1000; // 缓存60秒过期

    private static class CachedKitData {
        final List<PlayerKitData> data;
        final long timestamp;

        CachedKitData(List<PlayerKitData> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRE_MS;
        }
    }
    
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
        resolveEconomy();
        
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
        playerKitCache.clear();
        rewardTemplateCache.clear();
        resolveEconomy();
        plugin.getLogger().info("KitManager reloaded");
    }
    
    /**
     * 异步获取玩家礼包数据
     */
    public CompletableFuture<List<PlayerKitData>> getPlayerKitDataAsync(Player player) {
        String playerKey = player.getUniqueId().toString();
        
        // 检查缓存
        CachedKitData cached = playerKitCache.get(playerKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                return CompletableFuture.completedFuture(cached.data);
            }
            playerKitCache.remove(playerKey);
        }

        return plugin.getDatabaseManager().getPlayerKitDataAsync(player.getUniqueId())
                .thenApplyAsync(dataList -> {
                    playerKitCache.put(playerKey, new CachedKitData(dataList));
                    return dataList;
                }, executorService)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("获取玩家礼包数据失败: " + throwable.getMessage());
                    return new ArrayList<>();
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

    private int getMaxOwnedLevel(List<PlayerKitData> dataList, String kitType) {
        return dataList.stream()
                .filter(data -> data.getKitType().equalsIgnoreCase(kitType) && !data.isExpired())
                .mapToInt(PlayerKitData::getKitLevel)
                .max()
                .orElse(0);
    }

    private int getMaxPermanentOwnedLevel(List<PlayerKitData> dataList, String kitType) {
        return dataList.stream()
                .filter(data -> data.getKitType().equalsIgnoreCase(kitType)
                        && data.isPermanent()
                        && !data.isExpired())
                .mapToInt(PlayerKitData::getKitLevel)
                .max()
                .orElse(0);
    }

    private boolean hasActiveLevel(List<PlayerKitData> dataList, String kitType, int level) {
        return dataList.stream()
                .anyMatch(data -> data.getKitType().equalsIgnoreCase(kitType)
                        && data.getKitLevel() == level
                        && !data.isExpired());
    }

    private boolean hasPermanentLevel(List<PlayerKitData> dataList, String kitType, int level) {
        return dataList.stream()
                .anyMatch(data -> data.getKitType().equalsIgnoreCase(kitType)
                        && data.getKitLevel() == level
                        && data.isPermanent()
                        && !data.isExpired());
    }

    private PurchaseResult resolvePermanentPurchaseBlockResult(List<PlayerKitData> dataList, String kitType, int level) {
        int maxPermanentLevel = getMaxPermanentOwnedLevel(dataList, kitType);
        if (maxPermanentLevel > level) {
            return new PurchaseResult(false, "higher_level_owned",
                    "你已永久拥有更高等级礼包 Lv." + maxPermanentLevel, maxPermanentLevel);
        }
        if (hasPermanentLevel(dataList, kitType, level)) {
            return new PurchaseResult(false, "already_owned", "你已经永久拥有这个礼包了", level);
        }
        if (level > 1 && maxPermanentLevel < level - 1) {
            return new PurchaseResult(false, "need_previous_permanent_level",
                    "需要先永久拥有等级 " + (level - 1), level - 1);
        }
        return null;
    }

    private PurchaseResult resolveMonthlyPurchaseBlockResult(List<PlayerKitData> dataList, String kitType, int level) {
        int maxActiveLevel = getMaxOwnedLevel(dataList, kitType);
        if (maxActiveLevel >= level) {
            if (maxActiveLevel > level) {
                return new PurchaseResult(false, "higher_level_owned",
                        "你已拥有更高等级礼包 Lv." + maxActiveLevel, maxActiveLevel);
            }
            return new PurchaseResult(false, "already_owned", "你已经拥有这个礼包了", level);
        }
        return resolvePermanentPurchaseBlockResult(dataList, kitType, level);
    }
    
    /**
     * 获取玩家在指定礼包类型中的最高等级
     */
    public CompletableFuture<Integer> getPlayerMaxLevel(Player player, String kitType) {
        return getPlayerKitDataAsync(player).thenApply(dataList -> {
            return getMaxOwnedLevel(dataList, kitType);
        });
    }
    
    /**
     * 检查玩家是否可以购买指定礼包等级
     */
    public CompletableFuture<Boolean> canPurchaseLevel(Player player, String kitType, int level) {
        return getPlayerKitDataAsync(player).thenApply(dataList ->
                resolvePermanentPurchaseBlockResult(dataList, kitType, level) == null);
    }
    
    /**
     * 检查是否支持月付购买
     */
    public boolean supportsMonthlyPurchase(String kitType, int level) {
        if (!configManager.isKitSupportMonthly(kitType)) {
            return false;
        }

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
     * 购买礼包（永久）- 左键点击，点券购买
     */
    public CompletableFuture<PurchaseResult> purchaseKitPermanent(Player player, String kitType, int level) {
        return getPlayerKitDataAsync(player).thenCompose(dataList -> {
            PurchaseResult blocked = resolvePermanentPurchaseBlockResult(dataList, kitType, level);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
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
        return getPlayerKitDataAsync(player).thenCompose(dataList -> {
            PurchaseResult blocked = resolveMonthlyPurchaseBlockResult(dataList, kitType, level);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
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

        if (requiresSkyPVPItem(kitType) && !SkyPVPItemBridge.isAvailable()) {
            return CompletableFuture.completedFuture(
                new ClaimResult(false, "dependency_missing", "SkyPVPItem 插件未安装或未启用")
            );
        }

        if (!canProcessOperation(playerKey)) {
            return CompletableFuture.completedFuture(new ClaimResult(false, "busy", "服务器繁忙，请稍后再试"));
        }

        startOperation(playerKey);

        return getPlayerKitDataAsync(player)
                .thenCompose(dataList -> {
                    boolean hasKit = dataList.stream()
                            .anyMatch(data -> data.getKitType().equals(kitType)
                                    && data.getKitLevel() == level
                                    && !data.isExpired());
                    if (!hasKit) {
                        return CompletableFuture.completedFuture(new ClaimResult(false, "not_owned", "你还没有购买这个礼包"));
                    }

                    // 检查是否拥有更高等级的礼包（如果有，禁止领取低等级）
                    int maxLevel = dataList.stream()
                            .filter(data -> data.getKitType().equals(kitType) && !data.isExpired())
                            .mapToInt(PlayerKitData::getKitLevel)
                            .max()
                            .orElse(0);

                    if (maxLevel > level) {
                        return CompletableFuture.completedFuture(new ClaimResult(
                                false,
                                "higher_level_owned",
                                "你已拥有" + kitType + "礼包等级" + maxLevel + "，无法领取等级" + level + "的奖励"
                        ));
                    }

                    PlayerKitData kitData = dataList.stream()
                            .filter(data -> data.getKitType().equals(kitType)
                                    && data.getKitLevel() == level
                                    && !data.isExpired())
                            .findFirst()
                            .orElse(null);

                    if (kitData == null) {
                        return CompletableFuture.completedFuture(new ClaimResult(false, "not_found", "礼包数据未找到"));
                    }

                    if (kitData.hasClaimedToday()) {
                        return CompletableFuture.completedFuture(new ClaimResult(false, "already_claimed", "今天已经领取过了"));
                    }

                    kitData.markClaimedToday();

                    return plugin.getDatabaseManager().savePlayerKitDataAsync(kitData)
                            .thenApply(saved -> {
                                if (!saved) {
                                    return new ClaimResult(false, "database_error", "数据库错误");
                                }

                                plugin.getServer().getScheduler().runTask(plugin, () -> executeKitRewards(player, kitType, level));
                                playerKitCache.remove(playerKey);
                                return new ClaimResult(true, "success", "领取成功");
                            });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("领取礼包奖励失败: " + throwable.getMessage());
                    return new ClaimResult(false, "error", "处理过程中发生错误");
                })
                .whenComplete((result, throwable) -> endOperation(playerKey));
    }
    
    /**
     * 执行礼包奖励
     */
    private void executeKitRewards(Player player, String kitType, int level) {
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
            case "mars":
            case "earth":
            case "moon":
            case "sun":
            case "mining":
            case "galaxy":
                giveConfiguredRewardItems(player, kitType, level);
                break;

            case "zxgy":
                if (!SkyPVPItemBridge.giveSet(player, "zxgy")) {
                    plugin.getLogger().warning("无法通过 SkyPVPItem 发放沾星顾月套装给玩家 " + playerName);
                    player.sendMessage("§c沾星顾月礼包发放失败，请联系管理员检查 SkyPVPItem 插件");
                }
                break;

            case "ywxc":
                if (!SkyPVPItemBridge.giveSet(player, "ywxc")) {
                    plugin.getLogger().warning("无法通过 SkyPVPItem 发放仰望星辰套装给玩家 " + playerName);
                    player.sendMessage("§c仰望星辰礼包发放失败，请联系管理员检查 SkyPVPItem 插件");
                }
                break;
        }
    }

    private void giveConfiguredRewardItems(Player player, String kitType, int level) {
        List<String> configuredRewardKeys = configManager.getKitRewardItems(kitType, level);
        if (configuredRewardKeys.isEmpty()) {
            plugin.getLogger().warning("礼包 " + kitType + " 等级 " + level + " 没有配置EZItem模板");
            player.sendMessage("§c该礼包未配置 EZItem 模板，无法发放");
            return;
        }

        if (plugin.getEZItemIntegration() == null || !plugin.getEZItemIntegration().isEZItemEnabled()) {
            plugin.getLogger().warning("EZItem 未启用，无法发放礼包 " + kitType + " 等级 " + level);
            player.sendMessage("§cEZItem 未启用，无法发放该礼包");
            return;
        }

        int cleanedLegacyItems = cleanupLegacyIssuedItems(player, kitType, level);
        if (cleanedLegacyItems > 0) {
            player.sendMessage("§e已自动清理旧版手动发放装备 " + cleanedLegacyItems + " 件，并替换为 EZItem 版本");
        }

        for (String itemKey : configuredRewardKeys) {
            if (itemKey == null || itemKey.trim().isEmpty()) {
                continue;
            }

            if (plugin.getEZItemIntegration().getItemByTemplate(itemKey) == null) {
                plugin.getLogger().warning("EZItem模板不存在，停止发放: " + itemKey);
                player.sendMessage("§c礼包模板缺失: " + itemKey);
                return;
            }
        }

        for (String itemKey : configuredRewardKeys) {
            if (itemKey == null || itemKey.trim().isEmpty()) {
                continue;
            }

            if (!plugin.getEZItemIntegration().giveItemFromTemplate(player, itemKey)) {
                plugin.getLogger().warning("EZItem模板发放失败，中止礼包发放: " + itemKey);
                player.sendMessage("§c礼包发放失败，请联系管理员检查模板: " + itemKey);
                return;
            }
        }
    }

    public int cleanupLegacyIssuedItems(Player player, String kitType, int level) {
        if (player == null || plugin.getEZItemIntegration() == null || !plugin.getEZItemIntegration().isEZItemEnabled()) {
            return 0;
        }

        List<ItemStack> templateItems = getRewardTemplateItems(kitType, level);
        if (templateItems.isEmpty()) {
            return 0;
        }

        int removed = 0;
        PlayerInventory inventory = player.getInventory();

        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack candidate = contents[i];
            if (shouldRemoveLegacyItem(candidate, templateItems)) {
                contents[i] = null;
                removed++;
            }
        }
        inventory.setContents(contents);

        ItemStack[] armorContents = inventory.getArmorContents();
        for (int i = 0; i < armorContents.length; i++) {
            ItemStack candidate = armorContents[i];
            if (shouldRemoveLegacyItem(candidate, templateItems)) {
                armorContents[i] = null;
                removed++;
            }
        }
        inventory.setArmorContents(armorContents);

        return removed;
    }

    private List<ItemStack> getRewardTemplateItems(String kitType, int level) {
        String cacheKey = kitType + ":" + level;
        List<ItemStack> cachedItems = rewardTemplateCache.get(cacheKey);
        if (cachedItems != null) {
            return cloneItems(cachedItems);
        }

        List<ItemStack> templateItems = new ArrayList<>();
        List<String> configuredRewardKeys = configManager.getKitRewardItems(kitType, level);
        for (String itemKey : configuredRewardKeys) {
            if (itemKey == null || itemKey.trim().isEmpty()) {
                continue;
            }

            ItemStack templateItem = plugin.getEZItemIntegration().getItemByTemplate(itemKey);
            if (templateItem != null) {
                templateItems.add(templateItem);
            }
        }
        rewardTemplateCache.put(cacheKey, cloneItems(templateItems));
        return templateItems;
    }

    private boolean shouldRemoveLegacyItem(ItemStack candidate, List<ItemStack> templateItems) {
        if (candidate == null || candidate.getType() == Material.AIR) {
            return false;
        }

        for (ItemStack templateItem : templateItems) {
            if (matchesLegacyIssuedItem(candidate, templateItem)) {
                return !plugin.getEZItemIntegration().isEZItem(candidate);
            }
        }
        return false;
    }

    private List<ItemStack> cloneItems(List<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>(items.size());
        for (ItemStack item : items) {
            clones.add(item == null ? null : item.clone());
        }
        return clones;
    }

    private boolean matchesLegacyIssuedItem(ItemStack candidate, ItemStack templateItem) {
        if (candidate == null || templateItem == null) {
            return false;
        }
        if (candidate.getType() != templateItem.getType()) {
            return false;
        }

        ItemMeta candidateMeta = candidate.getItemMeta();
        ItemMeta templateMeta = templateItem.getItemMeta();
        if (!sameDisplayName(candidateMeta, templateMeta)) {
            return false;
        }
        if (!sameLore(candidateMeta, templateMeta)) {
            return false;
        }
        if (!candidate.getEnchantments().equals(templateItem.getEnchantments())) {
            return false;
        }
        return sameLeatherColor(candidateMeta, templateMeta);
    }

    private boolean sameDisplayName(ItemMeta first, ItemMeta second) {
        String firstName = first != null && first.hasDisplayName() ? first.getDisplayName() : null;
        String secondName = second != null && second.hasDisplayName() ? second.getDisplayName() : null;
        return java.util.Objects.equals(firstName, secondName);
    }

    private boolean sameLore(ItemMeta first, ItemMeta second) {
        List<String> firstLore = first != null && first.hasLore() ? first.getLore() : null;
        List<String> secondLore = second != null && second.hasLore() ? second.getLore() : null;
        return java.util.Objects.equals(firstLore, secondLore);
    }

    private boolean sameLeatherColor(ItemMeta first, ItemMeta second) {
        if (first instanceof LeatherArmorMeta && second instanceof LeatherArmorMeta) {
            return ((LeatherArmorMeta) first).getColor().equals(((LeatherArmorMeta) second).getColor());
        }
        return !(first instanceof LeatherArmorMeta) && !(second instanceof LeatherArmorMeta);
    }

    private boolean requiresSkyPVPItem(String kitType) {
        return "zxgy".equals(kitType) || "ywxc".equals(kitType);
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

                // 太阳4级特殊奖励
                if (level == 4) {
                    giveSunLevel4SpecialRewards(player);
                }

                // 太阳5级特殊奖励
                if (level == 5) {
                    giveSunLevel5SpecialRewards(player);
                }
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
                break;
        }
        
        // 给予药水奖励
        givePotionRewards(player, kitType, level);
    }
    
    /**
     * 给予太阳4级特殊奖励
     * 通过控制台调度 sunkits 插件命令
     */
    private void giveSunLevel4SpecialRewards(Player player) {
        String playerName = player.getName();

        // 小丑果实 3个
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sunkits give " + playerName + " clownfruit 3");
    }

    /**
     * 给予太阳5级特殊奖励
     * 通过控制台调度 sunkits 插件命令
     */
    private void giveSunLevel5SpecialRewards(Player player) {
        String playerName = player.getName();

        // 善良果实 4个
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sunkits give " + playerName + " goodfruit 4");

        // 小丑果实 10个
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sunkits give " + playerName + " clownfruit 10");

        // 除你武器 2个
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sunkits give " + playerName + " weapon 2");
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

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                String pointsStr = PlaceholderAPI.setPlaceholders(player, "%remiaftvalue_Points%");
                int currentPoints = Integer.parseInt(pointsStr);
                if (currentPoints >= amount) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "value take " + player.getName() + " Points " + amount);
                    future.complete(true);
                } else {
                    future.complete(false);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("无法解析玩家点券数量");
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

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                String coinsStr = PlaceholderAPI.setPlaceholders(player, "%vault_eco_balance_fixed%");
                double currentCoins = Double.parseDouble(coinsStr);
                if (currentCoins >= amount) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "eco take " + player.getName() + " " + amount);
                    future.complete(true);
                } else {
                    future.complete(false);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("无法解析玩家硬币数量");
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
            return new PurchaseResult(false, "error", "删除过程中发生错误");
        });
    }

    public CompletableFuture<BulkRemoveResult> removeAllKitLevels(String kitType, int level) {
        return plugin.getDatabaseManager().getPlayersByKitAsync(kitType, level).thenCompose(playerUUIDs -> {
            if (playerUUIDs.isEmpty()) {
                return CompletableFuture.completedFuture(
                    new BulkRemoveResult(false, "not_owned", "没有玩家拥有此礼包等级", 0, 0, playerUUIDs)
                );
            }

            return plugin.getDatabaseManager().removeAllPlayerKitDataAsync(kitType, level)
                    .thenApply(deletedRecords -> {
                        if (deletedRecords <= 0) {
                            return new BulkRemoveResult(false, "database_error", "数据库删除失败", 0, 0, playerUUIDs);
                        }

                        for (java.util.UUID playerUUID : playerUUIDs) {
                            playerKitCache.remove(playerUUID.toString());
                            plugin.getDatabaseManager().savePurchaseHistoryAsync(
                                playerUUID, kitType, level,
                                true, 0, "REMOVE",
                                "管理员批量回收操作"
                            );
                        }

                        return new BulkRemoveResult(
                            true,
                            "success",
                            "批量回收成功",
                            playerUUIDs.size(),
                            deletedRecords,
                            playerUUIDs
                        );
                    });
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("批量回收礼包等级时发生错误: " + throwable.getMessage());
            return new BulkRemoveResult(false, "error", "批量回收过程中发生错误", 0, 0, java.util.Collections.emptyList());
        });
    }
    
    /**
     * 管理员给予礼包功能
     */
    public CompletableFuture<TemporaryGrantResult> grantTemporaryKitOrCompensate(Player player, String kitType, int level, int durationDays, double compensationCoins) {
        if (player == null || kitType == null || kitType.trim().isEmpty() || level <= 0) {
            return CompletableFuture.completedFuture(new TemporaryGrantResult(false, false, false, "参数无效"));
        }

        final int safeDurationDays = Math.max(1, durationDays);
        return getPlayerKitDataAsync(player).thenCompose(dataList -> {
            boolean alreadyOwned = dataList.stream()
                    .anyMatch(data -> data.getKitType().equalsIgnoreCase(kitType)
                            && data.getKitLevel() >= level
                            && !data.isExpired());

            if (alreadyOwned) {
                return depositCompensationAsync(player, compensationCoins).thenApply(compensated -> {
                    if (!compensated) {
                        return new TemporaryGrantResult(false, false, false, "补偿失败");
                    }
                    return new TemporaryGrantResult(true, false, true, "已补偿硬币");
                });
            }

            PlayerKitData kitData = new PlayerKitData(
                    player.getUniqueId(),
                    kitType,
                    level,
                    false,
                    LocalDateTime.now(),
                    LocalDateTime.now().plusDays(safeDurationDays),
                    null
            );

            return plugin.getDatabaseManager().insertPlayerKitDataAsync(kitData)
                    .thenApply(saved -> {
                        if (!saved) {
                            return new TemporaryGrantResult(false, false, false, "数据库错误");
                        }
                        plugin.getDatabaseManager().savePurchaseHistoryAsync(
                                player.getUniqueId(), kitType, level, false, 0, "BATTLEPASS_TEMP",
                                "BattlePass temporary grant " + safeDurationDays + "d"
                        );
                        playerKitCache.remove(player.getUniqueId().toString());
                        notifyBattlePassKitClaim(player, kitType, level);
                        return new TemporaryGrantResult(true, true, false, "发放成功");
                    });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("临时礼包发放失败: " + throwable.getMessage());
            return new TemporaryGrantResult(false, false, false, "处理失败");
        });
    }

    private CompletableFuture<Boolean> depositCompensationAsync(Player player, double compensationCoins) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (compensationCoins <= 0.0D) {
                future.complete(true);
                return;
            }
            if (economy == null) {
                future.complete(false);
                return;
            }
            economy.depositPlayer(player, compensationCoins);
            future.complete(true);
        });
        return future;
    }

    private void resolveEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            this.economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = provider == null ? null : provider.getProvider();
    }

    private void notifyBattlePassKitClaim(Player player, String kitType, int level) {
        try {
            Class<?> bridge = Class.forName("com.remiaft.skypvpbattlepass.bridge.SkyPVPBattlePassBridge");
            java.lang.reflect.Method method = bridge.getMethod("notifyKitClaim", java.util.UUID.class, String.class, Integer.TYPE);
            method.invoke(null, player.getUniqueId(), kitType, Integer.valueOf(level));
        } catch (Exception ignored) {
        }
    }

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
                    return false;
                });
    }
    
    /**
     * 购买结果类
     */
    public static class TemporaryGrantResult {
        private final boolean success;
        private final boolean granted;
        private final boolean compensated;
        private final String message;

        public TemporaryGrantResult(boolean success, boolean granted, boolean compensated, String message) {
            this.success = success;
            this.granted = granted;
            this.compensated = compensated;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public boolean isGranted() { return granted; }
        public boolean isCompensated() { return compensated; }
        public String getMessage() { return message; }
    }

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

    public static class BulkRemoveResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final int affectedPlayers;
        private final int deletedRecords;
        private final List<java.util.UUID> playerUUIDs;

        public BulkRemoveResult(boolean success, String errorCode, String message,
                                int affectedPlayers, int deletedRecords, List<java.util.UUID> playerUUIDs) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.affectedPlayers = affectedPlayers;
            this.deletedRecords = deletedRecords;
            this.playerUUIDs = playerUUIDs;
        }

        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public int getAffectedPlayers() { return affectedPlayers; }
        public int getDeletedRecords() { return deletedRecords; }
        public List<java.util.UUID> getPlayerUUIDs() { return playerUUIDs; }
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
