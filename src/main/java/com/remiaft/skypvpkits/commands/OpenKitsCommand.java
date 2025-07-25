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
 * 打开礼包菜单命令
 * /openkits <玩家> [礼包类型]
 * 
 * @author AngelHell
 */
public class OpenKitsCommand implements CommandExecutor, TabCompleter {
    
    private final SkyPVPKits plugin;
    
    public OpenKitsCommand(SkyPVPKits plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skypvpkits.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("no_permission"));
            return true;
        }
        
        if (args.length < 1) {
            sender.sendMessage("§c用法: /openkits <玩家> [礼包类型]");
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player_not_found"));
            return true;
        }
        
        if (args.length == 1) {
            // 打开主菜单
            plugin.getMenuManager().openMainMenu(targetPlayer);
            sender.sendMessage("§a已为玩家 " + targetPlayer.getName() + " 打开礼包主菜单");
        } else {
            // 打开指定礼包菜单
            String kitType = parseKitType(args[1]);
            if (kitType == null) {
                sender.sendMessage(plugin.getConfigManager().getMessage("kit_not_found"));
                return true;
            }
            
            plugin.getMenuManager().openKitDetailMenu(targetPlayer, kitType);
            sender.sendMessage("§a已为玩家 " + targetPlayer.getName() + " 打开 " + 
                             plugin.getConfigManager().getKitDisplayName(kitType) + " 菜单");
        }
        
        return true;
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
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 玩家名称
            Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
        } else if (args.length == 2) {
            // 礼包类型
            completions.addAll(Arrays.asList("天空", "火星", "地球", "月球", "太阳", "挖矿", "银河"));
        }
        
        // 过滤匹配的补全
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
} 