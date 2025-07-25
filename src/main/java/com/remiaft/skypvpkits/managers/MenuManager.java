package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.data.PlayerKitData;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 菜单管理器
 * 负责创建和管理所有GUI界面
 * 
 * @author AngelHell
 */
public class MenuManager {
    
    private final SkyPVPKits plugin;
    private final ConfigManager configManager;
    private final KitManager kitManager;
    
    // 存储玩家当前打开的菜单信息
    private final Map<UUID, String> playerMenus = new HashMap<>();
    private BukkitTask refreshTask;
    private final Set<UUID> openMenuPlayers = ConcurrentHashMap.newKeySet();
    
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
            for (UUID uuid : openMenuPlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && isPlayerInOurMenu(player)) {
                    // 异步准备数据
                    CompletableFuture.runAsync(() -> {
                        // 这里可以做数据准备（如数据库、缓存等）
                        // ...
                        // 数据准备好后，主线程刷新UI
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            refreshPlayerMenu(player); // 只做UI操作
                        });
                    });
                }
            }
        }, 100L, 100L); // 每5秒刷新一次
    }
    
    /**
     * 停止自动刷新任务
     */
    public void stopAutoRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
    }
    
    /**
     * 检查玩家是否在我们的菜单中
     */
    private boolean isPlayerInOurMenu(Player player) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
            return false;
        }
        String title = player.getOpenInventory().getTitle();
        return title != null && (
            title.contains("礼包商城") || 
            title.contains("天空礼包") ||
            title.contains("火星礼包") ||
            title.contains("地球礼包") ||
            title.contains("月球礼包") ||
            title.contains("太阳礼包") ||
            title.contains("挖矿礼包") ||
            title.contains("银河礼包") ||
            title.contains("装备展示")  // 添加装备展示菜单识别
        );
    }
    
    /**
     * 刷新玩家的菜单
     */
    private void refreshPlayerMenu(Player player) {
        if (player.getOpenInventory() == null || player.getOpenInventory().getTopInventory() == null) {
            return;
        }
        
        String title = player.getOpenInventory().getTitle();
        if (title.contains("礼包商城")) {
            // 刷新主菜单
            updateMainMenuIcons(player.getOpenInventory().getTopInventory(), player);
        } else if (title.contains("装备展示")) {
            // 装备展示菜单不需要刷新，保持静态显示
            return;
        } else {
            // 刷新礼包详情菜单
            String kitType = extractKitTypeFromTitle(title);
            if (kitType != null) {
                updateKitDetailMenu(player.getOpenInventory().getTopInventory(), player, kitType);
            }
        }
    }
    
    /**
     * 更新主菜单图标
     */
    private void updateMainMenuIcons(Inventory inventory, Player player) {
        int[] iconSlots = {20, 21, 22, 23, 24, 29, 30};
        
        for (int i = 0; i < KitManager.KIT_TYPES.size() && i < iconSlots.length; i++) {
            String kitType = KitManager.KIT_TYPES.get(i);
            if (configManager.isKitEnabled(kitType)) {
                ItemStack icon = createKitIcon(player, kitType);
                inventory.setItem(iconSlots[i], icon);
            }
        }
    }
    
    /**
     * 更新礼包详情菜单
     */
    private void updateKitDetailMenu(Inventory inventory, Player player, String kitType) {
        // 重新加载等级信息
        loadKitLevels(inventory, player, kitType);
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
        int[] protectedSlots = {20, 21, 22, 23, 24, 29, 30, 49}; // 礼包图标位置 + 关闭按钮
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
        int[] protectedSlots = {20, 11, 12, 21, 30, 49}; // 装备位置 + 返回按钮位置
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
        // 礼包图标位置 (3x3布局中心区域)
        int[] iconSlots = {20, 21, 22, 23, 24, 29, 30}; // 天空、火星、地球、月球、太阳、挖矿、银河
        
        for (int i = 0; i < KitManager.KIT_TYPES.size() && i < iconSlots.length; i++) {
            String kitType = KitManager.KIT_TYPES.get(i);
            if (configManager.isKitEnabled(kitType)) {
                ItemStack icon = createKitIcon(player, kitType);
                inventory.setItem(iconSlots[i], icon);
            }
        }
        
        // 返回按钮
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c关闭菜单");
        closeMeta.setLore(Arrays.asList("§7点击关闭此菜单"));
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
            // 这里先显示基础信息，点击后再加载详细信息
            lore.add("§7点击查看详细信息");
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
        kitManager.getPlayerKitDataAsync(player).thenAccept(playerData -> {
            int maxLevels = configManager.getKitLevels(kitType);
            
            // 等级图标位置
            int[] levelSlots = {19, 20, 21, 22, 23, 28, 29, 30, 31, 32}; // 最多10个等级
            
            for (int level = 1; level <= maxLevels && level <= levelSlots.length; level++) {
                ItemStack levelIcon = createLevelIcon(player, kitType, level, playerData);
                inventory.setItem(levelSlots[level - 1], levelIcon);
            }
            
            // 返回按钮
            ItemStack backItem = new ItemStack(Material.ARROW);
            ItemMeta backMeta = backItem.getItemMeta();
            backMeta.setDisplayName("§e返回主菜单");
            backMeta.setLore(Arrays.asList("§7点击返回主菜单"));
            backItem.setItemMeta(backMeta);
            inventory.setItem(49, backItem);
        });
    }
    
    /**
     * 创建等级图标
     */
    private ItemStack createLevelIcon(Player player, String kitType, int level, List<PlayerKitData> playerData) {
        // 检查玩家是否拥有此等级
        boolean hasLevel = playerData.stream()
                .anyMatch(data -> data.getKitType().equals(kitType) 
                               && data.getKitLevel() == level 
                               && !data.isExpired());
        
        // 根据拥有状态选择材料
        Material material = hasLevel ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK;
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        
        meta.setDisplayName(configManager.getKitDisplayName(kitType) + " §f- §e等级 " + level);
        
        List<String> lore = new ArrayList<>();
        
        if (hasLevel) {
            lore.add("§a✓ 已拥有此等级");
            
            // 查找对应数据
            PlayerKitData kitData = playerData.stream()
                    .filter(data -> data.getKitType().equals(kitType) && data.getKitLevel() == level)
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
                // 银河系列：银河剑_1, 银河头盔_1, 等
                return "银河" + equipType + "_" + level;
                
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
            boolean hasLevel = playerData.stream()
                    .anyMatch(data -> data.getKitType().equals(kitType) 
                                   && data.getKitLevel() == level 
                                   && !data.isExpired());
            
            // 中心显示礼包信息
            ItemStack infoItem = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName("§6§l" + configManager.getKitDisplayName(kitType) + " §f- §e等级 " + level);
            
            List<String> lore = new ArrayList<>();
            lore.add("§8✧═══════════════✧");
            lore.add("");
            lore.add("§7状态：" + (hasLevel ? "§a已拥有 §f(｡◕‿◕｡)" : "§c未拥有 §f(´・ω・`)"));
            lore.add("");
            
            // 显示包含的装备
            lore.add("§7装备配置：");
            String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "靴子"};
            String[] displayEquipTypes = {"剑", "头盔", "胸甲", "护腿", "靴子"};
            for (int i = 0; i < equipTypes.length; i++) {
                String itemKey = generateItemKey(kitType, level, equipTypes[i]);
                ItemStack configItem = plugin.getItemLoader().getItemFromConfig(itemKey);
                lore.add("  §8" + displayEquipTypes[i] + "：" + (configItem != null ? "§a✓" : "§7×"));
            }
            
            lore.add("");

            
            if (!hasLevel) {
                lore.add("§7价格信息：");
                List<Integer> prices = configManager.getKitPermanentPrices(kitType);
                if (level <= prices.size()) {
                    int price = prices.get(level - 1);
                    lore.add("  §a" + price + " 点券 §8(永久)");
                    // 只有支持月付的礼包才显示硬币价格
                    if (plugin.getKitManager().supportsMonthlyPurchase(kitType, level)) {
                    lore.add("  §e" + (price * 100) + " 硬币 §8(月度订阅)");
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
        
        // 返回按钮
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§e返回礼包菜单");
        backMeta.setLore(Arrays.asList("§7点击返回 " + configManager.getKitDisplayName(kitType) + " 菜单"));
        backItem.setItemMeta(backMeta);
        inventory.setItem(49, backItem);
    }
    
    /**
     * 显示礼包预览物品
     */
    private void displayKitPreview(Inventory inventory, String kitType, int level) {
        // 预览位置（左侧）
        int[] previewSlots = {10, 19, 28, 37, 46}; // 剑、头盔、胸甲、护腿、靴子
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};
        
        // 获取礼包奖励物品列表
        List<ItemStack> rewardItems = plugin.getItemLoader().getKitRewardItems(kitType, level);
        
        if (!rewardItems.isEmpty() && rewardItems.size() >= 5) {
            // 使用配置的物品作为预览
            for (int i = 0; i < Math.min(5, rewardItems.size()); i++) {
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
        if (kitType.equals("mining")) {
            displayMiningKitInfo(player, level);
            return;
        }
        
        // 银河礼包特殊处理
        if (kitType.equals("galaxy")) {
            displayGalaxyNotSupportedMessage(player, level);
            return;
        }
        
        String kitName = configManager.getKitDisplayName(kitType);
        String title = "§8装备展示 §7- " + kitName + " §e等级" + level;
        
        Inventory inventory = Bukkit.createInventory(null, 54, title);
        
        // 填充背景
        fillItemDisplayBackground(inventory);
        
        // 异步加载物品信息
        kitManager.getPlayerKitDataAsync(player).thenAccept(playerData -> {
            // 在主线程中更新界面
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                loadItemsFromConfig(inventory, player, kitType, level);
                // 如果玩家仍在线且界面有效，则更新
                if (player.isOnline() && player.getOpenInventory() != null) {
                    player.openInventory(inventory);
                }
            });
        });
        
        // 立即打开空白界面，避免等待
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
     * 显示银河礼包不支持展示的提示信息
     */
    private void displayGalaxyNotSupportedMessage(Player player, int level) {
        String kitName = configManager.getKitDisplayName("galaxy");
        player.sendMessage("§6★ " + kitName + " §6(｡◕‿◕｡)");
        player.sendMessage("§7至尊级礼包");
        player.sendMessage("§7装备展示暂未开放");
        player.sendMessage("§8如需了解详情请联系管理员~");
        player.sendMessage("§8QQ：2076049239 §f♡");
    }
    
    /**
     * 从配置文件加载物品
     */
    private void loadItemsFromConfig(Inventory inventory, Player player, String kitType, int level) {
        // 装备展示位置（中心区域）
        int[] equipSlots = {20, 11, 12, 21, 30}; // 剑、头盔、胸甲、护腿、靴子
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};
        
        // 获取礼包奖励物品列表
        List<ItemStack> rewardItems = plugin.getItemLoader().getKitRewardItems(kitType, level);
        
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
            // 优先使用EZItem API，否则使用ItemLoader
            if (plugin.getEZItemIntegration() != null && plugin.getEZItemIntegration().isEZItemEnabled()) {
                ItemStack ezItem = plugin.getEZItemIntegration().getItemByTemplate(itemKey);
                if (ezItem != null) {
                    return ezItem;
                }
            }
            
            // 如果EZItem获取失败，使用ItemLoader加载配置中定义的物品
            return plugin.getItemLoader().getItemFromConfig(itemKey);
        } catch (Exception e) {
            plugin.getLogger().warning("无法加载物品: " + itemKey);
            return null;
        }
    }
    
    /**
     * 创建默认显示物品
     */
    private ItemStack createDefaultDisplayItem(String kitType, int level, String equipType) {
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
                lore.add("§d银河系最强装备");
                lore.add("§6至尊级别保护");
                break;
        }
        
        lore.add("");
        lore.add("§7购买礼包后可获得");
        
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        
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