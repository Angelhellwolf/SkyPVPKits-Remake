package com.remiaft.skypvpkits.utils;

import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品加载器 - 从配置文件加载物品定义
 * 支持解析材质、附魔、颜色等属性
 * 优先使用EZItem API获取物品
 * 
 * @author AngelHell
 */
public class ItemLoader {

    private static final Logger LOGGER = Logger.getLogger(ItemLoader.class.getName());
    private static final int MAX_CACHE_SIZE = 500;
    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000; // 30分钟过期

    private final FileConfiguration config;
    private final Map<String, CacheEntry> itemCache;
    private final SkyPVPKits plugin;

    private static class CacheEntry {
        final ItemStack item;
        final long timestamp;

        CacheEntry(ItemStack item) {
            this.item = item;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRE_MS;
        }
    }

    public ItemLoader(FileConfiguration config) {
        this.config = config;
        this.itemCache = new ConcurrentHashMap<>();
        this.plugin = SkyPVPKits.getInstance();
        LOGGER.info("物品加载器初始化完成");
    }
    
    /**
     * 从配置获取物品
     * 优先使用EZItem API，如果失败则使用配置文件
     * @param itemKey 物品键名
     * @return 创建的物品，如果失败返回null
     */
    public ItemStack getItemFromConfig(String itemKey) {
        // 检查缓存并清理过期项
        CacheEntry cached = itemCache.get(itemKey);
        if (cached != null) {
            if (!cached.isExpired()) {
                return cached.item.clone();
            } else {
                itemCache.remove(itemKey);
            }
        }

        // 清理缓存大小
        if (itemCache.size() >= MAX_CACHE_SIZE) {
            cleanExpiredCache();
        }

        ItemStack item = null;

        // 优先尝试从EZItem获取
        if (plugin != null && plugin.getEZItemIntegration() != null
            && plugin.getEZItemIntegration().isEZItemEnabled()) {
            try {
                item = plugin.getEZItemIntegration().getItemByTemplate(itemKey);
                if (item != null) {
                    LOGGER.fine("从EZItem获取物品: " + itemKey);
                    itemCache.put(itemKey, new CacheEntry(item.clone()));
                    return item.clone();
                }
            } catch (Exception e) {
                LOGGER.warning("从EZItem获取物品失败: " + itemKey + " - " + e.getMessage());
            }
        }

        // 如果EZItem获取失败，尝试从配置文件获取
        try {
            ConfigurationSection itemSection = config.getConfigurationSection("items." + itemKey);
            if (itemSection == null) {
                LOGGER.warning("物品配置不存在: " + itemKey);
                return null;
            }

            item = createItemFromConfig(itemSection);
            if (item != null) {
                LOGGER.fine("从配置文件获取物品: " + itemKey);
                itemCache.put(itemKey, new CacheEntry(item.clone()));
            }

            return item;

        } catch (Exception e) {
            LOGGER.severe("加载物品失败: " + itemKey + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 清理过期的缓存项
     */
    private void cleanExpiredCache() {
        itemCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * 从配置段创建物品
     */
    private ItemStack createItemFromConfig(ConfigurationSection section) {
        try {
            // 获取材质
            String materialName = section.getString("material");
            if (materialName == null) {
                LOGGER.warning("物品缺少材质定义");
                return null;
            }
            
            Material material = Material.getMaterial(materialName);
            if (material == null) {
                LOGGER.warning("未知材质: " + materialName);
                return null;
            }
            
            // 获取数量
            int amount = section.getInt("amount", 1);
            
            // 获取数据值（用于附魔金苹果等）
            short data = (short) section.getInt("data", 0);
            
            // 创建物品
            ItemStack item = new ItemStack(material, amount, data);
            ItemMeta meta = item.getItemMeta();
            
            if (meta != null) {
                // 设置显示名称
                String displayName = section.getString("display_name");
                if (displayName == null) {
                    displayName = section.getString("display-name");
                }
                if (displayName != null) {
                    meta.setDisplayName(displayName.replace("&", "§"));
                }
                
                // 设置描述
                List<String> lore = section.getStringList("lore");
                if (lore != null && !lore.isEmpty()) {
                    List<String> coloredLore = new ArrayList<>();
                    for (String line : lore) {
                        coloredLore.add(line.replace("&", "§"));
                    }
                    meta.setLore(coloredLore);
                }
                
                // 设置附魔
                ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
                if (enchantSection != null) {
                    for (String enchantName : enchantSection.getKeys(false)) {
                        Enchantment enchant = getEnchantmentByName(enchantName);
                        if (enchant != null) {
                            int level = enchantSection.getInt(enchantName);
                            meta.addEnchant(enchant, level, true);
                        } else {
                            LOGGER.warning("未知附魔: " + enchantName);
                        }
                    }
                }
                
                // 设置皮革装备颜色
                if (meta instanceof LeatherArmorMeta) {
                    String colorStr = section.getString("color");
                    if (colorStr != null) {
                        org.bukkit.Color color = parseColor(colorStr);
                        if (color != null) {
                            ((LeatherArmorMeta) meta).setColor(color);
                        }
                    }
                }
                
                item.setItemMeta(meta);
            }
            
            return item;
            
        } catch (Exception e) {
            LOGGER.severe("创建物品时发生错误: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 根据名称获取附魔
     */
    private Enchantment getEnchantmentByName(String name) {
        try {
            // 1.8 附魔名称映射
            switch (name.toUpperCase()) {
                case "PROTECTION_ENVIRONMENTAL":
                case "PROTECTION":
                    return Enchantment.PROTECTION_ENVIRONMENTAL;
                case "PROTECTION_FIRE":
                case "FIRE_PROTECTION":
                    return Enchantment.PROTECTION_FIRE;
                case "PROTECTION_FALL":
                case "FEATHER_FALLING":
                    return Enchantment.PROTECTION_FALL;
                case "PROTECTION_EXPLOSIONS":
                case "BLAST_PROTECTION":
                    return Enchantment.PROTECTION_EXPLOSIONS;
                case "PROTECTION_PROJECTILE":
                case "PROJECTILE_PROTECTION":
                    return Enchantment.PROTECTION_PROJECTILE;
                case "OXYGEN":
                case "RESPIRATION":
                    return Enchantment.OXYGEN;
                case "WATER_WORKER":
                case "AQUA_AFFINITY":
                    return Enchantment.WATER_WORKER;
                case "THORNS":
                    return Enchantment.THORNS;
                case "DAMAGE_ALL":
                case "SHARPNESS":
                    return Enchantment.DAMAGE_ALL;
                case "DAMAGE_UNDEAD":
                case "SMITE":
                    return Enchantment.DAMAGE_UNDEAD;
                case "DAMAGE_ARTHROPODS":
                case "BANE_OF_ARTHROPODS":
                    return Enchantment.DAMAGE_ARTHROPODS;
                case "KNOCKBACK":
                    return Enchantment.KNOCKBACK;
                case "FIRE_ASPECT":
                    return Enchantment.FIRE_ASPECT;
                case "LOOT_BONUS_MOBS":
                case "LOOTING":
                    return Enchantment.LOOT_BONUS_MOBS;
                case "DIG_SPEED":
                case "EFFICIENCY":
                    return Enchantment.DIG_SPEED;
                case "SILK_TOUCH":
                    return Enchantment.SILK_TOUCH;
                case "DURABILITY":
                case "UNBREAKING":
                    return Enchantment.DURABILITY;
                case "LOOT_BONUS_BLOCKS":
                case "FORTUNE":
                    return Enchantment.LOOT_BONUS_BLOCKS;
                case "ARROW_DAMAGE":
                case "POWER":
                    return Enchantment.ARROW_DAMAGE;
                case "ARROW_KNOCKBACK":
                case "PUNCH":
                    return Enchantment.ARROW_KNOCKBACK;
                case "ARROW_FIRE":
                case "FLAME":
                    return Enchantment.ARROW_FIRE;
                case "ARROW_INFINITE":
                case "INFINITY":
                    return Enchantment.ARROW_INFINITE;
                default:
                    // 尝试直接获取
                    return Enchantment.getByName(name);
            }
        } catch (Exception e) {
            LOGGER.warning("获取附魔失败: " + name + " - " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析颜色字符串 "r,g,b"
     */
    private org.bukkit.Color parseColor(String colorStr) {
        try {
            String[] parts = colorStr.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return org.bukkit.Color.fromRGB(r, g, b);
            }
        } catch (Exception e) {
            LOGGER.warning("解析颜色失败: " + colorStr + " - " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取礼包奖励物品列表
     * 根据礼包类型和等级获取所有装备
     */
    public List<ItemStack> getKitRewardItems(String kitType, int level) {
        List<ItemStack> items = new ArrayList<>();
        List<String> configuredRewardKeys = config.getStringList("kit_rewards." + kitType + "." + level + ".items");

        if (!configuredRewardKeys.isEmpty()) {
            for (String itemKey : configuredRewardKeys) {
                if (itemKey == null || itemKey.trim().isEmpty()) {
                    continue;
                }
                ItemStack item = getItemFromConfig(itemKey);
                if (item != null) {
                    items.add(item);
                }
            }

            LOGGER.fine("从奖励配置获取礼包物品: " + kitType + " 等级 " + level + " (" + items.size() + "个物品)");
            return items;
        }

        String[] equipTypes = {"剑", "头盔", "胸甲", "护腿", "鞋子"};
        
        // 优先使用EZItem API
        if (plugin != null && plugin.getEZItemIntegration() != null 
            && plugin.getEZItemIntegration().isEZItemEnabled()) {
            try {
                for (String equipType : equipTypes) {
                    ItemStack item = plugin.getEZItemIntegration().getKitItem(kitType, level, equipType);
                    if (item != null) {
                        items.add(item);
                    }
                }
                
                // 如果从EZItem获取到了物品，直接返回
                if (!items.isEmpty()) {
                    LOGGER.fine("从EZItem获取礼包物品: " + kitType + " 等级 " + level + " (" + items.size() + "个物品)");
                    return items;
                }
            } catch (Exception e) {
                LOGGER.warning("从EZItem获取礼包物品失败: " + kitType + " 等级 " + level + " - " + e.getMessage());
            }
        }
        
        // 如果EZItem获取失败，使用配置文件方式
        for (String equipType : equipTypes) {
            String itemKey = generateItemKey(kitType, level, equipType);
            ItemStack item = getItemFromConfigOnly(itemKey);
            if (item != null) {
                items.add(item);
            }
        }
        
        LOGGER.fine("从配置文件获取礼包物品: " + kitType + " 等级 " + level + " (" + items.size() + "个物品)");
        return items;
    }
    

    
    /**
     * 清空缓存
     */
    public void clearCache() {
        itemCache.clear();
        LOGGER.info("物品缓存已清空");
    }
    
    /**
     * 重载配置
     */
    public void reload(FileConfiguration newConfig) {
        clearCache();
        // 这里可以更新config引用，但由于是final，我们通过重新创建ItemLoader来实现
        LOGGER.info("物品配置已重载");
    }

    /**
     * 仅从配置文件获取物品（不使用EZItem）
     */
    private ItemStack getItemFromConfigOnly(String itemKey) {
        try {
            ConfigurationSection itemSection = config.getConfigurationSection("items." + itemKey);
            if (itemSection == null) {
                return null;
            }
            
            return createItemFromConfig(itemSection);
            
        } catch (Exception e) {
            LOGGER.warning("从配置文件加载物品失败: " + itemKey + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成物品键名
     */
    private String generateItemKey(String kitType, int level, String equipType) {
        switch (kitType) {
            case "sky":
                return level + "天空" + equipType;
            case "mars":
                return level + "火星spark" + equipType;
            case "earth":
                String earthEquipType = equipType;
                if (equipType.equals("剑")) earthEquipType = "剑";
                else if (equipType.equals("头盔")) earthEquipType = "头";
                else if (equipType.equals("胸甲")) earthEquipType = "甲";
                else if (equipType.equals("护腿")) earthEquipType = "腿";
                else if (equipType.equals("鞋子")) earthEquipType = "鞋";
                return "地球" + level + "_" + earthEquipType;
            case "moon":
                return level + "月球moon" + equipType;
            case "sun":
                return level + "太阳sun" + equipType;
            case "mining":
                if ("剑".equals(equipType)) {
                    return level + "挖矿mining稿";
                }
                return null;
            case "galaxy":
                String galaxyEquipType = equipType;
                if (equipType.equals("护腿")) galaxyEquipType = "裤";
                return "新银河" + level + "_" + galaxyEquipType;
            default:
                return kitType + level + "_" + equipType;
        }
    }
} 
