package com.remiaft.skypvpkits.commands;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.managers.KitManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 主命令处理器
 * 处理 /skypvpkits 命令的所有子命令
 * 
 * @author AngelHell
 */
public class KitsCommand implements CommandExecutor, TabCompleter {
    
    private final SkyPVPKits plugin;
    
    public KitsCommand(SkyPVPKits plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // 如果是玩家且没有参数，打开主菜单
            if (sender instanceof Player) {
                Player player = (Player) sender;
                plugin.getMenuManager().openMainMenu(player);
                return true;
            } else {
                sendHelpMessage(sender);
                return true;
            }
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
            case "帮助":
                sendHelpMessage(sender);
                break;
                
            case "reload":
            case "重载":
                handleReloadCommand(sender);
                break;
                
            case "menu":
            case "菜单":
                handleMenuCommand(sender, args);
                break;
                
            case "buy":
            case "购买":
                handleBuyCommand(sender, args);
                break;
                
            case "claim":
            case "领取":
                handleClaimCommand(sender, args);
                break;
                
            case "info":
            case "信息":
                handleInfoCommand(sender, args);
                break;
                
            case "give":
            case "给予":
                handleGiveCommand(sender, args);
                break;
                
            case "remove":
            case "delete":
            case "删除":
                handleRemoveCommand(sender, args);
                break;
                
            default:
                sender.sendMessage(plugin.getConfigManager().getMessage("unknown_command"));
                sendHelpMessage(sender);
                break;
        }
        
        return true;
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6✦ SkyPVPKits §f指令帮助 §6(｡◕‿◕｡)");
        sender.sendMessage("§7/skypvpkits §8- 打开礼包界面");
        sender.sendMessage("§7/skypvpkits menu §8- 打开礼包界面");
        sender.sendMessage("§7/skypvpkits buy <礼包> <等级> [永久|月付] §8- 购买礼包");
        sender.sendMessage("§7/skypvpkits claim <礼包> <等级> §8- 领取奖励");
        sender.sendMessage("§7/skypvpkits info [玩家] §8- 查看信息");
        sender.sendMessage("§7/skypvpkits help §8- 显示帮助");
        
        // 只对有管理员权限的玩家显示管理员命令
        if (sender.hasPermission("skypvpkits.admin") || sender.hasPermission("skypvpkits.reload")) {
            sender.sendMessage("");
            sender.sendMessage("§c★ 管理员指令：");
            if (sender.hasPermission("skypvpkits.reload")) {
                sender.sendMessage("§7/skypvpkits reload §8- 重载配置");
            }
            if (sender.hasPermission("skypvpkits.admin")) {
                sender.sendMessage("§7/skypvpkits give <玩家> <礼包> <等级> [永久|月付] §8- 给予礼包");
                sender.sendMessage("§7/skypvpkits remove <玩家> <礼包> <等级> §8- 删除礼包");
            }
        }
        
