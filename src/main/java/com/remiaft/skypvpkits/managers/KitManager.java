package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.bridge.SkyPVPItemBridge;
import com.remiaft.skypvpkits.bridge.AngelRewardsBridge;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
    private final java.util.Set<UUID> activeShopClaims = ConcurrentHashMap.newKeySet();
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
            return hasFreeAccessLevel(kitType, level, dataList) || dataList.stream()
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
            return hasFreeAccessLevel(kitType, level, dataList) || dataList.stream()
                    .anyMatch(data -> data.getKitType().equals(kitType)
                                   && data.getKitLevel() == level
                                   && !data.isExpired());
        });
    }

    public static final class NationalDayReward {
        public enum Type { KIT, GEMS }
        public final Type type;
        public final String kitType;
        public final int level;
        public final int gemAmount;
        public final String reason;

        public NationalDayReward(Type type, String kitType, int level, int gemAmount, String reason) {
            this.type = type;
            this.kitType = kitType;
            this.level = level;
            this.gemAmount = gemAmount;
            this.reason = reason;
        }
    }

    public NationalDayReward resolveNationalDayReward(String baseKit, List<PlayerKitData> playerData) {
        if (!configManager.isNationalDayActive() || baseKit == null || !configManager.isKitEnabled(baseKit)) return null;
        return nationalDayReward(baseKit, getMaxOwnedLevel(playerData, baseKit),
                configManager.getKitLevels(baseKit), getMaxOwnedLevel(playerData, "galaxy"));
    }

    /** Only real, unexpired ownership is supplied here; activity access never advances itself. */
    public static NationalDayReward nationalDayReward(String baseKit, int ownedLevel, int maxLevel, int galaxyOwned) {
        if (baseKit == null) return null;
        String family = baseKit.toLowerCase(Locale.ROOT);
        if (!Arrays.asList("moon", "earth", "mining").contains(family)) return null;
        int baseLevel = "earth".equals(family) ? 4 : 3;
        if (maxLevel < baseLevel) return null;
        if (ownedLevel < baseLevel)
            return new NationalDayReward(NationalDayReward.Type.KIT, family, baseLevel, 0, "国庆限时免费");
        if (ownedLevel < maxLevel)
            return new NationalDayReward(NationalDayReward.Type.KIT, family, baseLevel + 1, 0, "已拥有基础等级，免费升级");
        if (galaxyOwned < 3)
            return new NationalDayReward(NationalDayReward.Type.KIT, "galaxy", 3, 0, "已拥有最高等级，免费领取银河 3");
        return new NationalDayReward(NationalDayReward.Type.GEMS, family, 0, 5, "已拥有银河 3，赠送完美淬炼宝石 ×5");
    }

    public boolean hasFreeAccessLevel(String kitType, int level, List<PlayerKitData> playerData) {
        if (configManager.isAllKitsFreeLevel(kitType, level)) {
            return true;
        }
        if (configManager.isNationalDayActive() && playerData != null) {
            for (String base : Arrays.asList("moon", "earth", "mining")) {
                NationalDayReward reward = resolveNationalDayReward(base, playerData);
                if (reward != null && reward.type == NationalDayReward.Type.KIT
                        && reward.kitType.equalsIgnoreCase(kitType) && reward.level == level) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasFreeAccessLevel(String kitType, int level) {
        return configManager.isAllKitsFreeLevel(kitType, level);
    }

    public CompletableFuture<ClaimResult> claimNationalDayReward(Player player, String baseFamily) {
        return plugin.getDatabaseManager().getPlayerKitDataAsync(player.getUniqueId()).thenCompose(data -> {
            NationalDayReward reward = resolveNationalDayReward(baseFamily, data);
            if (reward == null) return CompletableFuture.completedFuture(
                    new ClaimResult(false, "promotion_ended", "国庆活动尚未开始或已结束"));
            if (reward.type == NationalDayReward.Type.GEMS) return claimNationalDayGems(player, baseFamily);
            playerKitCache.remove(player.getUniqueId().toString());
            return claimKitReward(player, reward.kitType, reward.level);
        });
    }

    private final java.util.Set<UUID> nationalDayGemsRecorded = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> nationalDayGemsChecking = ConcurrentHashMap.newKeySet();

    /** Automatically records one shared reward per player, including players online at activity start. */
    public void checkNationalDayGems(Player player) {
        if (!configManager.isNationalDayActive() || nationalDayGemsRecorded.contains(player.getUniqueId())
                || !nationalDayGemsChecking.add(player.getUniqueId())) return;
        plugin.getDatabaseManager().getPlayerKitDataAsync(player.getUniqueId()).thenCompose(data -> {
            for (String family : Arrays.asList("moon", "earth", "mining")) {
                NationalDayReward reward = resolveNationalDayReward(family, data);
                if (reward != null && reward.type == NationalDayReward.Type.GEMS)
                    return claimNationalDayGems(player, family);
            }
            return CompletableFuture.completedFuture(new ClaimResult(false, "not_eligible", ""));
        }).whenComplete((result, error) -> {
            if (error == null && result.isSuccess()) nationalDayGemsRecorded.add(player.getUniqueId());
            nationalDayGemsChecking.remove(player.getUniqueId());
        });
    }

    public CompletableFuture<ClaimResult> claimNationalDayGems(Player player, String baseFamily) {
        // Eligibility is re-read from paid entitlements; the menu icon is never authority.
        return plugin.getDatabaseManager().getPlayerKitDataAsync(player.getUniqueId()).thenCompose(data -> {
            NationalDayReward reward = resolveNationalDayReward(baseFamily, data);
            if (reward == null || reward.type != NationalDayReward.Type.GEMS)
                return CompletableFuture.completedFuture(new ClaimResult(false, "not_eligible", "未满足国庆宝石奖励条件"));
            return onServerThread(() -> {
                if (!configManager.isNationalDayActive()) throw new IllegalStateException("Activity ended");
                org.bukkit.plugin.Plugin forge = Bukkit.getPluginManager().getPlugin("RemiaftSkyForge");
                if (forge == null || !forge.isEnabled()) throw new IllegalStateException("SkyForge unavailable");
                @SuppressWarnings("unchecked")
                CompletableFuture<String> grant = (CompletableFuture<String>) forge.getClass()
                        .getMethod("grantNationalDayGems", Player.class).invoke(forge, player);
                return grant;
            }).thenCompose(grant -> grant).thenApply(state -> {
                boolean success = !"BUSY".equals(state);
                if (success) nationalDayGemsRecorded.add(player.getUniqueId());
                return new ClaimResult(success, success ? "success" : "busy", "DONE".equals(state)
                        ? "本次国庆的 5 个完美宝石已领取，每人仅一次"
                        : success ? "5 个完美宝石已登记，背包满或离线时可在点券商城的待领取奖励中补领"
                        : "奖励正在处理，请稍后再试");
            });
        }).exceptionally(error -> new ClaimResult(false, "delivery_failed", "宝石奖励暂不可用，请稍后重试"));
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
            int ownedLevel = getMaxOwnedLevel(dataList, kitType);
            if (configManager.isAllKitsFreeKitEligible(kitType)) {
                return Math.max(ownedLevel, configManager.getKitLevels(kitType));
            }
            return ownedLevel;
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
        if (!KIT_TYPES.contains(kitType) || !configManager.isKitEnabled(kitType))
            return CompletableFuture.completedFuture(new ClaimResult(false, "kit_not_found", "\u793c\u5305\u672a\u542f\u7528"));
        String playerKey = player.getUniqueId().toString();
        final AngelRewardsBridge angel = plugin.getAngelRewards();
        final boolean useAngel = angel != null && !requiresSkyPVPItem(kitType);
        if (useAngel && !activeShopClaims.add(player.getUniqueId()))
            return CompletableFuture.completedFuture(new ClaimResult(false, "busy", "\u8bf7\u7b49\u5f85\u672c\u6b21\u9886\u53d6\u5b8c\u6210"));

        if (requiresSkyPVPItem(kitType) && !SkyPVPItemBridge.isAvailable()) {
            return CompletableFuture.completedFuture(
                new ClaimResult(false, "dependency_missing", "SkyPVPItem 插件未安装或未启用")
            );
        }

        if (!canProcessOperation(playerKey)) {
            if (useAngel) activeShopClaims.remove(player.getUniqueId());
            return CompletableFuture.completedFuture(new ClaimResult(false, "busy", "服务器繁忙，请稍后再试"));
        }

        startOperation(playerKey);

        return getPlayerKitDataAsync(player)
                .thenCompose(dataList -> {
                    PlayerKitData ownedKitData = dataList.stream()
                            .filter(data -> data.getKitType().equals(kitType)
                                    && data.getKitLevel() == level
                                    && !data.isExpired())
                            .findFirst()
                            .orElse(null);
                    boolean hasPromotionAccess = hasFreeAccessLevel(kitType, level, dataList);
                    if (ownedKitData == null && !hasPromotionAccess) {
                        return CompletableFuture.completedFuture(new ClaimResult(false, "not_owned", "你还没有购买这个礼包"));
                    }

                    // 检查是否拥有更高等级的礼包（如果有，禁止领取低等级）
                    int maxLevel = dataList.stream()
                            .filter(data -> data.getKitType().equals(kitType) && !data.isExpired())
                            .mapToInt(PlayerKitData::getKitLevel)
                            .max()
                            .orElse(0);

                    if (configManager.isAllKitsFreeKitEligible(kitType)) {
                        maxLevel = Math.max(maxLevel, configManager.getKitLevels(kitType));
                    }

                    if (maxLevel > level && !hasPromotionAccess) {
                        return CompletableFuture.completedFuture(new ClaimResult(
                                false,
                                "higher_level_owned",
                                "你已拥有" + kitType + "礼包等级" + maxLevel + "，无法领取等级" + level + "的奖励"
                        ));
                    }

                    if (useAngel) return claimAngelRewards(player, kitType, level, ownedKitData == null, angel);

                    if (ownedKitData != null) {
                        if (ownedKitData.hasClaimedToday()) {
                            return CompletableFuture.completedFuture(new ClaimResult(false, "already_claimed", "今天已经领取过了"));
                        }

                        ownedKitData.markClaimedToday();
                        return plugin.getDatabaseManager().savePlayerKitDataAsync(ownedKitData)
                            .thenApply(saved -> {
                                if (!saved) {
                                    return new ClaimResult(false, "database_error", "数据库错误");
                                }

                                plugin.getServer().getScheduler().runTask(plugin, () -> executeKitRewards(player, kitType, level));
                                playerKitCache.remove(playerKey);
                                return new ClaimResult(true, "success", "领取成功");
                            });
                    }

                    if (!SkyPVPItemBridge.supportsActivityExpiry()) {
                        return CompletableFuture.completedFuture(new ClaimResult(
                                false,
                                "dependency_missing",
                                "SkyPVPItem 活动物品时效接口不可用"
                        ));
                    }

                    final long promotionEndsAtMillis = configManager.getAllKitsFreeEndEpochMillis();
                    final String promotionId = configManager.getAllKitsFreePromotionId();
                    final String promotionZoneId = configManager.getAllKitsFreeZoneId();
                    if (promotionEndsAtMillis <= System.currentTimeMillis()) {
                        return CompletableFuture.completedFuture(new ClaimResult(
                                false,
                                "promotion_ended",
                                "全服免费体验活动已结束"
                        ));
                    }

                    LocalDateTime claimTime = LocalDateTime.now();
                    LocalDateTime gameDayStart = getGameDayStart(claimTime);
                    return plugin.getDatabaseManager().claimPromotionRewardAsync(
                                    player.getUniqueId(),
                                    promotionId,
                                    kitType,
                                    level,
                                    gameDayStart,
                                    claimTime)
                            .thenApply(result -> {
                                if (result == DatabaseManager.PromotionClaimResult.ALREADY_CLAIMED) {
                                    return new ClaimResult(false, "already_claimed", "今天已经领取过了");
                                }
                                if (result != DatabaseManager.PromotionClaimResult.CLAIMED) {
                                    return new ClaimResult(false, "database_error", "数据库错误");
                                }
                                if (!configManager.isAllKitsFreeLevel(kitType, level)
                                        || promotionEndsAtMillis <= System.currentTimeMillis()) {
                                    return new ClaimResult(false, "promotion_ended", "全服免费体验活动已结束");
                                }
                                plugin.getServer().getScheduler().runTask(plugin,
                                        () -> executePromotionKitRewards(
                                                player,
                                                kitType,
                                                level,
                                                promotionId,
                                                promotionEndsAtMillis,
                                                promotionZoneId));
                                return new ClaimResult(true, "success", "领取成功");
                            });
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("领取礼包奖励失败: " + throwable.getMessage());
                    return new ClaimResult(false, "error", "处理过程中发生错误");
                })
                .whenComplete((result, throwable) -> {
                    if (useAngel) activeShopClaims.remove(player.getUniqueId());
                    endOperation(playerKey);
                });
    }

    private CompletableFuture<ClaimResult> claimAngelRewards(Player player, String family, int level,
            boolean activity, AngelRewardsBridge provider) {
        boolean isNationalDay = configManager.isNationalDayActive();
        final long deadline = activity ? (isNationalDay ? configManager.getNationalDayEndEpochMillis() : configManager.getAllKitsFreeEndEpochMillis()) : 0;
        final String promotion = activity ? (isNationalDay ? configManager.getNationalDayPromotionId() : configManager.getAllKitsFreePromotionId()) : null;
        final String zone = isNationalDay ? configManager.getNationalDayZoneId() : configManager.getAllKitsFreeZoneId();
        return onServerThread(() -> {
            List<ItemStack> rewards = provider.prepare(family, level, deadline, zone);
            provider.validate(player, rewards);
            return rewards;
        }).thenCompose(rewards -> plugin.getDatabaseManager().reserveShopRewardAsync(
            player.getUniqueId(), family, level, promotion, getGameDayStart(LocalDateTime.now()))
            .thenCompose(reservation -> {
                if (reservation.status == ShopClaimLedger.Status.ALREADY_CLAIMED)
                    return CompletableFuture.completedFuture(new ClaimResult(false, "already_claimed", "\u4eca\u5929\u5df2\u7ecf\u9886\u53d6\u8fc7\u4e86"));
                if (reservation.status != ShopClaimLedger.Status.RESERVED)
                    return CompletableFuture.completedFuture(new ClaimResult(false, "database_error", "\u9886\u53d6\u8bb0\u5f55\u672a\u80fd\u4fdd\u5b58"));
                return onServerThread(() -> {
                    if (activity && (deadline <= System.currentTimeMillis()
                            || (isNationalDay ? !configManager.isNationalDayActive()
                                    : !configManager.isAllKitsFreeLevel(family, level))))
                        throw new IllegalStateException("Activity has ended");
                    provider.deliver(player, rewards);
                    playerKitCache.remove(player.getUniqueId().toString());
                    return new ClaimResult(true, "success", "\u9886\u53d6\u6210\u529f");
                }).handle((result, failure) -> {
                    if (failure == null) return CompletableFuture.completedFuture(result);
                    Throwable cause = unwrap(failure);
                    if (cause.getClass().getSimpleName().equals("DeliveryException")) {
                        try {
                            if (!Boolean.TRUE.equals(cause.getClass().getMethod("isRestored").invoke(cause)))
                                return CompletableFuture.completedFuture(new ClaimResult(false, "delivery_failed", "\u53d1\u653e\u72b6\u6001\u5f02\u5e38\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u6838\u5bf9"));
                        } catch (ReflectiveOperationException uncertain) {
                            return CompletableFuture.completedFuture(new ClaimResult(false, "delivery_failed", "\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u6838\u5bf9\u53d1\u653e\u72b6\u6001"));
                        }
                    }
                    return plugin.getDatabaseManager().restoreShopRewardAsync(reservation).thenApply(restored -> {
                        playerKitCache.remove(player.getUniqueId().toString());
                        return new ClaimResult(false, "delivery_failed", restored
                            ? "\u672a\u53d1\u653e\u7269\u54c1\uff0c\u672c\u6b21\u9886\u53d6\u6b21\u6570\u5df2\u6062\u590d"
                            : "\u9886\u53d6\u72b6\u6001\u6062\u590d\u5931\u8d25\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u6838\u5bf9");
                    });
                }).thenCompose(result -> result);
            })).exceptionally(failure -> {
                Throwable cause = unwrap(failure);
                boolean full = cause.getClass().getSimpleName().equals("NoSpaceException");
                if (!full) plugin.getLogger().warning("AngelItem reward preparation failed for " + family + ":" + level + " (KIT-BUILD-01).");
                return new ClaimResult(false, full ? "no_space" : "delivery_failed", full
                    ? "\u80cc\u5305\u7a7a\u95f4\u4e0d\u8db3\uff0c\u672a\u6d88\u8017\u9886\u53d6\u6b21\u6570"
                    : "\u793c\u5305\u6682\u65f6\u65e0\u6cd5\u751f\u6210\uff0c\u672a\u53d1\u653e\u65e7\u7248\u7269\u54c1");
            });
    }

    private <T> CompletableFuture<T> onServerThread(Callable<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable task = () -> { try { result.complete(action.call()); } catch (Exception failure) { result.completeExceptionally(failure); } };
        try {
            if (Bukkit.isPrimaryThread()) task.run(); else plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        return result;
    }

    private Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }

    private LocalDateTime getGameDayStart(LocalDateTime time) {
        int resetHour = configManager.getDailyResetHour();
        LocalDateTime todayReset = time.toLocalDate().atTime(resetHour, 0);
        return time.isBefore(todayReset) ? todayReset.minusDays(1) : todayReset;
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
     * 活动领取专用发放路径。普通物品只写轻量 lore 标签，不给每件物品创建数据库行；
     * 沾星顾月/仰望星辰仍通过 SkyPVPItem 的索引仓储保存技能所需 grant。
     */
    private void executePromotionKitRewards(
            Player player,
            String kitType,
            int level,
            String promotionId,
            long promotionEndsAtMillis,
            String promotionZoneId) {
        if (!configManager.isAllKitsFreeLevel(kitType, level)
                || promotionEndsAtMillis <= System.currentTimeMillis()) {
            plugin.getLogger().info("跳过已结束活动的礼包发放: " + player.getName()
                    + " " + kitType + " Lv." + level);
            return;
        }

        if (requiresSkyPVPItem(kitType)) {
            if (!SkyPVPItemBridge.giveSetUntil(
                    player,
                    kitType,
                    promotionEndsAtMillis,
                    promotionZoneId)) {
                plugin.getLogger().warning("无法按活动截止时间发放 " + kitType
                        + " 套装给玩家 " + player.getName());
                player.sendMessage("§c活动礼包发放失败，请联系管理员检查 SkyPVPItem");
            }
            return;
        }

        List<ItemStack> activityItems = new ArrayList<>();
        if (!collectPromotionEquipment(activityItems, player, kitType, level)) {
            return;
        }
        collectPromotionSpecialItems(activityItems, kitType, level);

        if (activityItems.isEmpty()) {
            return;
        }
        int givenStacks = SkyPVPItemBridge.giveActivityItems(
                player,
                activityItems,
                promotionId,
                promotionEndsAtMillis,
                promotionZoneId);
        if (givenStacks != activityItems.size()) {
            plugin.getLogger().warning("活动礼包物品发放不完整: " + player.getName()
                    + " " + kitType + " Lv." + level + "，计划 " + activityItems.size()
                    + " 组，实际 " + givenStacks + " 组");
            player.sendMessage("§c活动礼包部分物品发放失败，请联系管理员");
        }
    }

    private boolean collectPromotionEquipment(
            List<ItemStack> destination,
            Player player,
            String kitType,
            int level) {
        List<String> configuredRewardKeys = configManager.getKitRewardItems(kitType, level);
        if (configuredRewardKeys.isEmpty()) {
            plugin.getLogger().warning("礼包 " + kitType + " 等级 " + level + " 没有配置EZItem模板");
            player.sendMessage("§c该礼包未配置 EZItem 模板，无法发放");
            return false;
        }
        if (plugin.getEZItemIntegration() == null || !plugin.getEZItemIntegration().isEZItemEnabled()) {
            plugin.getLogger().warning("EZItem 未启用，无法准备活动礼包 " + kitType + " 等级 " + level);
            player.sendMessage("§cEZItem 未启用，无法发放该礼包");
            return false;
        }

        int cleanedLegacyItems = cleanupLegacyIssuedItems(player, kitType, level);
        if (cleanedLegacyItems > 0) {
            player.sendMessage("§e已自动清理旧版手动发放装备 " + cleanedLegacyItems + " 件");
        }

        List<ItemStack> prepared = new ArrayList<>(configuredRewardKeys.size());
        for (String itemKey : configuredRewardKeys) {
            if (itemKey == null || itemKey.trim().isEmpty()) {
                continue;
            }
            ItemStack template = plugin.getEZItemIntegration().getItemByTemplate(itemKey);
            if (template == null) {
                plugin.getLogger().warning("活动礼包 EZItem 模板不存在，停止发放: " + itemKey);
                player.sendMessage("§c礼包模板缺失: " + itemKey);
                return false;
            }
            prepared.add(template);
        }
        destination.addAll(prepared);
        return true;
    }

    private void collectPromotionSpecialItems(List<ItemStack> destination, String kitType, int level) {
        switch (kitType) {
            case "moon":
                destination.add(createGoldenAppleStack(2 + (2 * level), true));
                destination.add(createStrengthPotionStack(2, 2, 90));
                break;
            case "earth":
                destination.add(createGoldenAppleStack(1, false));
                break;
            case "mars":
                destination.add(createGoldenAppleStack(1, true));
                break;
            case "mining":
                if (level >= 3) {
                    destination.add(createStrengthPotionStack(3, 5, 90));
                }
                break;
            default:
                break;
        }
        destination.removeIf(java.util.Objects::isNull);
    }

    private ItemStack createGoldenAppleStack(int amount, boolean enchanted) {
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, amount);
        if (enchanted) {
            apple.setDurability((short) 1);
        }
        return apple;
    }

    private ItemStack createStrengthPotionStack(
            int amplifier,
            int amount,
            int durationSeconds) {
        try {
            ItemStack potion = new ItemStack(Material.POTION, amount);
            PotionMeta meta = (PotionMeta) potion.getItemMeta();
            PotionEffect strengthEffect = new PotionEffect(
                    PotionEffectType.INCREASE_DAMAGE,
                    durationSeconds * 20,
                    amplifier - 1);
            meta.addCustomEffect(strengthEffect, true);
            meta.setDisplayName("§b力量" + amplifier + "药水");
            List<String> lore = new ArrayList<>();
            lore.add("§7效果: 力量 " + amplifier);
            lore.add("§7持续时间: " + (durationSeconds / 60) + "分"
                    + (durationSeconds % 60) + "秒");
            meta.setLore(lore);
            potion.setItemMeta(meta);
            return potion;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("创建活动力量药水失败: " + exception.getMessage());
            return null;
        }
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
