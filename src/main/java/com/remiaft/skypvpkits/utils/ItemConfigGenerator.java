package com.remiaft.skypvpkits.utils;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemConfigGenerator {
    
    public static void main(String[] args) {
        System.out.println("正在生成基于 items.yml 的配置...");
        
        // 从items.yml文件中解析出的物品名称列表
        List<String> itemNames = Arrays.asList(
            "地球1_剑", "地球1_头", "地球1_甲", "地球1_腿", "地球1_鞋",
            "地球2_剑", "地球2_头", "地球2_甲", "地球2_腿", "地球2_鞋",
            "地球3_剑", "地球3_头", "地球3_甲", "地球3_腿", "地球3_鞋",
            "地球4_剑", "地球4_头", "地球4_甲", "地球4_腿", "地球4_鞋",
            "1天空头盔", "1天空胸甲", "1天空护腿", "1天空鞋子", "1天空剑", "普通金苹果",
            "2天空头盔", "2天空胸甲", "2天空护腿", "2天空鞋子", "2天空剑", "附魔金苹果",
            "3天空头盔", "3天空胸甲", "3天空护腿", "3天空鞋子", "3天空剑",
            "4天空头盔", "4天空胸甲", "4天空护腿", "4天空鞋子", "4天空剑",
            "5天空头盔", "5天空胸甲", "5天空护腿", "5天空鞋子", "5天空剑",
            "1月球moon剑", "1月球moon头盔", "1月球moon胸甲", "1月球moon护腿", "1月球moon鞋子",
            "2月球moon剑", "2月球moon头盔", "2月球moon胸甲", "2月球moon护腿", "2月球moon鞋子",
            "3月球moon剑", "3月球moon头盔", "3月球moon胸甲", "3月球moon护腿", "3月球moon鞋子",
            "4月球moon剑", "4月球moon头盔", "4月球moon胸甲", "4月球moon护腿", "4月球moon鞋子",
            "1火星spark剑", "1火星spark头盔", "1火星spark胸甲", "1火星spark护腿", "1火星spark鞋子",
            "2火星spark剑", "2火星spark头盔", "2火星spark胸甲", "2火星spark护腿", "2火星spark鞋子",
            "3火星spark剑", "3火星spark头盔", "3火星spark胸甲", "3火星spark护腿", "3火星spark鞋子",
                "4火星spark剑", "4火星spark头盔", "4火星spark胸甲", "4火星spark护腿", "4火星spark鞋子",
                "1太阳sun剑", "1太阳sun头盔", "1太阳sun胸甲", "1太阳sun护腿", "1太阳sun鞋子",
                "2太阳sun剑", "2太阳sun头盔", "2太阳sun胸甲", "2太阳sun护腿", "2太阳sun鞋子",
                "3太阳sun剑", "3太阳sun头盔", "3太阳sun胸甲", "3太阳sun护腿", "3太阳sun鞋子",
                "4太阳sun剑", "4太阳sun头盔", "4太阳sun胸甲", "4太阳sun护腿", "4太阳sun鞋子",
                "5太阳sun剑", "5太阳sun头盔", "5太阳sun胸甲", "5太阳sun护腿", "5太阳sun鞋子",
                "1挖矿mining镐", "1挖矿mining头盔", "1挖矿mining胸甲", "1挖矿mining护腿", "1挖矿mining鞋子",
                "2挖矿mining镐", "2挖矿mining头盔", "2挖矿mining胸甲", "2挖矿mining护腿", "2挖矿mining鞋子",
                "3挖矿mining镐", "3挖矿mining头盔", "3挖矿mining胸甲", "3挖矿mining护腿", "3挖矿mining鞋子",
                "4挖矿mining镐", "4挖矿mining头盔", "4挖矿mining胸甲", "4挖矿mining护腿", "4挖矿mining鞋子",
                "新银河1_剑", "新银河1_头盔", "新银河1_胸甲", "新银河1_裤", "新银河1_鞋",
                "新银河2_剑", "新银河2_头盔", "新银河2_胸甲", "新银河2_裤", "新银河2_鞋",
                "新银河3_剑", "新银河3_头盔", "新银河3_胸甲", "新银河3_裤", "新银河3_鞋",
                "新银河4_剑", "新银河4_头盔", "新银河4_胸甲", "新银河4_裤", "新银河4_鞋"
        );
        
        // 生成配置并写入文件
        generateItemsConfig(itemNames);
    }
    
    private static void generateItemsConfig(List<String> itemNames) {
        try {
            // 写入到config.yml文件的items部分
            File configFile = new File("src/main/resources/config.yml");
            
            StringBuilder itemsConfig = new StringBuilder();
            itemsConfig.append("\n# 物品配置 - 从 items.yml 解析\nitems:\n");
            
            for (String itemName : itemNames) {
                Map<String, Object> config = parseItemFromName(itemName);
                
                itemsConfig.append("  ").append(itemName).append(":\n");
                itemsConfig.append("    material: ").append(config.get("material")).append("\n");
                itemsConfig.append("    display-name: \"").append(config.get("display-name")).append("\"\n");
                itemsConfig.append("    amount: ").append(config.get("amount")).append("\n");
                
                if (config.containsKey("enchantments")) {
                    itemsConfig.append("    enchantments:\n");
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> enchants = (Map<String, Integer>) config.get("enchantments");
                    for (Map.Entry<String, Integer> enchant : enchants.entrySet()) {
                        itemsConfig.append("      ").append(enchant.getKey()).append(": ").append(enchant.getValue()).append("\n");
                    }
                }
                
                if (config.containsKey("color")) {
                    itemsConfig.append("    color: ").append(config.get("color")).append("\n");
                }
                
                itemsConfig.append("\n");
            }
            
            // 写入文件
            try (FileWriter writer = new FileWriter("generated_items_config.yml")) {
                writer.write(itemsConfig.toString());
            }
            
            System.out.println("配置已生成到 generated_items_config.yml");
            System.out.println("生成了 " + itemNames.size() + " 个物品配置");
            
            // 同时输出到控制台
            System.out.println("\n=== 生成的配置内容 ===");
            System.out.println(itemsConfig);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static Map<String, Object> parseItemFromName(String itemName) {
        Map<String, Object> config = new HashMap<>();
        
        // 根据物品名称推断属性
        String material = inferMaterial(itemName);
        String displayName = inferDisplayName(itemName);
        int level = inferLevel(itemName);
        
        config.put("material", material);
        config.put("display-name", displayName);
        config.put("amount", 1);
        
        // 根据等级和类型添加附魔
        Map<String, Integer> enchantments = new HashMap<>();
        
        if (itemName.contains("剑") || itemName.contains("sword")) {
            if (itemName.contains("新银河")) {
                // 新银河系列剑有更强的附魔
                enchantments.put("DAMAGE_ALL", Math.min(level + 6, 15));
                enchantments.put("DURABILITY", Math.min(level + 2, 5));
                enchantments.put("FIRE_ASPECT", Math.min(level + 1, 4));
                enchantments.put("KNOCKBACK", Math.min(level, 3));
                if (level >= 3) {
                    enchantments.put("LOOT_BONUS_MOBS", Math.min(level - 1, 3));
                }
            } else {
                enchantments.put("DAMAGE_ALL", Math.min(level + 4, 10));
                enchantments.put("DURABILITY", 3);
                if (level >= 2) {
                    enchantments.put("FIRE_ASPECT", Math.min(level - 1, 2));
                }
                if (level >= 3) {
                    enchantments.put("KNOCKBACK", 1);
                }
            }
        } else if (itemName.contains("头") || itemName.contains("helmet") || itemName.contains("头盔")) {
            if (itemName.contains("新银河")) {
                // 新银河系列头盔有更强的附魔
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 5, 12));
                enchantments.put("DURABILITY", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_FALL", Math.min(level + 1, 5));
                enchantments.put("OXYGEN", Math.min(level, 3));
            } else {
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 3, 8));
                enchantments.put("DURABILITY", Math.min(level + 1, 3));
                if (itemName.contains("月球") || itemName.contains("火星")) {
                    enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 1, 4));
                    enchantments.put("PROTECTION_FALL", Math.min(level, 4));
                }
            }
        } else if (itemName.contains("甲") || itemName.contains("chest") || itemName.contains("胸甲")) {
            if (itemName.contains("新银河")) {
                // 新银河系列胸甲有更强的附魔
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 5, 12));
                enchantments.put("DURABILITY", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_FALL", Math.min(level + 1, 5));
                enchantments.put("THORNS", Math.min(level, 3));
            } else {
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 3, 9));
                enchantments.put("DURABILITY", Math.min(level + 1, 3));
                if (itemName.contains("月球") || itemName.contains("火星")) {
                    enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 1, 4));
                    enchantments.put("PROTECTION_FALL", Math.min(level, 4));
                }
            }
        } else if (itemName.contains("腿") || itemName.contains("leg") || itemName.contains("裤")) {
            if (itemName.contains("新银河")) {
                // 新银河系列护腿有更强的附魔
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 5, 12));
                enchantments.put("DURABILITY", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_FALL", Math.min(level + 1, 5));
            } else {
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 3, 9));
                enchantments.put("DURABILITY", Math.min(level + 1, 3));
                if (itemName.contains("月球") || itemName.contains("火星")) {
                    enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 1, 4));
                    enchantments.put("PROTECTION_FALL", Math.min(level, 4));
                }
            }
        } else if (itemName.contains("鞋") || itemName.contains("boot")) {
            if (itemName.contains("新银河")) {
                // 新银河系列靴子有更强的附魔
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 5, 12));
                enchantments.put("DURABILITY", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 2, 5));
                enchantments.put("PROTECTION_FALL", Math.min(level + 1, 5));
                enchantments.put("DEPTH_STRIDER", Math.min(level, 3));
            } else {
                enchantments.put("PROTECTION_ENVIRONMENTAL", Math.min(level + 3, 8));
                enchantments.put("DURABILITY", Math.min(level + 1, 3));
                if (itemName.contains("月球") || itemName.contains("火星")) {
                    enchantments.put("PROTECTION_PROJECTILE", Math.min(level + 1, 4));
                    enchantments.put("PROTECTION_FALL", Math.min(level, 4));
                }
            }
        }
        
        if (itemName.contains("火星") || itemName.contains("spark")) {
            enchantments.put("PROTECTION_FIRE", Math.min(level, 4));
        }

        if (itemName.contains("新银河")) {
            // 新银河系列特殊效果
            enchantments.put("PROTECTION_FIRE", Math.min(level + 1, 5));
            enchantments.put("PROTECTION_EXPLOSIONS", Math.min(level, 4));
        }
        
        if (!enchantments.isEmpty()) {
            config.put("enchantments", enchantments);
        }
        
        // 皮革装备颜色
        if (itemName.contains("天空") && (material.contains("LEATHER") || 
            (itemName.contains("头") || itemName.contains("甲") || itemName.contains("腿") || itemName.contains("鞋")))) {
            config.put("color", "135,206,235"); // 天蓝色
        }
        
        return config;
    }
    
    private static String inferMaterial(String itemName) {
        if (itemName.contains("剑")) {
            if (itemName.contains("地球") || itemName.contains("新银河")) return "DIAMOND_SWORD";
            return "DIAMOND_SWORD";
        } else if (itemName.contains("头") || itemName.contains("头盔")) {
            if (itemName.contains("地球") || itemName.contains("新银河")) return "DIAMOND_HELMET";
            if (itemName.contains("天空")) return "LEATHER_HELMET";
            return "DIAMOND_HELMET";
        } else if (itemName.contains("甲") || itemName.contains("胸甲")) {
            if (itemName.contains("地球") || itemName.contains("新银河")) return "DIAMOND_CHESTPLATE";
            if (itemName.contains("天空")) return "LEATHER_CHESTPLATE";
            return "DIAMOND_CHESTPLATE";
        } else if (itemName.contains("腿") || itemName.contains("裤")) {
            if (itemName.contains("地球") || itemName.contains("新银河")) return "DIAMOND_LEGGINGS";
            if (itemName.contains("天空")) return "LEATHER_LEGGINGS";
            return "DIAMOND_LEGGINGS";
        } else if (itemName.contains("鞋")) {
            if (itemName.contains("地球") || itemName.contains("新银河")) return "DIAMOND_BOOTS";
            if (itemName.contains("天空")) return "LEATHER_BOOTS";
            return "DIAMOND_BOOTS";
        } else if (itemName.contains("金苹果")) {
            if (itemName.contains("附魔")) return "GOLDEN_APPLE";
            return "GOLDEN_APPLE";
        }
        return "DIAMOND_SWORD";
    }
    
    private static String inferDisplayName(String itemName) {
        if (itemName.contains("地球")) {
            String level = itemName.substring(itemName.indexOf("地球") + 2, itemName.indexOf("地球") + 3);
            if (itemName.contains("剑")) return "§b§l地球" + level + "§7剑";
            if (itemName.contains("头")) return "§b§l地球" + level + "§7头盔";
            if (itemName.contains("甲")) return "§b§l地球" + level + "§7胸甲";
            if (itemName.contains("腿")) return "§b§l地球" + level + "§7护腿";
            if (itemName.contains("鞋")) return "§b§l地球" + level + "§7靴子";
        } else if (itemName.contains("天空")) {
            String level = itemName.substring(0, 1);
            if (itemName.contains("剑")) return "§b§l天空" + level + "§7剑";
            if (itemName.contains("头")) return "§b§l天空" + level + "§7头盔";
            if (itemName.contains("甲")) return "§b§l天空" + level + "§7胸甲";
            if (itemName.contains("腿")) return "§b§l天空" + level + "§7护腿";
            if (itemName.contains("鞋")) return "§b§l天空" + level + "§7靴子";
        } else if (itemName.contains("月球") || itemName.contains("moon")) {
            String level = itemName.substring(0, 1);
            if (itemName.contains("剑")) return "§d§l月球" + level + "§7剑";
            if (itemName.contains("头")) return "§d§l月球" + level + "§7头盔";
            if (itemName.contains("甲")) return "§d§l月球" + level + "§7胸甲";
            if (itemName.contains("腿")) return "§d§l月球" + level + "§7护腿";
            if (itemName.contains("鞋")) return "§d§l月球" + level + "§7靴子";
        } else if (itemName.contains("火星") || itemName.contains("spark")) {
            String level = itemName.substring(0, 1);
            if (itemName.contains("剑")) return "§c§l火星" + level + "§7剑";
            if (itemName.contains("头")) return "§c§l火星" + level + "§7头盔";
            if (itemName.contains("甲")) return "§c§l火星" + level + "§7胸甲";
            if (itemName.contains("腿")) return "§c§l火星" + level + "§7护腿";
            if (itemName.contains("鞋")) return "§c§l火星" + level + "§7靴子";
        } else if (itemName.contains("新银河")) {
            String level = itemName.substring(itemName.indexOf("新银河") + 3, itemName.indexOf("新银河") + 4);
            if (itemName.contains("剑")) return "§d§l新银河" + level + "§7剑";
            if (itemName.contains("头盔")) return "§d§l新银河" + level + "§7头盔";
            if (itemName.contains("胸甲")) return "§d§l新银河" + level + "§7胸甲";
            if (itemName.contains("裤")) return "§d§l新银河" + level + "§7护腿";
            if (itemName.contains("鞋")) return "§d§l新银河" + level + "§7靴子";
        } else if (itemName.contains("金苹果")) {
            if (itemName.contains("附魔")) return "§6附魔金苹果";
            return "§6普通金苹果";
        }
        return "§f" + itemName;
    }
    
    private static int inferLevel(String itemName) {
        try {
            if (itemName.matches("^\\d.*")) {
                return Integer.parseInt(itemName.substring(0, 1));
            } else if (itemName.contains("地球")) {
                return Integer.parseInt(itemName.substring(itemName.indexOf("地球") + 2, itemName.indexOf("地球") + 3));
            } else if (itemName.contains("新银河")) {
                return Integer.parseInt(itemName.substring(itemName.indexOf("新银河") + 3, itemName.indexOf("新银河") + 4));
            }
        } catch (Exception e) {
            // ignore
        }
        return 1;
    }
} 