        sender.sendMessage("");
        sender.sendMessage("§8✧ v1.0.0 by AngelHell §8(´∀｀)♡");
    }
    
    /**
     * 处理重载命令
     */
    private void handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("skypvpkits.reload")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        try {
                                plugin.reloadConfig();
            sender.sendMessage(plugin.getConfigManager().getMessage("reload_success"));
        } catch (Exception e) {
            sender.sendMessage(plugin.getConfigManager().getMessage("reload_failed"));
            plugin.getLogger().warning("重载配置时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 处理菜单命令
     */
    private void handleMenuCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console_not_supported"));
            return;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("skypvpkits.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        if (args.length == 1) {
            // 打开主菜单
            plugin.getMenuManager().openMainMenu(player);
        } else if (args.length == 2) {
            // 打开指定礼包菜单
            String kitType = parseKitType(args[1]);
            if (kitType != null) {
                plugin.getMenuManager().openKitDetailMenu(player, kitType);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
            }
        } else {
            player.sendMessage("§c用法: /skypvpkits menu [礼包类型]");
        }
    }
    
    /**
     * 处理购买命令
     */
    private void handleBuyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console_not_supported"));
            return;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("skypvpkits.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        if (args.length < 3) {
            player.sendMessage("§c用法: /skypvpkits buy <礼包> <等级> [永久|月付]");
            return;
        }
        
        String kitType = parseKitType(args[1]);
        if (kitType == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c等级必须是数字！");
            return;
        }
        
        boolean isPermanent = true;
        if (args.length >= 4) {
            String type = args[3].toLowerCase();
            if (type.equals("月付") || type.equals("monthly")) {
                isPermanent = false;
            }
        }
        
        // 异步执行购买
        if (isPermanent) {
            plugin.getKitManager().purchaseKitPermanent(player, kitType, level)
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            player.sendMessage(plugin.getConfigManager().getMessage("purchase_success", 
                                "kit_name", plugin.getConfigManager().getKitDisplayName(kitType),
                                "level", String.valueOf(level)));
                        } else {
                            handlePurchaseError(player, result);
                        }
                    });
        } else {
                                plugin.getKitManager().purchaseKitMonthlyWithCoins(player, kitType, level)
                    .thenAccept(result -> {
                        if (result.isSuccess()) {
                            player.sendMessage(plugin.getConfigManager().getMessage("monthly_purchase_success", 
                                "kit_name", plugin.getConfigManager().getKitDisplayName(kitType),
                                "level", String.valueOf(level)));
                        } else {
                            handlePurchaseError(player, result);
                        }
                    });
        }
    }
    
    /**
     * 处理领取命令
     */
    private void handleClaimCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("console_not_supported"));
            return;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("skypvpkits.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        if (args.length < 3) {
            player.sendMessage("§c用法: /skypvpkits claim <礼包> <等级>");
            return;
        }
        
        String kitType = parseKitType(args[1]);
        if (kitType == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c等级必须是数字！");
            return;
        }
        
        // 异步执行领取
        plugin.getKitManager().claimKitReward(player, kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        player.sendMessage(plugin.getConfigManager().getMessage("claim_success"));
                    } else {
                        handleClaimError(player, result);
                    }
                });
    }
    
    /**
     * 处理信息命令
     */
    private void handleInfoCommand(CommandSender sender, String[] args) {
        Player targetPlayer;
        
        if (args.length >= 2) {
            if (!sender.hasPermission("skypvpkits.admin")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
                return;
            }
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                sender.sendMessage(plugin.getConfigManager().getMessage("player_not_found"));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getConfigManager().getMessage("console_not_supported"));
                return;
            }
            targetPlayer = (Player) sender;
        }
        
        // 异步获取玩家信息
        plugin.getKitManager().getPlayerKitDataAsync(targetPlayer)
                .thenAccept(dataList -> {
                    sender.sendMessage("§6★ " + targetPlayer.getName() + " §f的礼包信息 §6(｡◕‿◕｡)");
                    
                    if (dataList.isEmpty()) {
                        sender.sendMessage("§7还没有购买任何礼包呢~ §f(´・ω・`)");
                        return;
                    }
                    
                    // 按礼包类型分组显示
                    for (String kitType : KitManager.KIT_TYPES) {
                        List<Integer> levels = new ArrayList<>();
                        dataList.stream()
                                .filter(data -> data.getKitType().equals(kitType) && !data.isExpired())
                                .forEach(data -> levels.add(data.getKitLevel()));
                        
                        if (!levels.isEmpty()) {
                            String kitName = plugin.getConfigManager().getKitDisplayName(kitType);
                            sender.sendMessage("§7" + kitName + "：§f" + levels.toString() + " §a✧");
                        }
                    }
                });
    }
    
    /**
     * 处理给予命令（管理员）
     */
    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skypvpkits.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        if (args.length < 4) {
            sender.sendMessage("§c用法: /skypvpkits give <玩家> <礼包> <等级> [永久|月付]");
            return;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_not_found"));
            return;
        }
        
        final String kitType = parseKitType(args[2]);
        if (kitType == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
            return;
        }
        
        final int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c等级必须是数字！");
            return;
        }
        
        final boolean isPermanent;
        if (args.length >= 5) {
            String type = args[4].toLowerCase();
            isPermanent = !(type.equals("月付") || type.equals("monthly"));
        } else {
            isPermanent = true;
        }
        
        // 实现管理员给予礼包功能
        // 检查礼包等级是否有效
        int maxLevels = plugin.getConfigManager().getKitLevels(kitType);
        if (level <= 0 || level > maxLevels) {
            sender.sendMessage("§c无效的等级！该礼包最大等级为: " + maxLevels);
            return;
        }
        
        // 直接在数据库中添加礼包数据
        plugin.getKitManager().giveKitToPlayer(targetPlayer, kitType, level, isPermanent)
            .thenAccept(success -> {
                if (success) {
                    String duration = isPermanent ? "永久" : "月付";
                    String kitName = plugin.getConfigManager().getKitDisplayName(kitType);
                    
                    sender.sendMessage("§a★ 给予成功！ §f" + targetPlayer.getName() + 
                        " " + kitName + " Lv." + level + " (" + duration + ") §a(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧");
                    
                    // 通知目标玩家
                    if (targetPlayer.isOnline()) {
                        targetPlayer.sendMessage("§6✦ 管理员给你发放了礼包！ §f" + 
                            kitName + " Lv." + level + " (" + duration + ") §6(｡◕‿◕｡)");
                        targetPlayer.sendMessage("§7使用 §e/kits claim " + kitType + " " + level + " §7来领取奖励~ §f♡");
                    }
                    
                    // 记录日志
                    plugin.getLogger().info("[管理员操作] " + sender.getName() + 
                        " 给予 " + targetPlayer.getName() + " " + kitName + " Lv." + level + " (" + duration + ")");
                    
                } else {
                    sender.sendMessage("§c✖ 给予失败，请检查数据库连接 §f(╥﹏╥)");
                }
            })
            .exceptionally(throwable -> {
                sender.sendMessage("§c✖ 处理出错了：" + throwable.getMessage() + " §f(´･_･`)");
                plugin.getLogger().severe("管理员给予礼包时发生错误: " + throwable.getMessage());
                throwable.printStackTrace();
                return null;
            });
    }
    
    /**
     * 处理删除命令
     */
    private void handleRemoveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skypvpkits.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return;
        }
        
        if (args.length < 4) {
            sender.sendMessage("§c用法: /skypvpkits remove <玩家> <礼包> <等级>");
            sender.sendMessage("§c示例: /skypvpkits remove AngelHell 天空 1");
            return;
        }
        
        // 解析参数
        String targetPlayerName = args[1];
        String kitType = parseKitType(args[2]);
        
        if (kitType == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
            return;
        }
        
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c等级必须是数字！");
            return;
        }
        
        // 检查目标玩家（支持离线玩家）
        org.bukkit.OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
        if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
            sender.sendMessage("§c玩家 " + targetPlayerName + " 从未进入过服务器！");
            return;
        }
        
        // 检查等级是否有效
        int maxLevels = plugin.getConfigManager().getKitLevels(kitType);
        if (level <= 0 || level > maxLevels) {
            sender.sendMessage(plugin.getConfigManager().getMessage("level_not_found"));
            return;
        }
        
        String kitName = plugin.getConfigManager().getKitDisplayName(kitType);
        
        // 直接执行删除操作（使用UUID）
        plugin.getKitManager().removeKitLevelByUUID(targetPlayer.getUniqueId(), kitType, level)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        sender.sendMessage("§a成功删除 " + targetPlayer.getName() + " 的 " + 
                            kitName + " Lv" + level + "！");
                        
                        // 通知目标玩家（仅在线时）
                        if (targetPlayer.isOnline()) {
                            Player onlinePlayer = targetPlayer.getPlayer();
                            if (onlinePlayer != null) {
                                onlinePlayer.sendMessage("§c§l[系统] §f管理员删除了你的 " + 
                                    kitName + " Lv" + level);
                            }
                        }
                        
                        // 记录日志
                        plugin.getLogger().info("[管理员操作] " + sender.getName() + 
                            " 删除了 " + targetPlayer.getName() + " 的 " + kitName + " Lv" + level);
                        
                    } else {
                        sender.sendMessage("§c删除失败: " + result.getMessage());
                    }
                })
                .exceptionally(throwable -> {
                    sender.sendMessage("§c删除过程中发生错误: " + throwable.getMessage());
                    plugin.getLogger().severe("管理员删除礼包时发生错误: " + throwable.getMessage());
                    throwable.printStackTrace();
                    return null;
                });
    }
    
    /**
     * 解析礼包类型
     */
    private String parseKitType(String input) {
        String inputLower = input.toLowerCase();
        
        // 中文名称映射
        switch (inputLower) {
            case "天空":
            case "sky":
                return "sky";
            case "火星":
            case "mars":
                return "mars";
            case "地球":
            case "earth":
                return "earth";
            case "月球":
            case "moon":
                return "moon";
            case "太阳":
            case "sun":
                return "sun";
            case "挖矿":
            case "mining":
                return "mining";
            case "银河":
            case "galaxy":
                return "galaxy";
            default:
                return null;
        }
    }
    
    /**
     * 处理购买错误
     */
    private void handlePurchaseError(Player player, KitManager.PurchaseResult result) {
        switch (result.getErrorCode()) {
            case "cannot_purchase":
                player.sendMessage(plugin.getConfigManager().getMessage("need_previous_level"));
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
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 第一个参数：子命令
            // 基础命令
            completions.addAll(Arrays.asList("help", "帮助"));
            
            // 需要基础权限的命令
            if (sender.hasPermission("skypvpkits.use")) {
                completions.addAll(Arrays.asList("menu", "菜单", "buy", "购买", "claim", "领取"));
            }
            
            // info命令的权限检查
            if (sender.hasPermission("skypvpkits.use") || sender.hasPermission("skypvpkits.admin")) {
                completions.addAll(Arrays.asList("info", "信息"));
            }
            
            // 管理员命令
            if (sender.hasPermission("skypvpkits.reload")) {
                completions.addAll(Arrays.asList("reload", "重载"));
            }
            if (sender.hasPermission("skypvpkits.admin")) {
                completions.addAll(Arrays.asList("give", "给予", "remove", "delete", "删除"));
            }
            
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("menu") || subCommand.equals("菜单") || 
                 subCommand.equals("buy") || subCommand.equals("购买") || 
                 subCommand.equals("claim") || subCommand.equals("领取")) 
                 && sender.hasPermission("skypvpkits.use")) {
                // 礼包类型
                completions.addAll(Arrays.asList("天空", "火星", "地球", "月球", "太阳", "挖矿", "银河"));
            } else if ((subCommand.equals("info") || subCommand.equals("信息")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员查看其他玩家信息
                Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            } else if ((subCommand.equals("give") || subCommand.equals("给予")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员给予礼包 - 玩家名称
                Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            } else if ((subCommand.equals("remove") || subCommand.equals("delete") || subCommand.equals("删除")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员删除礼包 - 玩家名称
                Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
            }
            
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("buy") || subCommand.equals("购买") || 
                 subCommand.equals("claim") || subCommand.equals("领取")) 
                 && sender.hasPermission("skypvpkits.use")) {
                // 等级
                for (int i = 1; i <= 5; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ((subCommand.equals("give") || subCommand.equals("给予")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员给予礼包 - 礼包类型
                completions.addAll(Arrays.asList("天空", "火星", "地球", "月球", "太阳", "挖矿", "银河"));
            } else if ((subCommand.equals("remove") || subCommand.equals("delete") || subCommand.equals("删除")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员删除礼包 - 礼包类型
                completions.addAll(Arrays.asList("天空", "火星", "地球", "月球", "太阳", "挖矿", "银河"));
            }
            
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("buy") || subCommand.equals("购买")) 
                && sender.hasPermission("skypvpkits.use")) {
                // 购买类型
                completions.addAll(Arrays.asList("永久", "月付"));
            } else if ((subCommand.equals("give") || subCommand.equals("给予")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员给予礼包 - 等级
                for (int i = 1; i <= 5; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ((subCommand.equals("remove") || subCommand.equals("delete") || subCommand.equals("删除")) 
                       && sender.hasPermission("skypvpkits.admin")) {
                // 管理员删除礼包 - 等级
                for (int i = 1; i <= 5; i++) {
                    completions.add(String.valueOf(i));
                }
            }
            
        } else if (args.length == 5) {
            String subCommand = args[0].toLowerCase();
            if ((subCommand.equals("give") || subCommand.equals("给予")) 
                && sender.hasPermission("skypvpkits.admin")) {
                // 管理员给予礼包 - 类型
                completions.addAll(Arrays.asList("永久", "月付"));
            }
        }
        
        // 过滤匹配的补全
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
} 