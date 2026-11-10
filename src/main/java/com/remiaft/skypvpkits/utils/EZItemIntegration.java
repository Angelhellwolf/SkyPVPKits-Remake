package com.remiaft.skypvpkits.utils;

import com.remiaft.ezitem.api.EZItemAPI;
import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * EZItem集成工具类
 * 负责通过EZItem API获取模板物品
 * 
 * @author AngelHell
 */
public class EZItemIntegration {
    
    private static final Logger LOGGER = Logger.getLogger(EZItemIntegration.class.getName());
    private final SkyPVPKits plugin;
    private EZItemAPI ezItemAPI;
    private boolean ezItemEnabled = false;
    private final Map<String, ItemStack> templateCache = new ConcurrentHashMap<>();
    private final Set<String> missingTemplates = ConcurrentHashMap.newKeySet();
    
    // 模板名称列表 - 来自ItemConfigGenerator
    private static final List<String> TEMPLATE_NAMES = Arrays.asList(
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
        "1挖矿mining稿", "1挖矿mining金苹果",
        "2挖矿mining稿", "2挖矿mining金苹果",
        "3挖矿mining稿", "3挖矿mining金苹果",
        "4挖矿mining稿", "4挖矿mining金苹果",
        "新银河1_剑", "新银河1_头盔", "新银河1_胸甲", "新银河1_裤", "新银河1_鞋",
        "新银河2_剑", "新银河2_头盔", "新银河2_胸甲", "新银河2_裤", "新银河2_鞋",
        "新银河3_剑", "新银河3_头盔", "新银河3_胸甲", "新银河3_裤", "新银河3_鞋",
        "新银河4_剑", "新银河4_头盔", "新银河4_胸甲", "新银河4_裤", "新银河4_鞋"
    );
    
    public EZItemIntegration(SkyPVPKits plugin) {
        this.plugin = plugin;
        initializeEZItemAPI();
    }
    
    /**
     * 初始化EZItem API
     */
    private void initializeEZItemAPI() {
        templateCache.clear();
        missingTemplates.clear();
        try {
            if (plugin.getServer().getPluginManager().getPlugin("EZItem") != null) {
                this.ezItemAPI = EZItemAPI.getInstance();
                this.ezItemEnabled = true;
                LOGGER.info("EZItem API 集成成功");
            } else {
                LOGGER.warning("EZItem 插件未找到，将使用配置文件模式");
            }
        } catch (Exception e) {
            LOGGER.warning("EZItem API 初始化失败: " + e.getMessage());
            this.ezItemEnabled = false;
        }
    }
    
    /**
     * 检查EZItem是否可用
     */
    public boolean isEZItemEnabled() {
        return ezItemEnabled && ezItemAPI != null;
    }
    
    /**
     * 通过模板名称获取物品
     * @param templateName 模板名称
     * @return 物品实例，如果获取失败返回null
     */
    public ItemStack getItemByTemplate(String templateName) {
        if (!isEZItemEnabled()) {
            return null;
        }
        if (templateName == null) {
            return null;
        }

        String normalizedTemplateName = templateName.trim();
        if (normalizedTemplateName.isEmpty() || missingTemplates.contains(normalizedTemplateName)) {
            return null;
        }

        ItemStack cachedItem = templateCache.get(normalizedTemplateName);
        if (cachedItem != null) {
            return cachedItem.clone();
        }
        
        try {
            ItemStack item = ezItemAPI.getTemplateItem(normalizedTemplateName);
            if (item != null) {
                templateCache.put(normalizedTemplateName, item.clone());
                LOGGER.fine("从EZItem获取物品: " + normalizedTemplateName);
                return item.clone();
            } else {
                missingTemplates.add(normalizedTemplateName);
                LOGGER.warning("EZItem模板不存在: " + normalizedTemplateName);
            }
        } catch (Exception e) {
            LOGGER.warning("从EZItem获取物品失败: " + normalizedTemplateName + " - " + e.getMessage());
        }
        
        return null;
    }

    /**
     * 在没有玩家在线交互的启用/重载阶段预热模板缓存。
     *
     * EZItem 的模板首次读取包含 Bukkit ItemStack 反序列化。把这一步留到菜单回调中
     * 会直接占用服务器主线程，并可能造成接近一秒的停顿。
     *
     * @param templateNames 要预热的模板名称
     * @return 成功放入缓存的模板数量
     */
    public int preloadTemplates(Iterable<String> templateNames) {
        if (!isEZItemEnabled() || templateNames == null) {
            return 0;
        }

        int loaded = 0;
        for (String templateName : templateNames) {
            if (templateName == null) {
                continue;
            }

            String normalizedTemplateName = templateName.trim();
            if (normalizedTemplateName.isEmpty()) {
                continue;
            }

            boolean alreadyCached = templateCache.containsKey(normalizedTemplateName);
            if (getItemByTemplate(normalizedTemplateName) != null && !alreadyCached) {
                loaded++;
            }
        }
        return loaded;
    }

    /**
     * 通过模板名称直接发放物品给玩家
     * 由EZItem负责写入过期时间、绑定和扩展标记
     *
     * @param player 玩家
     * @param templateName 模板名称
     * @return 发放成功返回true
     */
    public boolean giveItemFromTemplate(Player player, String templateName) {
        if (!isEZItemEnabled() || player == null || templateName == null || templateName.trim().isEmpty()) {
            return false;
        }

        try {
            return ezItemAPI.giveItemFromTemplate(player, templateName);
        } catch (Exception e) {
            LOGGER.warning("通过EZItem模板发放物品失败: " + templateName + " - " + e.getMessage());
            return false;
        }
    }

    public boolean isEZItem(ItemStack item) {
        if (!isEZItemEnabled() || item == null) {
            return false;
        }

        try {
            return ezItemAPI.isEZItem(item);
        } catch (Exception e) {
            LOGGER.warning("检测EZItem物品失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 根据礼包类型和等级获取装备物品
     * @param kitType 礼包类型
     * @param level 等级
     * @param equipType 装备类型
     * @return 物品实例，如果获取失败返回null
     */
    public ItemStack getKitItem(String kitType, int level, String equipType) {
        String templateName = generateTemplateName(kitType, level, equipType);
        if (templateName == null) {
            return null;
        }
        return getItemByTemplate(templateName);
    }
    
    /**
     * 生成模板名称
     * 根据礼包类型、等级和装备类型生成对应的模板名称
     */
    private String generateTemplateName(String kitType, int level, String equipType) {
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
                // 挖矿系列只有矿稿，没有护甲
                if (!"剑".equals(equipType)) {
                    return null;
                }
                return level + "挖矿mining稿";
                
            case "galaxy":
                // 新银河系列：新银河1_剑, 新银河1_头盔, 等
                String galaxyEquipType = equipType;
                if (equipType.equals("护腿")) galaxyEquipType = "裤";
                return "新银河" + level + "_" + galaxyEquipType;
                
            default:
                return kitType + level + "_" + equipType;
        }
    }
    
    /**
     * 获取所有可用的模板名称
     */
    public static List<String> getAllTemplateNames() {
        return TEMPLATE_NAMES;
    }
    
    /**
     * 检查模板名称是否有效
     */
    public boolean isValidTemplate(String templateName) {
        return TEMPLATE_NAMES.contains(templateName);
    }
    
    /**
     * 重新初始化API连接
     */
    public void reinitialize() {
        initializeEZItemAPI();
    }
} 
