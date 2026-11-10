package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.bridge.SkyPVPItemBridge;
import com.remiaft.skypvpkits.data.PlayerKitData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单管理器
 * 负责创建和管理所有GUI界面
 * 
 * @author AngelHell
 */
public class MenuManager {

    private static final int[] MAIN_MENU_ICON_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32};
    private static final long AUTO_REFRESH_INTERVAL_TICKS = 600L;
    
    private final SkyPVPKits plugin;
    private final ConfigManager configManager;
    private final KitManager kitManager;
    
    private BukkitTask refreshTask;
    private final Set<UUID> openMenuPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> refreshingMenuPlayers = ConcurrentHashMap.newKeySet();
    
    public MenuManager(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.kitManager = plugin.getKitManager();
        
        // 启动自动刷新任务
        startAutoRefresh();
    }
    
    /**
     * 启动自动刷新任务
     */
    private void startAutoRefresh() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (openMenuPlayers.isEmpty()) {
                return;
            }
            for (UUID uuid : openMenuPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) {
                    openMenuPlayers.remove(uuid);
                    refreshingMenuPlayers.remove(uuid);
                    continue;
                }
                if (!isPlayerInRefreshableMenu(player)) {
                    refreshingMenuPlayers.remove(uuid);
                    continue;
                }
                if (refreshingMenuPlayers.add(uuid)) {
                    refreshPlayerMenu(player);
                }
            }
        }, AUTO_REFRESH_INTERVAL_TICKS, AUTO_REFRESH_INTERVAL_TICKS); // 每30秒刷新一次
    }
    
    /**
     * 停止自动刷新任务
     */
    public void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshingMenuPlayers.clear();
    }
    
    /**
     * 检查玩家是否在我们的菜单中
     */
    private boolean isPlayerInOurMenu(Player player) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
            return false;
        }
        String title = player.getOpenInventory().getTitle();
        if (title == null) {
            return false;
        }
        if (title.contains("礼包商城") || title.contains("装备展示")) {
            return true;
        }

        String cleanTitle = title.replaceAll("§.", "");
        for (String kitType : KitManager.KIT_TYPES) {
            String cleanKitName = configManager.getKitDisplayName(kitType).replaceAll("§.", "");
            if (cleanTitle.contains(cleanKitName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerInRefreshableMenu(Player player) {
        if (!isPlayerInOurMenu(player)) {
            return false;
        }
        String title = player.getOpenInventory().getTitle();
        return title != null && !title.contains("礼包商城") && !title.contains("装备展示");
    }
    
    /**
     * 刷新玩家的菜单
     */
    private void refreshPlayerMenu(Player player) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
            refreshingMenuPlayers.remove(player.getUniqueId());
            return;
        }
        
        String title = player.getOpenInventory().getTitle();
        if (title == null || title.contains("礼包商城") || title.contains("装备展示")) {
            refreshingMenuPlayers.remove(player.getUniqueId());
            return;
        }

        String kitType = extractKitTypeFromTitle(title);
        if (kitType != null) {
            updateKitDetailMenu(player.getOpenInventory().getTopInventory(), player, kitType);
        } else {
            refreshingMenuPlayers.remove(player.getUniqueId());
        }
    }
    
    /**
     * 更新主菜单图标
     */
    private void updateMainMenuIcons(Inventory inventory, Player player) {
        for (int i = 0; i < KitManager.KIT_TYPES.size() && i < MAIN_MENU_ICON_SLOTS.length; i++) {
            String kitType = KitManager.KIT_TYPES.get(i);
            if (configManager.isKitEnabled(kitType)) {
                ItemStack icon = createKitIcon(player, kitType);
                inventory.setItem(MAIN_MENU_ICON_SLOTS[i], icon);
            }
        }
    }
    
    /**
     * 更新礼包详情菜单
     */
    private void updateKitDetailMenu(Inventory inventory, Player player, String kitType) {
        // 重新加载等级信息
        loadKitLevels(inventory, player, kitType, () -> refreshingMenuPlayers.remove(player.getUniqueId()));
    }
    
    /**
     * 打开主菜单
     */
    public void openMainMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 
            configManager.getMainMenuSize(), 
            configManager.getMainMenuTitle());
        
        // 填充主菜单背景
        fillMainMenuBackground(inventory, configManager.getMainMenuBackground());
        
        // 放置礼包图标
        placeKitIcons(inventory, player);
        
        player.openInventory(inventory);
    }
    
    /**
     * 打开礼包详情菜单
     */
    public void openKitDetailMenu(Player player, String kitType) {
        if (!KitManager.KIT_TYPES.contains(kitType)) {
            player.sendMessage(configManager.getMessage("kit_not_found"));
            return;
        }
        
        String kitName = configManager.getKitDisplayName(kitType);
        String title = configManager.getKitDetailMenuTitle()
                .replace("{kit_name}", kitName)
                .replace("{level}", "总览");
        
        Inventory inventory = Bukkit.createInventory(null, 
            configManager.getKitDetailMenuSize(), 
            title);
        
        // 填充礼包详情菜单背景
        fillKitDetailBackground(inventory, configManager.getKitDetailMenuBackground());
        
        // 异步加载礼包等级信息
        loadKitLevels(inventory, player, kitType);
        
        player.openInventory(inventory);
    }
    
    /**
     * 打开礼包详情菜单 - 重载方法支持指定等级
     */
    public void openKitDetailMenu(Player player, String kitType, int level) {
        openKitLevelMenu(player, kitType, level);
    }
    
    /**
     * 打开特定礼包等级菜单
     */
    public void openKitLevelMenu(Player player, String kitType, int level) {
        if (!KitManager.KIT_TYPES.contains(kitType)) {
            player.sendMessage(configManager.getMessage("kit_not_found"));
            return;
        }
        
        int maxLevels = configManager.getKitLevels(kitType);
        if (level <= 0 || level > maxLevels) {
            player.sendMessage(configManager.getMessage("level_not_found"));
            return;
        }
        
        String kitName = configManager.getKitDisplayName(kitType);
        String title = configManager.getKitDetailMenuTitle()
                .replace("{kit_name}", kitName)
                .replace("{level}", String.valueOf(level));
        
        Inventory inventory = Bukkit.createInventory(null, 
            configManager.getKitDetailMenuSize(), 
            title);
        
        // 填充礼包等级详情菜单背景
        fillKitLevelDetailBackground(inventory, configManager.getKitDetailMenuBackground());
        
        // 异步加载礼包等级详情
        loadKitLevelDetail(inventory, player, kitType, level);
        
        player.openInventory(inventory);
    }
    
    /**
     * 为主菜单填充背景
     */
    private void fillMainMenuBackground(Inventory inventory, String backgroundConfig) {
        String[] parts = backgroundConfig.split(":");
        Material material = Material.valueOf(parts[0]);
        short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
        
        ItemStack background = new ItemStack(material, 1, data);
        ItemMeta meta = background.getItemMeta();
        meta.setDisplayName("§r");
        background.setItemMeta(meta);
        
        // 主菜单保护的位置：礼包图标位置 + 关闭按钮
        int[] protectedSlots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 49}; // 礼包图标位置 + 关闭按钮
        Set<Integer> protectedSet = new HashSet<>();
        for (int slot : protectedSlots) {
            protectedSet.add(slot);
        }
        
        // 填充非保护位置
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!protectedSet.contains(i)) {
                inventory.setItem(i, background);
            }
        }
    }
    
    /**
     * 为礼包详情菜单填充背景
     */
    private void fillKitDetailBackground(Inventory inventory, String backgroundConfig) {
        String[] parts = backgroundConfig.split(":");
        Material material = Material.valueOf(parts[0]);
        short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
        
        ItemStack background = new ItemStack(material, 1, data);
        ItemMeta meta = background.getItemMeta();
        meta.setDisplayName("§r");
        background.setItemMeta(meta);
        
        // 礼包详情菜单保护的位置：等级图标位置 + 返回按钮
        int[] protectedSlots = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32, 49}; // 等级位置 + 返回按钮
        Set<Integer> protectedSet = new HashSet<>();
        for (int slot : protectedSlots) {
            protectedSet.add(slot);
        }
        
        // 填充非保护位置
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!protectedSet.contains(i)) {
                inventory.setItem(i, background);
            }
        }
    }
    
    /**
     * 为礼包等级详情菜单填充背景
     */
    private void fillKitLevelDetailBackground(Inventory inventory, String backgroundConfig) {
        String[] parts = backgroundConfig.split(":");
        Material material = Material.valueOf(parts[0]);
        short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
        
        ItemStack background = new ItemStack(material, 1, data);
        ItemMeta meta = background.getItemMeta();
        meta.setDisplayName("§r");
        background.setItemMeta(meta);
        
        // 礼包等级详情菜单保护的位置：装备预览位置 + 按钮位置
        int[] protectedSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 40, 49}; // 预览区域 + 按钮
        Set<Integer> protectedSet = new HashSet<>();
        for (int slot : protectedSlots) {
            protectedSet.add(slot);
        }
        
        // 填充非保护位置
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!protectedSet.contains(i)) {
                inventory.setItem(i, background);
            }
        }
    }
    
    /**
     * 为装备展示菜单填充背景（避免覆盖展示物品）
     */
    private void fillItemDisplayBackground(Inventory inventory) {
        ItemStack background = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 7);
        ItemMeta meta = background.getItemMeta();
        meta.setDisplayName("§r");
        background.setItemMeta(meta);
        
        // 装备展示位置（需要保护的位置）
        int[] protectedSlots = {20, 11, 12, 21, 29, 30, 49}; // 装备位置 + 返回按钮位置
        Set<Integer> protectedSet = new HashSet<>();
        for (int slot : protectedSlots) {
            protectedSet.add(slot);
        }
        
        // 只填充非保护位置的空白位置
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!protectedSet.contains(i) && inventory.getItem(i) == null) {
                inventory.setItem(i, background);
            }
        }
    }
    
    /**
     * 放置礼包图标
     */
    private void placeKitIcons(Inventory inventory, Player player) {
        for (int i = 0; i < KitManager.KIT_TYPES.size() && i < MAIN_MENU_ICON_SLOTS.length; i++) {
            String kitType = KitManager.KIT_TYPES.get(i);
            if (configManager.isKitEnabled(kitType)) {
                ItemStack icon = createKitIcon(player, kitType);
                inventory.setItem(MAIN_MENU_ICON_SLOTS[i], icon);
            }
        }
        
        // 返回按钮
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c关闭菜单");
        closeMeta.setLore(Collections.singletonList("§7点击关闭此菜单"));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(49, closeItem);
    }
    
    /**
     * 创建礼包图标
     */
    private ItemStack createKitIcon(Player player, String kitType) {
        String iconConfig = configManager.getKitIcon(kitType);
        String[] parts = iconConfig.split(":");
        Material material = Material.valueOf(parts[0]);
        short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
        
        ItemStack icon = new ItemStack(material, 1, data);
        ItemMeta meta = icon.getItemMeta();
        
        meta.setDisplayName(configManager.getKitDisplayName(kitType));
        
        List<String> lore = new ArrayList<>(configManager.getKitDescription(kitType));
        lore.add("");
        
        // 同步获取玩家最高等级（使用缓存或简化版本）
        try {
            if (configManager.isNationalDayActive()) {
                String offer = "moon".equals(kitType) ? "月球 3 免费" : "earth".equals(kitType)
                        ? "地球 4 限时免费" : "mining".equals(kitType) ? "矿工 3 限时免费" : null;
                if (offer != null) {
                    lore.add("§a国庆活动：" + offer);
                    lore.add("§7截止：§e" + configManager.getNationalDayEndDisplay());
                    lore.add("§7已拥有可领升级奖励");
                }
            }
            // 这里先显示基础信息，点击后再加载详细信息
            lore.add("");
            lore.add("§e左键 §7查看礼包详情");
        } catch (Exception e) {
            plugin.getLogger().warning("创建礼包图标时出错: " + e.getMessage());
        }
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        
        return icon;
    }
    
    /**
     * 异步加载礼包等级
     */
    private void loadKitLevels(Inventory inventory, Player player, String kitType) {
        loadKitLevels(inventory, player, kitType, null);
    }

    private void loadKitLevels(Inventory inventory, Player player, String kitType, Runnable completion) {
        kitManager.getPlayerKitDataAsync(player)
                .whenComplete((playerData, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        if (throwable != null) {
                            plugin.getLogger().warning("刷新礼包菜单失败: " + throwable.getMessage());
                            return;
                        }
                        renderKitLevels(inventory, player, kitType, playerData);
                    } finally {
                        if (completion != null) {
                            completion.run();
                        }
                    }
                }));
    }

    private void renderKitLevels(Inventory inventory, Player player, String kitType, List<PlayerKitData> playerData) {
        int maxLevels = configManager.getKitLevels(kitType);

        // 等级图标位置
        int[] levelSlots = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32}; // 最多10个等级

        for (int level = 1; level <= maxLevels && level <= levelSlots.length; level++) {
            ItemStack levelIcon = createLevelIcon(player, kitType, level, playerData);
            inventory.setItem(levelSlots[level - 1], levelIcon);
        }

        KitManager.NationalDayReward activity = kitManager.resolveNationalDayReward(kitType, playerData);
        if (activity != null) {
            ItemStack offer = new ItemStack(activity.type == KitManager.NationalDayReward.Type.GEMS
                    ? Material.NETHER_STAR : Material.EXP_BOTTLE);
            ItemMeta offerMeta = offer.getItemMeta();
            offerMeta.setDisplayName("§6国庆免费领取");
            List<String> offerLore = new ArrayList<>();
            offerLore.add("§a" + (activity.type == KitManager.NationalDayReward.Type.GEMS
                    ? "完美淬炼宝石 ×5" : configManager.getKitDisplayName(activity.kitType) + " " + activity.level + " · 限时免费"));
            offerLore.add("§7" + activity.reason);
            offerLore.add("§7截止：§e" + configManager.getNationalDayEndDisplay());
            if (activity.type == KitManager.NationalDayReward.Type.GEMS) {
                offerLore.add("§7整个活动每人共 5 个，仅一次");
                offerLore.add("§7自动发放；背包满可在商城补领");
            } else {
                offerLore.add("§7活动期间每日可领取一次");
                offerLore.add("§e左键 §7领取今日活动奖励");
            }
            offerMeta.setLore(offerLore);
            offer.setItemMeta(offerMeta);
            inventory.setItem(40, offer);
        } else {
            inventory.setItem(40, null);
        }

        // 返回按钮
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§e返回主菜单");
        backMeta.setLore(Collections.singletonList("§7点击返回主菜单"));
        backItem.setItemMeta(backMeta);
        inventory.setItem(49, backItem);
    }
    
    /**
     * 创建等级图标
     */
    private ItemStack createLevelIcon(Player player, String kitType, int level, List<PlayerKitData> playerData) {
        boolean hasActiveLevel = playerData.stream()
                .anyMatch(data -> data.getKitType().equals(kitType) 
                               && data.getKitLevel() == level 
                               && !data.isExpired());
        boolean hasPermanentLevel = playerData.stream()
                .anyMatch(data -> data.getKitType().equals(kitType)
                        && data.getKitLevel() == level
                        && data.isPermanent()
                        && !data.isExpired());
        boolean hasTemporaryLevel = hasActiveLevel && !hasPermanentLevel;
        boolean hasPromotionAccess = kitManager.hasFreeAccessLevel(kitType, level, playerData);
        boolean isNationalDay = configManager.isNationalDayActive();
        int highestPermanentLevel = playerData.stream()
                .filter(data -> data.getKitType().equals(kitType) && data.isPermanent() && !data.isExpired())
                .mapToInt(PlayerKitData::getKitLevel)
                .max()
                .orElse(0);
        boolean hasHigherPermanentLevel = highestPermanentLevel > level;
        boolean missingPreviousPermanent = level > 1 && highestPermanentLevel < level - 1;
        
        Material material;
        if (hasPermanentLevel) {
            material = Material.EMERALD_BLOCK;
        } else if (hasTemporaryLevel) {
            material = Material.EMERALD;
        } else if (hasPromotionAccess) {
            material = Material.GOLD_BLOCK;
        } else if (hasHigherPermanentLevel) {
            material = Material.BARRIER;
        } else if (missingPreviousPermanent) {
            material = Material.COAL_BLOCK;
        } else {
            material = Material.REDSTONE_BLOCK;
        }
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        
        meta.setDisplayName(configManager.getKitDisplayName(kitType) + " §f- §e等级 " + level
                + (hasPromotionAccess ? " §a限时免费" : ""));

        List<String> lore = new ArrayList<>();
        
        if (hasPermanentLevel) {
            lore.add("§a✓ 已拥有此等级");
            
            // 查找对应数据
            PlayerKitData kitData = playerData.stream()
                    .filter(data -> data.getKitType().equals(kitType)
                            && data.getKitLevel() == level
                            && data.isPermanent()
                            && !data.isExpired())
                    .findFirst()
                    .orElse(null);
            
            if (kitData != null) {
                if (kitData.isPermanent()) {
                    lore.add("§7类型: §a永久");
                } else {
                    long remainingDays = kitData.getRemainingDays();
                    lore.add("§7类型: §e月付 §7(剩余 " + remainingDays + " 天)");
                }
                
                if (kitData.hasClaimedToday()) {
                    lore.add("§7今日奖励: §c已领取");
                } else {
                    lore.add("§7今日奖励: §a可领取");
                }
            }
            
            lore.add("");
            lore.add("§e点击 §7领取今日奖励");
        } else if (hasTemporaryLevel) {
            lore.add("§e⌛ 当前为限时/非永久礼包");

            PlayerKitData tempData = playerData.stream()
                    .filter(data -> data.getKitType().equals(kitType)
                            && data.getKitLevel() == level
                            && !data.isPermanent()
                            && !data.isExpired())
                    .findFirst()
                    .orElse(null);

            if (tempData != null) {
                lore.add("§7类型: §e限时 §7(剩余 " + tempData.getRemainingDays() + " 天)");
                lore.add(tempData.hasClaimedToday() ? "§7今日奖励: §c已领取" : "§7今日奖励: §a可领取");
            }

            if (level > 1 && highestPermanentLevel < level - 1) {
                lore.add("§c该等级暂时不能作为升级前置");
                lore.add("§7需要先永久拥有等级 " + (level - 1));
            }

            lore.add("");
            lore.add("§e左键 §7领取今日奖励");
            lore.add("§eShift+左键 §7购买当前等级永久版");
            if (plugin.getKitManager().supportsMonthlyPurchase(kitType, level)) {
                lore.add("§eShift+右键 §7硬币购买（月付）");
            }
            lore.add("§e右键 §7查看装备详情");
        } else if (hasPromotionAccess) {
            if (isNationalDay) {
                if ("moon".equalsIgnoreCase(kitType) && level == 3) {
                    lore.add("§a★ 国庆活动：月球 3 免费！");
                } else if ("earth".equalsIgnoreCase(kitType) && level == 4) {
                    lore.add("§a★ 国庆活动：地球 4 限时免费体验已解锁！");
                } else if ("mining".equalsIgnoreCase(kitType) && level == 3) {
                    lore.add("§a★ 国庆活动：矿工 3 限时免费体验已解锁！");
                } else {
                    lore.add("§a★ 国庆活动：免费升级！");
                }
                lore.add("§7免费截止：§e" + configManager.getNationalDayEndDisplay());
                lore.add("§7活动期间每日可免费领取一次");
            } else {
                lore.add("§6★ 全服免费体验已解锁");
                lore.add("§7活动结束：§f" + configManager.getAllKitsFreeEndDisplay());
            }
            lore.add("§7该权益不会替代永久/月付充值记录");
            lore.add("");
            lore.add("§e左键 §7领取今日奖励");
            lore.add("§eShift+左键 §7购买当前等级永久版");
            lore.add("§e右键 §7查看装备详情");
        } else if (hasHigherPermanentLevel) {
            lore.add("§e↗ 已永久拥有更高等级：Lv." + highestPermanentLevel);
            lore.add("§7当前等级已被更高礼包覆盖");
            lore.add("§7不能重复购买，也不能单独领取低等级奖励");
            lore.add("");
            lore.add("§e右键 §7查看装备详情");
        } else if (missingPreviousPermanent) {
            lore.add("§c✗ 尚未满足购买条件");
            lore.add("§7需要先永久拥有等级 " + (level - 1));
            lore.add("§7限时/月付礼包不能作为升级前置");
            lore.add("");
            lore.add("§e右键 §7查看装备详情");
        } else {
            lore.add("§c✗ 尚未拥有此等级");
            
            // 显示价格信息
            List<Integer> prices = configManager.getKitPermanentPrices(kitType);
            if (level <= prices.size()) {
                int basePrice = prices.get(level - 1);
                double discount = configManager.getKitLevelDiscount(kitType, level);
                int finalPrice = (int) Math.ceil(basePrice * discount);
                
                lore.add("");
                lore.add("§6永久购买价格:");
                if (discount < 1.0) {
                    if (discount == 0.0) {
                        lore.add("§a免费！");
                    } else {
                        lore.add("§7原价: §c" + basePrice + " 点券");
                        lore.add("§7折扣: §e" + (int)(discount * 100) + "%");
                        lore.add("§7现价: §a" + finalPrice + " 点券");
                    }
                } else {
                    lore.add("§7价格: §a" + finalPrice + " 点券");
                }
                
                // 检查是否支持月付，如果支持才显示硬币价格
                if (plugin.getKitManager().supportsMonthlyPurchase(kitType, level)) {
                int coinsMonthlyPrice = finalPrice * 100;
                lore.add("");
                lore.add("§6硬币月付价格:");
                lore.add("§7价格: §e" + coinsMonthlyPrice + " 硬币");
                }
                
                // 特殊提示
                if (!configManager.isKitSupportPoints(kitType)) {
                    lore.add("");
                    lore.add("§c该礼包暂不支持点券购买");
                    lore.add("§e请联系: §f" + configManager.getContactQQ());
                }
            }
            
            lore.add("");
            lore.add("§e左键 §7点券购买（永久）");
            lore.add("§e右键 §7查看装备详情");
            // 只有支持月付的礼包才显示Shift+左键提示
            if (plugin.getKitManager().supportsMonthlyPurchase(kitType, level)) {
            lore.add("§eShift+左键 §7硬币购买（月付）");
            }
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        icon.setItemMeta(meta);
        
        return icon;
    }
    
    /**
     * 生成正确的物品键名
     */
    private String generateItemKey(String kitType, int level, String equipType) {
        // 根据items.yml中的实际格式生成物品键名
        switch (kitType) {
            case "sky":
                // 天空系列：1天空剑, 1天空头盔, 1天空胸甲, 1天空护腿, 1天空鞋子
                return level + "天空" + equipType;
                
            case "mars":
                // 火星系列：1火星spark剑, 1火星spark头盔, 等
                return level + "火星spark" + equipType;
                
            case "earth":
                // 地球系列：地球1_剑, 地球1_头, 地球1_甲, 地球1_腿, 地球1_鞋
                String earthEquipType = equipType;
                if (equipType.equals("剑")) earthEquipType = "剑";
                else if (equipType.equals("头盔")) earthEquipType = "头";
                else if (equipType.equals("胸甲")) earthEquipType = "甲";
                else if (equipType.equals("护腿")) earthEquipType = "腿";
                else if (equipType.equals("鞋子")) earthEquipType = "鞋";
                return "地球" + level + "_" + earthEquipType;
                
            case "moon":
                // 月球系列：1月球moon剑, 1月球moon头盔, 等
                return level + "月球moon" + equipType;
                
            case "sun":
                // 太阳系列：1太阳sun剑, 1太阳sun头盔, 等
                return level + "太阳sun" + equipType;
                
            case "mining":
                // 挖矿系列：1挖矿mining稿 (只有稿子，没有装备)
                if (equipType.equals("剑")) {
                    return level + "挖矿mining稿";
                } else {
                    return null; // 挖矿礼包没有其他装备
                }
                
            case "galaxy":
                // 新银河系列：新银河1_剑, 新银河1_头盔, 等
                String galaxyEquipType = equipType;
                if (equipType.equals("护腿")) galaxyEquipType = "裤";
                return "新银河" + level + "_" + galaxyEquipType;

            case "zxgy":
                return null;
                
            default:
                return level + kitType + equipType;
        }
    }
    
    /**
     * 异步加载礼包等级详情
     */
    private void loadKitLevelDetail(Inventory inventory, Player player, String kitType, int level) {
        // 显示详细的礼包内容
        kitManager.getPlayerKitDataAsync(player).thenAccept(playerData -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean hasActiveLevel = playerData.stream()
                        .anyMatch(data -> data.getKitType().equals(kitType)
                                && data.getKitLevel() == level
                                && !data.isExpired());
                boolean hasPermanentLevel = playerData.stream()
                        .anyMatch(data -> data.getKitType().equals(kitType)
                                && data.getKitLevel() == level
                                && data.isPermanent()
                                && !data.isExpired());
                boolean hasTemporaryLevel = hasActiveLevel && !hasPermanentLevel;
                boolean hasPromotionAccess = kitManager.hasFreeAccessLevel(kitType, level);
                int highestPermanentLevel = playerData.stream()
                        .filter(data -> data.getKitType().equals(kitType) && data.isPermanent() && !data.isExpired())
                        .mapToInt(PlayerKitData::getKitLevel)
                        .max()
                        .orElse(0);
                boolean hasHigherPermanentLevel = highestPermanentLevel > level;
                boolean missingPreviousPermanent = level > 1 && highestPermanentLevel < level - 1;

                // 中心显示礼包信息
                ItemStack infoItem = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta infoMeta = infoItem.getItemMeta();
                infoMeta.setDisplayName("§6§l" + configManager.getKitDisplayName(kitType) + " §f- §e等级 " + level);

                List<String> lore = new ArrayList<>();
                lore.add("§8✧═══════════════✧");
                lore.add("");
                if (hasPermanentLevel) {
                    lore.add("§7状态：§a已永久拥有 §f(｡◕‿◕｡)");
                } else if (hasTemporaryLevel) {
                    lore.add("§7状态：§e当前为限时/非永久拥有 §f(｡◕‿◕｡)");
                } else if (hasPromotionAccess) {
                    lore.add("§7状态：§6全服免费体验 §f(｡◕‿◕｡)");
                    lore.add("§7结束时间：§f" + configManager.getAllKitsFreeEndDisplay());
                } else if (hasHigherPermanentLevel) {
                    lore.add("§7状态：§e已永久拥有更高等级 Lv." + highestPermanentLevel + " §f(｡◕‿◕｡)");
                } else if (missingPreviousPermanent) {
                    lore.add("§7状态：§6需先补永久前置等级 §f(´・ω・`)");
                } else {
                    lore.add("§7状态：§c未拥有 §f(´・ω・`)");
                }
                lore.add("");

                // 显示包含的装备
                lore.add("§7装备配置：");
                for (String equipmentLine : buildEquipmentSummary(kitType, level)) {
                    lore.add(equipmentLine);
                }

                lore.add("");

                if (hasHigherPermanentLevel) {
                    lore.add("§7购买状态：");
                    lore.add("  §e你已永久拥有更高等级礼包");
                    lore.add("  §7当前等级无需重复购买");
                } else if (hasTemporaryLevel || hasPromotionAccess) {
                    lore.add("§7购买状态：");
                    lore.add(hasPromotionAccess
                            ? "  §6当前由全服免费体验临时解锁"
                            : "  §e当前仅为限时/非永久拥有");
                    lore.add("  §7如需作为升级前置，必须购买本级永久版");
                } else if (missingPreviousPermanent) {
                    lore.add("§7购买状态：");
                    lore.add("  §c需要先永久拥有等级 " + (level - 1));
                    lore.add("  §7限时/月付礼包不能作为升级前置");
                } else if (!hasPermanentLevel) {
                    lore.add("§7价格信息：");
                    List<Integer> prices = configManager.getKitPermanentPrices(kitType);
                    if (level <= prices.size()) {
                        int basePrice = prices.get(level - 1);
                        double discount = configManager.getKitLevelDiscount(kitType, level);
                        int finalPrice = (int) Math.ceil(basePrice * discount);
                        if (discount < 1.0D && discount > 0.0D) {
                            lore.add("  §c原价 " + basePrice + " 点券");
                            lore.add("  §e折后 " + finalPrice + " 点券 §8(永久)");
                        } else {
                            lore.add("  §a" + finalPrice + " 点券 §8(永久)");
                        }
                        // 只有支持月付的礼包才显示硬币价格
                        if (plugin.getKitManager().supportsMonthlyPurchase(kitType, level)) {
                            lore.add("  §e" + (finalPrice * 100) + " 硬币 §8(月度订阅)");
                        }
                    }
                }

                lore.add("");
                lore.add("§8✧═══════════════✧");

                infoMeta.setLore(lore);
                infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                infoItem.setItemMeta(infoMeta);
                inventory.setItem(22, infoItem);

                // 左侧显示预览物品（简化版）
                displayKitPreview(inventory, kitType, level);
            });
        });
        
        // 返回按钮
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§e返回礼包菜单");
        backMeta.setLore(Collections.singletonList("§7点击返回 " + configManager.getKitDisplayName(kitType) + " 菜单"));
        backItem.setItemMeta(backMeta);
        inventory.setItem(49, backItem);
    }
    
    /**
     * 显示礼包预览物品
     */
    private void displayKitPreview(Inventory inventory, String kitType, int level) {
        int[] previewSlots = getPreviewSlots(kitType);
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};

        List<ItemStack> rewardItems = getMenuPreviewItems(kitType, level);

        if (plugin.getAngelRewards() != null) {
            for (int i = 0; i < Math.min(previewSlots.length, rewardItems.size()); i++) inventory.setItem(previewSlots[i], rewardItems.get(i).clone());
            return;
        }

        if (!rewardItems.isEmpty()) {
            for (int i = 0; i < Math.min(previewSlots.length, rewardItems.size()); i++) {
                ItemStack rewardItem = rewardItems.get(i);
                if (rewardItem != null) {
                    ItemStack previewItem = rewardItem.clone();
                    ItemMeta meta = previewItem.getItemMeta();
                    meta.setLore(Arrays.asList(
                        "§7物品: §f" + meta.getDisplayName(),
                        "§7等级: §e" + level,
                        "",
                        "§e右键查看详细装备信息"
                    ));
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                    previewItem.setItemMeta(meta);
                    
                    inventory.setItem(previewSlots[i], previewItem);
                }
            }
        } else {
            // 使用传统方式
            for (int i = 0; i < equipTypes.length; i++) {
                String itemKey = generateItemKey(kitType, level, equipTypes[i]);
                ItemStack previewItem = getItemFromConfig(itemKey);
                
                if (previewItem != null) {
                    // 简化lore，只显示名称和类型
                    ItemMeta meta = previewItem.getItemMeta();
                    meta.setLore(Arrays.asList(
                        "§7类型: §f" + equipTypes[i],
                        "§7等级: §e" + level,
                        "",
                        "§e右键查看详细装备信息"
                    ));
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                    previewItem.setItemMeta(meta);
                    
                    inventory.setItem(previewSlots[i], previewItem);
                } else {
                    // 创建占位符
                    ItemStack placeholder = createDefaultDisplayItem(kitType, level, equipTypes[i]);
                    ItemMeta meta = placeholder.getItemMeta();
                    List<String> lore = new ArrayList<>(meta.getLore());
                    lore.add("");
                    lore.add("§c该物品暂未配置");
                    meta.setLore(lore);
                    placeholder.setItemMeta(meta);
                    
                    inventory.setItem(previewSlots[i], placeholder);
                }
            }
        }
    }
    
    /**
     * 检查菜单类型
     */
    public MenuType getMenuType(String title) {
        if (title.equals(configManager.getMainMenuTitle())) {
            return MenuType.MAIN_MENU;
        } else if (title.contains("装备展示")) {
            return MenuType.ITEM_DISPLAY;
        } else if (title.contains("等级") || title.contains("总览")) {
            return MenuType.KIT_DETAIL;
        }
        return MenuType.UNKNOWN;
    }
    
    /**
     * 从菜单标题提取礼包类型
     */
    public String extractKitTypeFromTitle(String title) {
        for (String kitType : KitManager.KIT_TYPES) {
            String displayName = configManager.getKitDisplayName(kitType);
            // 去除颜色代码后比较
            String cleanDisplayName = displayName.replaceAll("§.", "");
            String cleanTitle = title.replaceAll("§.", "");
            if (cleanTitle.contains(cleanDisplayName)) {
                return kitType;
            }
        }
        return null;
    }
    
    /**
     * 打开物品展示菜单
     */
    public void openItemDisplayMenu(Player player, String kitType, int level) {
        // 挖矿礼包不显示物品详情，只显示功能说明
        if (kitType.equals("mining") && plugin.getAngelRewards() == null) {
            displayMiningKitInfo(player, level);
            return;
        }

        String kitName = configManager.getKitDisplayName(kitType);
        String title = "§8装备展示 §7- " + kitName + " §e等级" + level;
        
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        
        // 填充背景
        fillItemDisplayBackground(inventory);

        // 物品展示不依赖玩家数据库数据。模板已在插件启用阶段预热，此处只克隆缓存，
        // 因而可以在当前主线程一次性完成并只打开一次背包。
        loadItemsFromConfig(inventory, player, kitType, level);
        player.openInventory(inventory);
    }
    
    /**
     * 显示挖矿礼包信息（不显示物品）
     */
    private void displayMiningKitInfo(Player player, int level) {
        String kitName = configManager.getKitDisplayName("mining");
        player.sendMessage("§6★ " + kitName + " §eLv." + level + " §6(｡◕‿◕｡)");
        player.sendMessage("§7工具类型：挖矿镐");
        player.sendMessage("§7效率等级：" + (level + 2) + " §8| §7耐久等级：" + (level + 1));
        
        if (level >= 3) {
            player.sendMessage("§a✦ 精准采集");
        }
        
        if (level >= 4) {
            player.sendMessage("§a✦ 时运 III");
        }
        
        player.sendMessage("§8专注挖矿效率，无装备展示~ §f(´∀｀)");
    }


    /**
     * 从配置文件加载物品
     */
    private void loadItemsFromConfig(Inventory inventory, Player player, String kitType, int level) {
        int[] equipSlots = getItemDisplaySlots(kitType);
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};

        List<ItemStack> rewardItems = getMenuPreviewItems(kitType, level);
        
        if (!rewardItems.isEmpty()) {
            // 显示所有奖励物品 - 保持原始物品信息，不添加额外的礼包信息
            for (int i = 0; i < rewardItems.size() && i < equipSlots.length; i++) {
                ItemStack item = rewardItems.get(i);
                if (item != null) {
                    // 直接使用原始物品，不添加额外信息
                    inventory.setItem(equipSlots[i], item.clone());
                }
            }
        } else {
            // 如果没有配置奖励，显示传统方式
            for (int i = 0; i < equipTypes.length; i++) {
                String itemKey = generateItemKey(kitType, level, equipTypes[i]);
                ItemStack item = getItemFromConfig(itemKey);
                
                if (item != null) {
                    // 直接使用配置中的物品，保持原始外观
                    inventory.setItem(equipSlots[i], item.clone());
                } else {
                    // 创建默认显示物品
                    ItemStack defaultItem = createDefaultDisplayItem(kitType, level, equipTypes[i]);
                    inventory.setItem(equipSlots[i], defaultItem);
                }
            }
        }
    }
    
    /**
     * 从配置或EZItem获取物品
     */
    private ItemStack getItemFromConfig(String itemKey) {
        try {
            if (plugin.getEZItemIntegration() != null && plugin.getEZItemIntegration().isEZItemEnabled()) {
                return plugin.getEZItemIntegration().getItemByTemplate(itemKey);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法加载物品: " + itemKey);
        }
        return null;
    }
    
    /**
     * 创建默认显示物品
     */
    private ItemStack createDefaultDisplayItem(String kitType, int level, String equipType) {
        if ("zxgy".equals(kitType) || "ywxc".equals(kitType)) {
            return createBuiltInFallbackItem(kitType, equipType);
        }

        Material material;
        
        // 根据装备类型选择材质
        switch (equipType) {
            case "剑":
                if (kitType.equals("mining")) {
                    material = Material.DIAMOND_PICKAXE;
                } else {
                    material = Material.DIAMOND_SWORD;
                }
                break;
            case "头盔":
                material = Material.DIAMOND_HELMET;
                break;
            case "胸甲":
                material = Material.DIAMOND_CHESTPLATE;
                break;
            case "护腿":
                material = Material.DIAMOND_LEGGINGS;
                break;
            case "鞋子":
                material = Material.DIAMOND_BOOTS;
                break;
            default:
                material = Material.DIAMOND_SWORD;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        String kitName = configManager.getKitDisplayName(kitType);
        meta.setDisplayName(kitName + " " + equipType);
        
        List<String> lore = new ArrayList<>();
        lore.add("§7等级 " + level + " 装备");
        lore.add("");
        
        // 根据礼包类型添加特色说明
        switch (kitType) {
            case "sky":
                lore.add("§b基础装备，适合新手");
                break;
            case "mars":
            case "earth":
                lore.add("§c进阶装备，战斗专用");
                break;
            case "moon":
                lore.add("§f高级装备，附带特殊保护");
                break;
            case "sun":
                lore.add("§e终极装备，无与伦比");
                break;
            case "mining":
                if (equipType.equals("剑")) {
                    lore.add("§8专业挖矿工具");
                    lore.add("§e效率挖掘 " + (level + 2));
                    lore.add("§e耐久 " + (level + 1));
                    if (level >= 3) {
                        lore.add("§a精准采集");
                    }
                } else {
                    lore.add("§8挖矿防护装备");
                }
                break;
            case "galaxy":
                lore.add("§d新银河系列装备");
                lore.add("§6顶级防护和攻击");
                lore.add("§5银河之力加持");
                break;
        }
        
        lore.add("");
        lore.add("§7购买礼包后可获得");
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        
        return item;
    }
    
    private List<String> buildEquipmentSummary(String kitType, int level) {
        if ("zxgy".equals(kitType)) {
            return Arrays.asList(
                "  §8战斧：§a✓",
                "  §8战弓：§a✓",
                "  §8头盔：§a✓",
                "  §8胸甲：§a✓",
                "  §8护腿：§a✓",
                "  §8靴子：§a✓"
            );
        }
        if ("ywxc".equals(kitType)) {
            return Arrays.asList(
                "  §8观星剑：§a✓",
                "  §8巡天弓：§a✓",
                "  §8头盔：§a✓",
                "  §8胸甲：§a✓",
                "  §8护腿：§a✓",
                "  §8靴子：§a✓"
            );
        }
        if ("mining".equals(kitType)) {
            return Arrays.asList(
                "  §8专业矿稿：§a✓",
                "  §8矿工营养胶囊：§a✓"
            );
        }

        List<String> summary = new ArrayList<>();
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "靴子"};
        String[] displayEquipTypes = {"剑", "头盔", "胸甲", "护腿", "靴子"};
        for (int i = 0; i < equipTypes.length; i++) {
            String itemKey = generateItemKey(kitType, level, equipTypes[i]);
            ItemStack configItem = itemKey == null ? null : getItemFromConfig(itemKey);
            summary.add("  §8" + displayEquipTypes[i] + "：" + (configItem != null ? "§a✓" : "§7×"));
        }
        return summary;
    }

    private int[] getPreviewSlots(String kitType) {
        if ("zxgy".equals(kitType) || "ywxc".equals(kitType)) {
            return new int[] {10, 11, 19, 28, 37, 46};
        }
        return new int[] {10, 19, 28, 37, 46};
    }

    private int[] getItemDisplaySlots(String kitType) {
        if (plugin.getAngelRewards() != null && !"zxgy".equals(kitType) && !"ywxc".equals(kitType))
            return new int[] {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        if ("zxgy".equals(kitType) || "ywxc".equals(kitType)) {
            return new int[] {20, 11, 12, 21, 29, 30};
        }
        return new int[] {20, 11, 12, 21, 30};
    }

    private List<ItemStack> getMenuPreviewItems(String kitType, int level) {
        if (plugin.getAngelRewards() != null) {
            try {
                if ("zxgy".equals(kitType) || "ywxc".equals(kitType)) return SkyPVPItemBridge.previewSet(kitType);
                return plugin.getAngelRewards().preview(kitType, level);
            }
            catch (RuntimeException failure) {
                ItemStack unavailable = new ItemStack(Material.BARRIER);
                ItemMeta meta = unavailable.getItemMeta(); meta.setDisplayName("\u00a7c\u6682\u4e0d\u53ef\u7528"); unavailable.setItemMeta(meta);
                return Collections.singletonList(unavailable);
            }
        }
        if ("zxgy".equals(kitType)) {
            return createZxgyPreviewItems();
        }
        if ("ywxc".equals(kitType)) {
            return createYwxcPreviewItems();
        }

        List<ItemStack> items = new ArrayList<>();
        for (String itemKey : configManager.getKitRewardItems(kitType, level)) {
            if (itemKey == null || itemKey.trim().isEmpty()) {
                continue;
            }

            ItemStack item = getItemFromConfig(itemKey);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private ItemStack createBuiltInFallbackItem(String kitType, String equipType) {
        if ("zxgy".equals(kitType)) {
            return createZxgyFallbackItem(equipType);
        }
        return createYwxcFallbackItem(equipType);
    }

    private List<ItemStack> createZxgyPreviewItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(createPreviewItem(
            Material.DIAMOND_AXE,
            "§5沾星战斧",
            Arrays.asList(
                "§7星辰的惩罚",
                "§7造成伤害时固定削减目标 §c1点生命上限",
                "§7目标死亡后生命上限恢复"
            ),
            Enchantment.DAMAGE_ALL,
            5
        ));
        items.add(createPreviewItem(
            Material.BOW,
            "§d顾月长弓",
            Arrays.asList(
                "§7星月系远程武器",
                "§7命中时触发专属箭矢特效"
            ),
            Enchantment.ARROW_DAMAGE,
            4
        ));
        items.add(createPreviewArmor(Material.DIAMOND_HELMET, "§d顾月兜鍪", "§7星月护具部件"));
        items.add(createPreviewArmor(
            Material.DIAMOND_CHESTPLATE,
            "§5沾星胸甲",
            "§7保护 IV",
            "§7额外受到伤害减少 §a1% - 3%"
        ));
        items.add(createPreviewArmor(Material.DIAMOND_LEGGINGS, "§d顾月护腿", "§7星月护具部件"));
        items.add(createPreviewArmor(Material.DIAMOND_BOOTS, "§d顾月战靴", "§7星月护具部件"));
        return items;
    }

    private ItemStack createZxgyFallbackItem(String equipType) {
        if ("剑".equals(equipType)) {
            return createPreviewItem(
                Material.DIAMOND_AXE,
                "§5沾星战斧",
                Arrays.asList(
                    "§7星辰的惩罚",
                    "§7造成伤害时固定削减目标 §c1点生命上限"
                ),
                Enchantment.DAMAGE_ALL,
                5
            );
        }
        if ("头盔".equals(equipType)) {
            return createPreviewArmor(Material.DIAMOND_HELMET, "§d顾月兜鍪", "§7星月护具部件");
        }
        if ("胸甲".equals(equipType)) {
            return createPreviewArmor(
                Material.DIAMOND_CHESTPLATE,
                "§5沾星胸甲",
                "§7保护 IV",
                "§7额外受到伤害减少 §a1% - 3%"
            );
        }
        if ("护腿".equals(equipType)) {
            return createPreviewArmor(Material.DIAMOND_LEGGINGS, "§d顾月护腿", "§7星月护具部件");
        }
        return createPreviewArmor(Material.DIAMOND_BOOTS, "§d顾月战靴", "§7星月护具部件");
    }

    private List<ItemStack> createYwxcPreviewItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(createPreviewItem(
            Material.DIAMOND_SWORD,
            "§b观星剑",
            Arrays.asList(
                "§7三次有效伤害后触发一次压制",
                "§7若目标拥有生命恢复，则强制缩短 §c1秒",
                "§75秒冷却"
            ),
            Enchantment.DAMAGE_ALL,
            6
        ));
        items.add(createPreviewItem(
            Material.BOW,
            "§b巡天弓",
            Arrays.asList(
                "§7远程连续命中可压制目标恢复",
                "§73次命中触发，5秒冷却"
            ),
            Enchantment.ARROW_DAMAGE,
            6
        ));
        items.add(createPreviewArmor(
            Material.DIAMOND_HELMET,
            "§b星穹盔",
            "§7保护 VI",
            "§7受到伤害固定减少 §a5%"
        ));
        items.add(createPreviewArmor(
            Material.DIAMOND_CHESTPLATE,
            "§b星穹甲",
            "§7保护 VI",
            "§7若攻击者有力量 III+，其伤害再降低 §a30%"
        ));
        items.add(createPreviewArmor(Material.DIAMOND_LEGGINGS, "§b星穹腿", "§7高防星辉护腿"));
        items.add(createPreviewArmor(Material.DIAMOND_BOOTS, "§b星穹靴", "§7高防星辉战靴"));
        return items;
    }

    private ItemStack createYwxcFallbackItem(String equipType) {
        if ("剑".equals(equipType)) {
            return createPreviewItem(
                Material.DIAMOND_SWORD,
                "§b观星剑",
                Arrays.asList(
                    "§7三次有效伤害后压制目标的生命恢复",
                    "§75秒冷却"
                ),
                Enchantment.DAMAGE_ALL,
                6
            );
        }
        if ("头盔".equals(equipType)) {
            return createPreviewArmor(Material.DIAMOND_HELMET, "§b星穹盔", "§7保护 VI", "§7固定减伤 5%");
        }
        if ("胸甲".equals(equipType)) {
            return createPreviewArmor(
                Material.DIAMOND_CHESTPLATE,
                "§b星穹甲",
                "§7保护 VI",
                "§7对力量 III+ 攻击者额外压伤害"
            );
        }
        if ("护腿".equals(equipType)) {
            return createPreviewArmor(Material.DIAMOND_LEGGINGS, "§b星穹腿", "§7高防星辉护腿");
        }
        return createPreviewArmor(Material.DIAMOND_BOOTS, "§b星穹靴", "§7高防星辉战靴");
    }

    private ItemStack createPreviewArmor(Material material, String displayName, String... loreLines) {
        return createPreviewItem(
            material,
            displayName,
            Arrays.asList(loreLines),
            Enchantment.PROTECTION_ENVIRONMENTAL,
            4
        );
    }

    private ItemStack createPreviewItem(Material material, String displayName, List<String> loreLines,
                                        Enchantment enchantment, int enchantLevel) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(new ArrayList<>(loreLines));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(enchantment, enchantLevel);
        return item;
    }

    public enum MenuType {
        MAIN_MENU,
        KIT_DETAIL,
        ITEM_DISPLAY,
        UNKNOWN
    }

    public void onMenuOpen(Player player) {
        openMenuPlayers.add(player.getUniqueId());
    }

    public void onMenuClose(Player player) {
        openMenuPlayers.remove(player.getUniqueId());
    }
} 
