package com.remiaft.skypvpkits.commands;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.utils.ItemParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class InitializeItemsCommand implements CommandExecutor {

    private final SkyPVPKits plugin;

    public InitializeItemsCommand(SkyPVPKits plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skypvpkits.admin")) {
            sender.sendMessage("§c你没有权限执行此命令!");
            return true;
        }

        sender.sendMessage("§a开始解析items.yml文件...");

        try {
            // 读取items.yml文件
            File itemsFile = new File(plugin.getDataFolder(), "libs/items.yml");
            if (!itemsFile.exists()) {
                sender.sendMessage("§c找不到items.yml文件!");
                return true;
            }

            YamlConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
            
            // 解析物品数据
            Map<String, Object> parsedItems = ItemParser.parseItemsFromYml(itemsConfig);
            sender.sendMessage("§a成功解析了 " + parsedItems.size() + " 个物品");

            // 生成配置结构
            Map<String, Object> configStructure = ItemParser.generateConfigStructure(parsedItems);

            // 生成新的config.yml内容
            generateNewConfig(configStructure);
            
            sender.sendMessage("§a物品解析完成! 已更新config.yml文件");
            sender.sendMessage("§e请重新加载插件以应用新配置");

        } catch (Exception e) {
            sender.sendMessage("§c解析过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    private void generateNewConfig(Map<String, Object> configStructure) throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        StringBuilder configContent = new StringBuilder();
        
        // 添加基本配置
        configContent.append("# SkyPVPKits 配置文件\n");
        configContent.append("# 从 items.yml 自动生成\n\n");
        
        configContent.append("# 数据库配置\n");
        configContent.append("database:\n");
        configContent.append("  host: \"localhost\"\n");
        configContent.append("  port: 3306\n");
        configContent.append("  database: \"skypvpkits\"\n");
        configContent.append("  username: \"root\"\n");
        configContent.append("  password: \"\"\n");
        configContent.append("  table_prefix: \"skypvp_\"\n\n");
        
        configContent.append("# 菜单配置\n");
        configContent.append("menu:\n");
        configContent.append("  title: \"§b天空PVP礼包系统\"\n");
        configContent.append("  size: 54\n\n");
        
        configContent.append("# 礼包配置\n");
        configContent.append("kits:\n");
        configContent.append("  天空:\n");
        configContent.append("    name: \"§b天空礼包\"\n");
        configContent.append("    description: \"基础装备套装\"\n");
        configContent.append("    slot: 10\n");
        configContent.append("    material: \"LEATHER_HELMET\"\n");
        configContent.append("    levels: 5\n");
        configContent.append("  月球:\n");
        configContent.append("    name: \"§f月球礼包\"\n");
        configContent.append("    description: \"月球探索套装\"\n");
        configContent.append("    slot: 12\n");
        configContent.append("    material: \"CHAINMAIL_HELMET\"\n");
        configContent.append("    levels: 4\n");
        configContent.append("  火星:\n");
        configContent.append("    name: \"§c火星礼包\"\n");
        configContent.append("    description: \"火星战士套装\"\n");
        configContent.append("    slot: 14\n");
        configContent.append("    material: \"DIAMOND_HELMET\"\n");
        configContent.append("    levels: 4\n");
        configContent.append("  太阳:\n");
        configContent.append("    name: \"§e太阳礼包\"\n");
        configContent.append("    description: \"太阳神套装\"\n");
        configContent.append("    slot: 16\n");
        configContent.append("    material: \"GOLD_HELMET\"\n");
        configContent.append("    levels: 5\n");
        configContent.append("  挖矿:\n");
        configContent.append("    name: \"§7挖矿礼包\"\n");
        configContent.append("    description: \"专业采矿套装\"\n");
        configContent.append("    slot: 30\n");
        configContent.append("    material: \"DIAMOND_PICKAXE\"\n");
        configContent.append("    levels: 4\n");
        configContent.append("  银河:\n");
        configContent.append("    name: \"§d新银河礼包\"\n");
        configContent.append("    description: \"新银河系列装备\"\n");
        configContent.append("    slot: 32\n");
        configContent.append("    material: \"DIAMOND_SWORD\"\n");
        configContent.append("    levels: 4\n\n");
        
        // 添加解析的物品数据
        configContent.append("# 物品定义 (从 items.yml 解析)\n");
        configContent.append("items:\n");
        Map<String, Object> items = (Map<String, Object>) configStructure.get("items");
        appendMapToYaml(configContent, items, 1);
        
        configContent.append("\n# 礼包奖励 (从 items.yml 解析)\n");
        configContent.append("kit_rewards:\n");
        Map<String, Object> kitRewards = (Map<String, Object>) configStructure.get("kit_rewards");
        appendMapToYaml(configContent, kitRewards, 1);
        
        // 写入文件
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(configContent.toString());
        }
    }
    
    private void appendMapToYaml(StringBuilder sb, Map<String, Object> map, int indent) {
        String indentStr = "  ".repeat(indent);
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                sb.append(indentStr).append(key).append(":\n");
                appendMapToYaml(sb, (Map<String, Object>) value, indent + 1);
            } else if (value instanceof java.util.List) {
                sb.append(indentStr).append(key).append(":\n");
                java.util.List<?> list = (java.util.List<?>) value;
                for (Object item : list) {
                    if (item instanceof String) {
                        sb.append(indentStr).append("  - \"").append(escapeYamlString((String) item)).append("\"\n");
                    } else {
                        sb.append(indentStr).append("  - ").append(item).append("\n");
                    }
                }
            } else if (value instanceof String) {
                sb.append(indentStr).append(key).append(": \"").append(escapeYamlString((String) value)).append("\"\n");
            } else {
                sb.append(indentStr).append(key).append(": ").append(value).append("\n");
            }
        }
    }
    
    private String escapeYamlString(String str) {
        return str.replace("\"", "\\\"").replace("\n", "\\n");
    }
} 