package com.remiaft.skypvpkits.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemsYmlParser {
    
    public static void main(String[] args) {
        System.out.println("开始解析 items.yml 文件...");
        
        try {
            File itemsFile = new File("src/main/libs/items.yml");
            if (!itemsFile.exists()) {
                System.out.println("items.yml 文件不存在");
                return;
            }
            
            YamlConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
            
            // 获取 templates 节点
            if (!itemsConfig.contains("templates")) {
                System.out.println("items.yml 中没有找到 templates 节点");
                return;
            }
            
            Map<String, Object> items = new HashMap<>();
            Set<String> keys = itemsConfig.getConfigurationSection("templates").getKeys(false);
            System.out.println("找到 " + keys.size() + " 个物品模板");
            
            for (String key : keys) {
                try {
                    String encodedData = itemsConfig.getString("templates." + key + ".item");
                    if (encodedData != null) {
                        // 解码Base64数据
                        byte[] data = Base64.getDecoder().decode(encodedData);
                        
                        // 反序列化物品
                        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(data));
                        ItemStack itemStack = (ItemStack) dataInput.readObject();
                        dataInput.close();
                        
                        // 转换为配置格式
                        Map<String, Object> itemConfig = convertItemStackToConfig(itemStack);
                        items.put(key, itemConfig);
                        
                        System.out.println("成功解析物品: " + key + " - " + itemStack.getType());
                        
                    }
                } catch (Exception e) {
                    System.out.println("解析物品 " + key + " 时出错: " + e.getMessage());
                }
            }
            
            // 生成YAML格式输出
            System.out.println("\n=== 生成的配置 ===");
            System.out.println("items:");
            for (Map.Entry<String, Object> entry : items.entrySet()) {
                Map<String, Object> itemConfig = (Map<String, Object>) entry.getValue();
                
                System.out.println("  " + entry.getKey() + ":");
                System.out.println("    material: " + itemConfig.get("material"));
                System.out.println("    display-name: \"" + itemConfig.get("display-name") + "\"");
                if (itemConfig.containsKey("amount")) {
                    System.out.println("    amount: " + itemConfig.get("amount"));
                }
                if (itemConfig.containsKey("enchantments")) {
                    System.out.println("    enchantments:");
                    Map<String, Integer> enchants = (Map<String, Integer>) itemConfig.get("enchantments");
                    for (Map.Entry<String, Integer> enchant : enchants.entrySet()) {
                        System.out.println("      " + enchant.getKey() + ": " + enchant.getValue());
                    }
                }
                if (itemConfig.containsKey("color")) {
                    System.out.println("    color: " + itemConfig.get("color"));
                }
                System.out.println();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static Map<String, Object> convertItemStackToConfig(ItemStack itemStack) {
        Map<String, Object> config = new HashMap<>();
        
        // 基本属性
        config.put("material", itemStack.getType().name());
        config.put("amount", itemStack.getAmount());
        
        // 显示名称和描述
        if (itemStack.hasItemMeta()) {
            if (itemStack.getItemMeta().hasDisplayName()) {
                config.put("display-name", itemStack.getItemMeta().getDisplayName());
            }
            if (itemStack.getItemMeta().hasLore()) {
                config.put("lore", itemStack.getItemMeta().getLore());
            }
        }
        
        // 附魔
        if (!itemStack.getEnchantments().isEmpty()) {
            Map<String, Integer> enchantments = new HashMap<>();
            itemStack.getEnchantments().forEach((enchant, level) -> {
                enchantments.put(enchant.getName(), level);
            });
            config.put("enchantments", enchantments);
        }
        
        // 颜色（皮革装备）
        if (itemStack.hasItemMeta() && 
            itemStack.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta) {
            org.bukkit.inventory.meta.LeatherArmorMeta meta = 
                (org.bukkit.inventory.meta.LeatherArmorMeta) itemStack.getItemMeta();
            org.bukkit.Color color = meta.getColor();
            config.put("color", color.getRed() + "," + color.getGreen() + "," + color.getBlue());
        }
        
        return config;
    }
} 