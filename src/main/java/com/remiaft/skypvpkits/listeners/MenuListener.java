package com.remiaft.skypvpkits.listeners;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.managers.KitManager;
import com.remiaft.skypvpkits.managers.MenuManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 菜单监听器
 * 处理所有GUI界面的点击事件
 * 
 * 点击逻辑说明：
 * 
 * 【主菜单】
 * - 左键：进入礼包详情菜单
 * - 右键/中键：显示礼包信息（聊天框）
 * - 关闭按钮：关闭菜单
 * 
 * 【礼包详情菜单】
 * - 绿宝石块（已拥有）：
 *   * 任意点击 → 领取今日奖励
 * - 红石块（未拥有）：
 *   * 左键 → 点券永久购买并领取
 *   * Shift+左键 → 硬币月付购买（仅限1-2级，太阳/银河/挖矿不支持）
 *   * 右键/中键 → 查看装备详情
 * - 其他物品（包含"等级"）：
 *   * 左键/中键 → 点券永久购买
 *   * Shift+左键 → 硬币月付购买（仅限1-2级，太阳/银河/挖矿不支持）
 *   * 右键 → 查看装备详情
 * - 返回按钮：返回对应菜单
 * 
 * 【物品展示菜单】
 * - 购买按钮：左键/中键 → 点券永久购买
 * - 领取按钮：任意点击 → 领取奖励
 * - 装备物品：右键 → 显示详细信息
 * - 返回按钮：返回礼包详情菜单
 * - 关闭按钮：关闭菜单
 * 
 * @author AngelHell
 */
public class MenuListener implements Listener {
    
    private final SkyPVPKits plugin;
    private final MenuManager menuManager;
    private final KitManager kitManager;
    
    public MenuListener(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.menuManager = plugin.getMenuManager();
        this.kitManager = plugin.getKitManager();
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }
        
        String title = event.getInventory().getTitle();
        MenuManager.MenuType menuType = menuManager.getMenuType(title);
        
        if (menuType == MenuManager.MenuType.UNKNOWN) {
            return;
        }
        
        // 取消默认点击行为
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        
        // 检测各种点击状态
        boolean isShiftClick = event.isShiftClick();
        boolean isLeftClick = event.isLeftClick();
        boolean isRightClick = event.isRightClick();
        boolean isMiddleClick = event.getClick().name().contains("MIDDLE");
        
