package com.remiaft.skypvpkits.commands;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.utils.EZItemIntegration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * EZItem集成测试命令
 * 用于测试和验证EZItem API的集成情况
 * 
 * @author AngelHell
 */
public class TestEZItemCommand implements CommandExecutor, TabCompleter {
    
    private final SkyPVPKits plugin;
    
    public TestEZItemCommand(SkyPVPKits plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skypvpkits.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }
        
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "status":
                handleStatusCommand(sender);
                break;
                
            case "get":
                handleGetCommand(sender, args);
                break;
                
            case "list":
                handleListCommand(sender);
                break;
                
            case "test":
                handleTestCommand(sender, args);
                break;
                
            default:
                sendHelpMessage(sender);
                break;
        }
        
        return true;
    }
    
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6§l=== EZItem 集成测试命令 ===");
        sender.sendMessage("§e/testezitem status §7- 检查EZItem集成状态");
        sender.sendMessage("§e/testezitem get <模板名> §7- 获取指定模板物品");
        sender.sendMessage("§e/testezitem list §7- 列出所有可用模板");
        sender.sendMessage("§e/testezitem test <礼包> <等级> §7- 测试礼包物品获取");
    }
    
    private void handleStatusCommand(CommandSender sender) {
        EZItemIntegration integration = plugin.getEZItemIntegration();
        
        sender.sendMessage("§6§l=== EZItem 集成状态 ===");
        sender.sendMessage("§e集成对象: " + (integration != null ? "§a已初始化" : "§c未初始化"));
        
        if (integration != null) {
            sender.sendMessage("§eEZItem状态: " + (integration.isEZItemEnabled() ? "§a已启用" : "§c未启用"));
            sender.sendMessage("§e可用模板数量: §f" + EZItemIntegration.getAllTemplateNames().size());
        }
        
        // 检查EZItem插件是否已加载
        boolean ezItemLoaded = plugin.getServer().getPluginManager().getPlugin("EZItem") != null;
        sender.sendMessage("§eEZItem插件: " + (ezItemLoaded ? "§a已加载" : "§c未加载"));
    }
    
    private void handleGetCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }
        
        if (args.length < 2) {
            sender.sendMessage("§c用法: /testezitem get <模板名>");
            return;
        }
        
        Player player = (Player) sender;
        String templateName = args[1];
        
        EZItemIntegration integration = plugin.getEZItemIntegration();
        if (integration == null || !integration.isEZItemEnabled()) {
            player.sendMessage("§cEZItem集成未启用！");
            return;
        }
        
        ItemStack item = integration.getItemByTemplate(templateName);
        if (item != null) {
            player.getInventory().addItem(item);
            player.sendMessage("§a成功获取物品: §f" + templateName);
        } else {
            player.sendMessage("§c无法获取物品: §f" + templateName);
            player.sendMessage("§7请检查模板是否存在或EZItem是否正常工作");
        }
    }
    
    private void handleListCommand(CommandSender sender) {
        sender.sendMessage("§6§l=== 可用模板列表 ===");
        
        List<String> templates = EZItemIntegration.getAllTemplateNames();
        int count = 0;
        StringBuilder sb = new StringBuilder();
        
        for (String template : templates) {
            if (count > 0 && count % 5 == 0) {
                sender.sendMessage(sb.toString());
                sb = new StringBuilder();
            }
            
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("§e").append(template);
            count++;
        }
        
        if (sb.length() > 0) {
            sender.sendMessage(sb.toString());
        }
        
        sender.sendMessage("§7总计: §f" + templates.size() + " §7个模板");
    }
    
    private void handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§c用法: /testezitem test <礼包> <等级>");
            sender.sendMessage("§c例如: /testezitem test sky 1");
            return;
        }
        
        Player player = (Player) sender;
        String kitType = args[1];
        int level;
        
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c等级必须是数字！");
            return;
        }
        
        EZItemIntegration integration = plugin.getEZItemIntegration();
        if (integration == null || !integration.isEZItemEnabled()) {
            player.sendMessage("§cEZItem集成未启用！");
            return;
        }
        
        player.sendMessage("§6测试获取 " + kitType + " 等级 " + level + " 的装备...");
        
        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};
        int successCount = 0;
        
        for (String equipType : equipTypes) {
            ItemStack item = integration.getKitItem(kitType, level, equipType);
            if (item != null) {
                player.getInventory().addItem(item);
                player.sendMessage("§a✓ " + equipType + ": §f" + item.getType());
                successCount++;
            } else {
                player.sendMessage("§c✗ " + equipType + ": §7未找到");
            }
        }
        
        player.sendMessage("§6测试完成！成功获取 §e" + successCount + "§6/§e" + equipTypes.length + " §6个物品");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("status", "get", "list", "test"));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("get")) {
                // 模板名称补全（只显示前几个，避免太多）
                List<String> templates = EZItemIntegration.getAllTemplateNames();
                for (int i = 0; i < Math.min(10, templates.size()); i++) {
                    completions.add(templates.get(i));
                }
            } else if (subCommand.equals("test")) {
                completions.addAll(Arrays.asList("sky", "mars", "earth", "moon", "sun", "mining", "galaxy"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            String kitType = args[1];
            if (kitType.equals("galaxy")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4"));
            } else {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            }
        }
        
        // 过滤匹配项
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
} 