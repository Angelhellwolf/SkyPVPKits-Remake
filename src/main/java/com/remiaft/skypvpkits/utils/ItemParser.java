package com.remiaft.skypvpkits.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class ItemParser {
    
    /**
     * 解析items.yml文件并生成配置文件格式的物品定义
     */
    public static Map<String, Object> parseItemsFromYml(YamlConfiguration itemsConfig) {
        Map<String, Object> items = new LinkedHashMap<>();
        
        Set<String> keys = itemsConfig.getKeys(false);
        for (String key : keys) {
            String encodedItem = itemsConfig.getString(key + ".item");
            if (encodedItem != null) {
                try {
                    ItemStack item = deserializeItem(encodedItem);
                    if (item != null) {
                        Map<String, Object> itemData = convertItemToConfig(item);
                        // 根据原名称推断礼包类型和等级
                        String[] categoryAndLevel = extractCategoryAndLevel(key);
                        itemData.put("category", categoryAndLevel[0]);
                        itemData.put("level", categoryAndLevel[1]);
                        
                        items.put(key, itemData);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse item: " + key);
                    e.printStackTrace();
                }
            }
        }
        
        return items;
    }
    
    /**
     * 反序列化Base64编码的物品数据
     */
    private static ItemStack deserializeItem(String encodedData) {
        try {
            byte[] serializedData = Base64.getDecoder().decode(encodedData);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(serializedData);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将ItemStack转换为配置文件格式
     */
    private static Map<String, Object> convertItemToConfig(ItemStack item) {
        Map<String, Object> config = new LinkedHashMap<>();
        
        // 基本信息
        config.put("material", item.getType().toString());
        config.put("amount", item.getAmount());
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 显示名称
            if (meta.hasDisplayName()) {
                config.put("display_name", meta.getDisplayName());
            }
            
            // 描述
            if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                config.put("lore", meta.getLore());
            }
            
            // 附魔
            if (meta.hasEnchants()) {
                Map<String, Integer> enchants = new LinkedHashMap<>();
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    enchants.put(getEnchantmentName(entry.getKey()), entry.getValue());
                }
                config.put("enchantments", enchants);
            }
            
            // 皮革装备颜色
            if (meta instanceof LeatherArmorMeta) {
                LeatherArmorMeta armorMeta = (LeatherArmorMeta) meta;
                Color color = armorMeta.getColor();
                if (!color.equals(Color.fromRGB(160, 101, 64))) { // 默认颜色
                    Map<String, Integer> colorMap = new LinkedHashMap<>();
                    colorMap.put("red", color.getRed());
                    colorMap.put("green", color.getGreen());
                    colorMap.put("blue", color.getBlue());
                    config.put("color", colorMap);
                }
            }
        }
        
        return config;
    }
    
    /**
     * 获取1.8兼容的附魔名称
     */
    private static String getEnchantmentName(Enchantment enchantment) {
        // 1.8版本的附魔名称映射
        Map<String, String> enchantmentMap = new HashMap<>();
        enchantmentMap.put("PROTECTION", "PROTECTION_ENVIRONMENTAL");
        enchantmentMap.put("FIRE_PROTECTION", "PROTECTION_FIRE");
        enchantmentMap.put("FEATHER_FALLING", "PROTECTION_FALL");
        enchantmentMap.put("BLAST_PROTECTION", "PROTECTION_EXPLOSIONS");
        enchantmentMap.put("PROJECTILE_PROTECTION", "PROTECTION_PROJECTILE");
        enchantmentMap.put("RESPIRATION", "OXYGEN");
        enchantmentMap.put("AQUA_AFFINITY", "WATER_WORKER");
        enchantmentMap.put("THORNS", "THORNS");
        enchantmentMap.put("DEPTH_STRIDER", "DEPTH_STRIDER");
        enchantmentMap.put("SHARPNESS", "DAMAGE_ALL");
        enchantmentMap.put("SMITE", "DAMAGE_UNDEAD");
        enchantmentMap.put("BANE_OF_ARTHROPODS", "DAMAGE_ARTHROPODS");
        enchantmentMap.put("KNOCKBACK", "KNOCKBACK");
        enchantmentMap.put("FIRE_ASPECT", "FIRE_ASPECT");
        enchantmentMap.put("LOOTING", "LOOT_BONUS_MOBS");
        enchantmentMap.put("EFFICIENCY", "DIG_SPEED");
        enchantmentMap.put("SILK_TOUCH", "SILK_TOUCH");
        enchantmentMap.put("UNBREAKING", "DURABILITY");
        enchantmentMap.put("FORTUNE", "LOOT_BONUS_BLOCKS");
        enchantmentMap.put("POWER", "ARROW_DAMAGE");
        enchantmentMap.put("PUNCH", "ARROW_KNOCKBACK");
        enchantmentMap.put("FLAME", "ARROW_FIRE");
        enchantmentMap.put("INFINITY", "ARROW_INFINITE");
        
        String modernName = enchantment.getName();
        return enchantmentMap.getOrDefault(modernName, modernName);
    }
    
    /**
     * 从物品名称中提取礼包类型和等级
     */
    private static String[] extractCategoryAndLevel(String itemName) {
        String category = "unknown";
        String level = "1";
        
        if (itemName.contains("天空") || itemName.contains("sky")) {
            category = "sky";
        } else if (itemName.contains("月球") || itemName.contains("moon")) {
            category = "moon";
        } else if (itemName.contains("火星") || itemName.contains("spark")) {
            category = "mars";
        } else if (itemName.contains("太阳") || itemName.contains("sun")) {
            category = "sun";
        } else if (itemName.contains("挖矿") || itemName.contains("mining")) {
            category = "mining";
        } else if (itemName.contains("银河")) {
            category = "galaxy";
        }
        
        // 提取等级数字
        for (int i = 1; i <= 5; i++) {
            if (itemName.contains(String.valueOf(i))) {
                level = String.valueOf(i);
                break;
            }
        }
        
        return new String[]{category, level};
    }
    
    /**
     * 生成配置文件格式的完整结构
     */
    public static Map<String, Object> generateConfigStructure(Map<String, Object> parsedItems) {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        Map<String, Object> kitRewards = new LinkedHashMap<>();
        
        // 按礼包类型和等级组织物品
        Map<String, Map<String, List<String>>> kitStructure = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : parsedItems.entrySet()) {
            String itemName = entry.getKey();
            Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
            
            String category = (String) itemData.get("category");
            String level = (String) itemData.get("level");
            
            // 移除category和level，这些是临时字段
            itemData.remove("category");
            itemData.remove("level");
            
            // 添加到items部分
            items.put(itemName, itemData);
            
            // 组织礼包结构
            kitStructure.computeIfAbsent(category, k -> new LinkedHashMap<>())
                      .computeIfAbsent(level, k -> new ArrayList<>())
                      .add(itemName);
        }
        
        // 生成kit_rewards结构
        for (Map.Entry<String, Map<String, List<String>>> categoryEntry : kitStructure.entrySet()) {
            String category = categoryEntry.getKey();
            Map<String, List<String>> levels = categoryEntry.getValue();
            
            Map<String, Object> categoryRewards = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> levelEntry : levels.entrySet()) {
                String level = levelEntry.getKey();
                List<String> itemList = levelEntry.getValue();
                
                Map<String, Object> levelData = new LinkedHashMap<>();
                levelData.put("items", itemList);
                levelData.put("special_rewards", Arrays.asList(
                    generateSpecialRewardDescription(category, level)
                ));
                
                categoryRewards.put(level, levelData);
            }
            
            kitRewards.put(category, categoryRewards);
        }
        
        config.put("items", items);
        config.put("kit_rewards", kitRewards);
        
        return config;
    }
    
    /**
     * 生成特殊奖励描述
     */
    private static String generateSpecialRewardDescription(String category, String level) {
        Map<String, String> categoryNames = new HashMap<>();
        categoryNames.put("sky", "天空");
        categoryNames.put("moon", "月球");
        categoryNames.put("mars", "火星");
        categoryNames.put("sun", "太阳");
        categoryNames.put("mining", "挖矿");
        categoryNames.put("galaxy", "银河");
        
        String categoryName = categoryNames.getOrDefault(category, category);
        return "§e" + categoryName + "礼包 Lv" + level + " §7- 专属装备套装";
    }
} 