        switch (menuType) {
            case MAIN_MENU:
                handleMainMenuClick(player, clickedItem, isLeftClick, isRightClick, isMiddleClick);
                break;
            case KIT_DETAIL:
                handleKitDetailClick(player, title, clickedItem, isLeftClick, isRightClick, isShiftClick, isMiddleClick);
                break;
            case ITEM_DISPLAY:
                handleItemDisplayClick(player, title, clickedItem, isLeftClick, isRightClick, isMiddleClick);
                break;
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        String title = event.getInventory().getTitle();
        if (menuManager.getMenuType(title) != MenuManager.MenuType.UNKNOWN) {
            menuManager.onMenuOpen(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        String title = event.getInventory().getTitle();
        if (menuManager.getMenuType(title) != MenuManager.MenuType.UNKNOWN) {
            menuManager.onMenuClose(player);
        }
    }
    
    /**
     * 处理主菜单点击
     */
    private void handleMainMenuClick(Player player, ItemStack clickedItem, boolean isLeftClick, boolean isRightClick, boolean isMiddleClick) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 关闭菜单按钮
        if (displayName.contains("关闭")) {
            player.closeInventory();
            return;
        }
        
        // 检查是否为礼包图标
        String kitType = getKitTypeFromDisplayName(displayName);
        if (kitType != null) {
            if (isLeftClick) {
                // 左键查看详情
                menuManager.openKitDetailMenu(player, kitType);
            } else if (isRightClick) {
                // 右键查看礼包整体信息（发送消息）
                showKitInfo(player, kitType);
            } else if (isMiddleClick) {
                // 中键查看礼包整体信息（发送消息）
                showKitInfo(player, kitType);
            }
        }
    }
    
    /**
     * 处理礼包详情菜单点击
     */
    private void handleKitDetailClick(Player player, String title, ItemStack clickedItem, boolean isLeftClick, boolean isRightClick, boolean isShiftClick, boolean isMiddleClick) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 返回按钮
        if (displayName.contains("返回主菜单")) {
            menuManager.openMainMenu(player);
            return;
        } else if (displayName.contains("返回")) {
            String kitType = menuManager.extractKitTypeFromTitle(title);
            if (kitType != null) {
                menuManager.openKitDetailMenu(player, kitType);
            }
            return;
        }
        
        // 解析等级信息
        String kitType = menuManager.extractKitTypeFromTitle(title);
        if (kitType == null) {
            return;
        }
        
        if (displayName.equals("§6国庆免费领取")) {
            kitManager.claimNationalDayReward(player, kitType).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (error != null) player.sendMessage("§c活动奖励暂不可用，请稍后重试");
                    else player.sendMessage((result.isSuccess() ? "§a" : "§c") + result.getMessage());
                    refreshMenu(player, kitType);
                }));
            return;
        }

        int level = extractLevelFromDisplayName(displayName);
        if (level <= 0) {
            return;
        }
        
        // 检查是否为等级图标 - 支持多种材料类型
        Material itemType = clickedItem.getType();
        boolean isPermanentOwnedKit = itemType == Material.EMERALD_BLOCK;
        boolean isTemporaryOwnedKit = itemType == Material.EMERALD;
        boolean isPromotionKit = itemType == Material.GOLD_BLOCK;
        boolean isBlockedByHigherLevel = itemType == Material.BARRIER;
        boolean isLockedByPreviousPermanent = itemType == Material.COAL_BLOCK;
        boolean isUnownedKit = (itemType == Material.REDSTONE_BLOCK || itemType == Material.REDSTONE);
        
        if (isPermanentOwnedKit) {
            // 已拥有的等级 - 领取奖励
            claimKitReward(player, kitType, level);
        } else if (isTemporaryOwnedKit) {
            if (isShiftClick && isLeftClick) {
                purchaseKitPermanent(player, kitType, level);
            } else if (isRightClick || isMiddleClick) {
                menuManager.openItemDisplayMenu(player, kitType, level);
            } else {
                claimKitReward(player, kitType, level);
            }
        } else if (isPromotionKit) {
            if (isShiftClick && isLeftClick) {
                // 活动权益与充值完全分离，玩家仍可正常购买永久版。
                purchaseKitPermanent(player, kitType, level);
            } else if (isShiftClick && isRightClick) {
                // 月付入口同样保留，活动不会占用或延长真实订阅。
                purchaseKitMonthlyWithCoins(player, kitType, level);
            } else if (isRightClick || isMiddleClick) {
                menuManager.openItemDisplayMenu(player, kitType, level);
            } else {
                claimKitReward(player, kitType, level);
            }
        } else if (isBlockedByHigherLevel) {
            if (isRightClick || isMiddleClick) {
                menuManager.openItemDisplayMenu(player, kitType, level);
                return;
            }
            player.sendMessage(plugin.getConfigManager().getMessage("already_owned"));
        } else if (isLockedByPreviousPermanent) {
            if (isRightClick || isMiddleClick) {
                menuManager.openItemDisplayMenu(player, kitType, level);
                return;
            }
            player.sendMessage("§c需要先永久拥有等级 " + (level - 1) + " 才能购买更高等级礼包");
        } else if (isUnownedKit) {
            // 未拥有的等级 - 根据点击类型决定购买方式
            if (isShiftClick && isLeftClick) {
                // Shift + 左键：硬币月付购买
                purchaseKitMonthlyWithCoins(player, kitType, level);
            } else if (isLeftClick) {
                // 左键：点券永久购买
                purchaseAndClaimKit(player, kitType, level);
            } else if (isRightClick) {
                // 右键：打开物品展示菜单
                menuManager.openItemDisplayMenu(player, kitType, level);
            } else if (isMiddleClick) {
                // 中键：打开物品展示菜单
                menuManager.openItemDisplayMenu(player, kitType, level);
            }
        } else {
            // 其他类型的物品，检查是否包含等级信息
            if (displayName.contains("等级") && (isLeftClick || isRightClick || isShiftClick || isMiddleClick)) {
                // 可能是装备预览或其他功能按钮
                if (isRightClick) {
                    // 右键查看装备详情
                    menuManager.openItemDisplayMenu(player, kitType, level);
                } else if (isShiftClick && isLeftClick) {
                    // Shift + 左键尝试硬币月付购买
                    purchaseKitMonthlyWithCoins(player, kitType, level);
                } else if (isLeftClick) {
                    // 左键尝试点券永久购买
                    purchaseAndClaimKit(player, kitType, level);
                } else if (isMiddleClick) {
                    // 中键尝试点券永久购买
                    purchaseAndClaimKit(player, kitType, level);
                }
            }
        }
    }
    
    /**
     * 处理物品展示菜单点击
     */
    private void handleItemDisplayClick(Player player, String title, ItemStack clickedItem, boolean isLeftClick, boolean isRightClick, boolean isMiddleClick) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return;
        }
        
        String displayName = meta.getDisplayName();
        
        // 返回按钮
        if (displayName.contains("返回")) {
            // 从标题中提取礼包类型和等级，返回礼包详情菜单
            String kitType = extractKitTypeFromTitle(title);
            if (kitType != null) {
                menuManager.openKitDetailMenu(player, kitType);
            }
            return;
        }
        
        // 关闭按钮
        if (displayName.contains("关闭")) {
            player.closeInventory();
            return;
        }
        
        // 检查是否是功能按钮或装备项
        if (displayName.contains("购买") || displayName.contains("领取")) {
            // 从标题中提取礼包信息
            String kitType = extractKitTypeFromTitle(title);
            if (kitType != null) {
                int level = extractLevelFromTitle(title);
                if (level > 0) {
                    if (displayName.contains("购买")) {
                        // 购买功能按钮
                        if (isLeftClick || isMiddleClick) {
                            // 左键/中键：点券永久购买
                            purchaseAndClaimKit(player, kitType, level);
                        }
                    } else if (displayName.contains("领取")) {
                        // 领取功能按钮
                        if (isLeftClick || isRightClick || isMiddleClick) {
                            claimKitReward(player, kitType, level);
                        }
                    }
                }
            }
            return;
        }
        
        // 装备预览物品（右键可以获取更多信息）
        if (isRightClick && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            List<String> lore = clickedItem.getItemMeta().getLore();
            if (lore != null && !lore.isEmpty()) {
                // 发送装备详细信息给玩家
                player.sendMessage("§f" + displayName);
                for (String line : lore) {
                    player.sendMessage(line);
                }
            }
        }
        
        // 其他点击不做处理，只是展示
    }
    
    /**
     * 从标题中提取礼包类型
     */
    private String extractKitTypeFromTitle(String title) {
        for (String kitType : KitManager.KIT_TYPES) {
            String kitName = plugin.getConfigManager().getKitDisplayName(kitType).replaceAll("§.", "");
            String cleanTitle = title.replaceAll("§.", "");
            if (cleanTitle.contains(kitName)) {
                return kitType;
            }
        }
        return null;
    }
    
    /**
     * 显示礼包整体信息
     */
    private void showKitInfo(Player player, String kitType) {
        String kitName = plugin.getConfigManager().getKitDisplayName(kitType);
        player.sendMessage("§6★ " + kitName + " §f(๑•̀ㅂ•́)و");
        if ("zxgy".equals(kitType)) {
            player.sendMessage("§7装备配置：战斧·战弓·头盔·胸甲·护腿·靴子");
            player.sendMessage("§7核心效果：§d星辰的惩罚 §7与专属弓箭特效");
            return;
        }
        if ("ywxc".equals(kitType)) {
            player.sendMessage("§7装备配置：观星剑·巡天弓·头盔·胸甲·护腿·靴子");
            player.sendMessage("§7核心效果：§b三次命中压生命恢复 §7与高防星辉减伤");
            return;
        }
        player.sendMessage("§7装备配置：剑·头盔·胸甲·护腿·靴子");
    }
    
    /**
     * 显示指定等级的详细信息
     */
    private void showKitLevelInfo(Player player, String kitType, int level) {
        String kitName = plugin.getConfigManager().getKitDisplayName(kitType);
        int permanentPrice = getPermanentPrice(kitType, level);
        
        player.sendMessage("§6★ " + kitName + " §eLv." + level + " §f✧");
        player.sendMessage("§7永久购买：§a" + permanentPrice + " 点券 §f( ´▽｀)");
        
        // 只有支持月付的礼包才显示硬币价格
        if (kitManager.supportsMonthlyPurchase(kitType, level)) {
            int coinsPrice = permanentPrice * 100;
            player.sendMessage("§7月度订阅：§e" + coinsPrice + " 硬币 §f(•ᴗ•)");
        }
    }
    
    /**
     * 获取永久购买价格（从KitManager复制的方法）
     */
    private int getPermanentPrice(String kitType, int level) {
        try {
            java.util.List<Integer> prices = plugin.getConfigManager().getKitPermanentPrices(kitType);
            if (level <= 0 || level > prices.size()) {
                return 0;
            }
            
            int basePrice = prices.get(level - 1);
            double discount = plugin.getConfigManager().getKitLevelDiscount(kitType, level);
            
            return (int) Math.ceil(basePrice * discount);
        } catch (Exception e) {
            plugin.getLogger().warning("获取价格失败: " + e.getMessage());
            return 100; // 默认价格
        }
    }
    
    /**
     * 点券购买礼包（永久）
     */
    private void purchaseAndClaimKit(Player player, String kitType, int level) {
        kitManager.purchaseKitPermanent(player, kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage("§a✦ 购买成功！ §f" + 
                            plugin.getConfigManager().getKitDisplayName(kitType) + 
                            " Lv." + level + " §a(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧");
                        
                        // 购买成功后自动领取奖励
                        kitManager.claimKitReward(player, kitType, level)
                                .thenAccept(claimResult -> {
                                    if (claimResult.isSuccess()) {
                                        player.sendMessage("§a★ 自动领取成功！ §f(｡◕‿◕｡)");
                                    } else {
                                        handleClaimError(player, claimResult);
                                    }
                                });
                        
                        // 刷新菜单
                        refreshMenu(player, kitType);
                    } else {
                        handlePurchaseError(player, result);
                    }
                });
    }

    private void purchaseKitPermanent(Player player, String kitType, int level) {
        kitManager.purchaseKitPermanent(player, kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage("§a✦ 已购买永久版！ §f" +
                                plugin.getConfigManager().getKitDisplayName(kitType) +
                                " Lv." + level + " §a(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧");
                        refreshMenu(player, kitType);
                    } else {
                        handlePurchaseError(player, result);
                    }
                });
    }
    
    /**
     * 硬币月付购买礼包
     */
    private void purchaseKitMonthlyWithCoins(Player player, String kitType, int level) {
        kitManager.purchaseKitMonthlyWithCoins(player, kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage("§a✦ 订阅成功！ §f" + 
                            plugin.getConfigManager().getKitDisplayName(kitType) + 
                            " Lv." + level + " §7月度版 §a(´∀｀)♡");
                        
                        // 刷新菜单
                        refreshMenu(player, kitType);
                    } else {
                        handlePurchaseError(player, result);
                    }
                });
    }
    
    /**
     * 领取礼包奖励
     */
    private void claimKitReward(Player player, String kitType, int level) {
        kitManager.claimKitReward(player, kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("claim_success"));
                        
                        // 刷新菜单
                        refreshMenu(player, kitType);
                    } else {
                        handleClaimError(player, result);
                    }
                });
    }
    
    /**
     * 刷新菜单
     */
    private void refreshMenu(Player player, String kitType) {
        // 延迟一点时间再刷新，让玩家看到消息
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            menuManager.openKitDetailMenu(player, kitType);
        }, 10L); // 0.5秒后
    }
    
    /**
     * 从显示名称获取礼包类型
     */
    private String getKitTypeFromDisplayName(String displayName) {
        for (String kitType : KitManager.KIT_TYPES) {
            String kitDisplayName = plugin.getConfigManager().getKitDisplayName(kitType);
            if (displayName.contains(kitDisplayName.replaceAll("§.", ""))) {
                return kitType;
            }
        }
        return null;
    }
    
    /**
     * 从显示名称提取等级
     */
    private int extractLevelFromDisplayName(String displayName) {
        // 清理颜色代码
        String cleanName = displayName.replaceAll("§.", "");
        
        // 尝试多种格式的等级提取
        
        // 格式1：等级 X
        if (cleanName.contains("等级")) {
            String[] parts = cleanName.split("等级");
            if (parts.length > 1) {
                String levelStr = parts[1].trim().replaceAll("[^0-9]", "");
                if (!levelStr.isEmpty()) {
                    try {
                        return Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        // 继续尝试其他格式
                    }
                }
            }
        }
        
        // 格式2：Lv X 或 LV X
        if (cleanName.toLowerCase().contains("lv")) {
            String[] parts = cleanName.toLowerCase().split("lv");
            if (parts.length > 1) {
                String levelStr = parts[1].trim().replaceAll("[^0-9]", "");
                if (!levelStr.isEmpty()) {
                    try {
                        return Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        // 继续尝试其他格式
                    }
                }
            }
        }
        
        // 格式3：Level X
        if (cleanName.toLowerCase().contains("level")) {
            String[] parts = cleanName.toLowerCase().split("level");
            if (parts.length > 1) {
                String levelStr = parts[1].trim().replaceAll("[^0-9]", "");
                if (!levelStr.isEmpty()) {
                    try {
                        return Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        // 继续尝试其他格式
                    }
                }
            }
        }
        
        // 格式4：直接查找数字（作为最后的尝试）
        String[] words = cleanName.split("\\s+");
        for (String word : words) {
            if (word.matches("\\d+")) {
                try {
                    int level = Integer.parseInt(word);
                    if (level >= 1 && level <= 10) { // 合理的等级范围
                        return level;
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
        
        return 0; // 无法解析时返回0
    }
    
    /**
     * 从标题中提取等级信息
     */
    private int extractLevelFromTitle(String title) {
        String cleanTitle = title.replaceAll("§.", "");
        
        // 查找"等级X"格式
        if (cleanTitle.contains("等级")) {
            String[] parts = cleanTitle.split("等级");
            if (parts.length > 1) {
                String levelStr = parts[1].trim().replaceAll("[^0-9]", "");
                if (!levelStr.isEmpty()) {
                try {
                    return Integer.parseInt(levelStr);
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
        }
        
        // 查找数字
        String[] words = cleanTitle.split("\\s+");
        for (String word : words) {
            if (word.matches("\\d+")) {
                try {
                    int level = Integer.parseInt(word);
                    if (level >= 1 && level <= 10) {
                        return level;
                    }
                } catch (NumberFormatException e) {
                    // 忽略
                }
            }
        }
        
        return 1; // 默认等级1
    }
    
    /**
     * 处理购买错误
     */
    private void handlePurchaseError(Player player, KitManager.PurchaseResult result) {
        switch (result.getErrorCode()) {
            case "already_owned":
            case "higher_level_owned":
                player.sendMessage(plugin.getConfigManager().getMessage("already_owned"));
                break;
            case "need_previous_permanent_level":
                player.sendMessage("§c需要先永久拥有等级 " + result.getAmount() + " 才能购买更高等级礼包");
                break;
            case "need_previous_level":
            case "cannot_purchase":
                player.sendMessage(plugin.getConfigManager().getMessage(
                        "need_previous_level",
                        "level", String.valueOf(result.getAmount())
                ));
                break;
            case "insufficient_points":
                String pointsMsg = plugin.getConfigManager().getMessage("insufficient_points")
                    .replace("{amount}", String.valueOf(result.getAmount()));
                player.sendMessage(pointsMsg);
                break;
            case "insufficient_money":
                String moneyMsg = plugin.getConfigManager().getMessage("insufficient_money")
                    .replace("{amount}", String.valueOf(result.getAmount()));
                player.sendMessage(moneyMsg);
                break;
            case "not_supported":
                player.sendMessage("§c该礼包暂不支持点券购买，请联系: " + plugin.getConfigManager().getContactQQ());
                break;
            case "monthly_not_supported":
                player.sendMessage("§c该礼包不支持月付购买");
                break;
            case "database_error":
                player.sendMessage(plugin.getConfigManager().getMessage("database_error"));
                break;
            default:
                player.sendMessage("§c购买失败: " + result.getMessage());
                break;
        }
    }
    
    /**
     * 处理领取错误
     */
    private void handleClaimError(Player player, KitManager.ClaimResult result) {
        switch (result.getErrorCode()) {
            case "not_owned":
                player.sendMessage(plugin.getConfigManager().getMessage("not_owned"));
                break;
            case "already_claimed":
                player.sendMessage(plugin.getConfigManager().getMessage("already_claimed"));
                break;
            case "database_error":
                player.sendMessage(plugin.getConfigManager().getMessage("database_error"));
                break;
            default:
                player.sendMessage("§c领取失败: " + result.getMessage());
                break;
        }
    }
} 
