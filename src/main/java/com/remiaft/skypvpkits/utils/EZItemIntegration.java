package com.remiaft.skypvpkits.utils;

import com.remiaft.ezitem.api.EZItemAPI;
import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
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
        "1挖矿mining镐", "1挖矿mining头盔", "1挖矿mining胸甲", "1挖矿mining护腿", "1挖矿mining鞋子",
        "2挖矿mining镐", "2挖矿mining头盔", "2挖矿mining胸甲", "2挖矿mining护腿", "2挖矿mining鞋子",
        "3挖矿mining镐", "3挖矿mining头盔", "3挖矿mining胸甲", "3挖矿mining护腿", "3挖矿mining鞋子",
        "4挖矿mining镐", "4挖矿mining头盔", "4挖矿mining胸甲", "4挖矿mining护腿", "4挖矿mining鞋子",
        "1银河galaxy剑", "1银河galaxy头盔", "1银河galaxy胸甲", "1银河galaxy护腿", "1银河galaxy鞋子",
        "2银河galaxy剑", "2银河galaxy头盔", "2银河galaxy胸甲", "2银河galaxy护腿", "2银河galaxy鞋子"
    );
    
    public EZItemIntegration(SkyPVPKits plugin) {
        this.plugin = plugin;
        initializeEZItemAPI();
    }
    
    /**
     * 初始化EZItem API
     */
    private void initializeEZItemAPI() {
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
        
        try {
            ItemStack item = ezItemAPI.getTemplateItem(templateName);
            if (item != null) {
                LOGGER.fine("从EZItem获取物品: " + templateName);
                return item.clone();
            } else {
                LOGGER.warning("EZItem模板不存在: " + templateName);
            }
        } catch (Exception e) {
            LOGGER.warning("从EZItem获取物品失败: " + templateName + " - " + e.getMessage());
        }
        
        return null;
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
                // 挖矿系列：1挖矿mining镐, 1挖矿mining头盔, 等（剑->镐）
                String miningEquipType = equipType.equals("剑") ? "镐" : equipType;
                return level + "挖矿mining" + miningEquipType;
                
            case "galaxy":
                // 银河系列：1银河galaxy剑, 1银河galaxy头盔, 等
                return level + "银河galaxy" + equipType;
                
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