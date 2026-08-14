package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 配置管理器
 * 
 * @author AngelHell
 */
public class ConfigManager {

    private static final String ZXGY_KIT_TYPE = "zxgy";
    private static final String ZXGY_DISPLAY_NAME = "§5沾星顾月";
    private static final List<String> ZXGY_DESCRIPTION = Arrays.asList(
            "§7星月主题弓斧套装",
            "§7永久礼包，附带专属战斗效果"
    );
    private static final List<Integer> ZXGY_PERMANENT_PRICES = Collections.singletonList(88800);
    private static final List<Double> ZXGY_LEVEL_DISCOUNTS = Collections.singletonList(1.0D);
    private static final String YWXC_KIT_TYPE = "ywxc";
    private static final String YWXC_DISPLAY_NAME = "§b仰望星辰";
    private static final List<String> YWXC_DESCRIPTION = Arrays.asList(
            "§7高防星辉主题套装",
            "§7永久礼包，主打压制生命恢复与强力减伤"
    );
    private static final List<Integer> YWXC_PERMANENT_PRICES = Collections.singletonList(181250);
    private static final List<Double> YWXC_LEVEL_DISCOUNTS = Collections.singletonList(0.8D);
    
    private final SkyPVPKits plugin;
    private FileConfiguration config;
    
    public ConfigManager(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }
    
    /**
     * 重载配置
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }
    
    /**
     * 获取配置文件
     */
    public FileConfiguration getConfig() {
        return config;
    }
    
    /**
     * 获取消息前缀
     */
    public String getPrefix() {
        return config.getString("messages.prefix", "§6[礼包系统] §r");
    }
    
    /**
     * 获取消息
     */
    public String getMessage(String key) {
        return getPrefix() + config.getString("messages." + key, "§c消息未找到: " + key);
    }
    
    /**
     * 获取消息（带占位符替换）
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return message;
    }
    
    /**
     * 检查服务器是否处于安全期
     */
    public boolean isServerSafePeriod() {
        return config.getBoolean("server.safe_period", false);
    }
    
    /**
     * 获取联系QQ
     */
    public String getContactQQ() {
        return config.getString("server.contact_qq", "2076049239");
    }
    
    /**
     * 获取数据库配置
     */
    public String getDatabaseHost() {
        return config.getString("database.host", "localhost");
    }
    
    public int getDatabasePort() {
        return config.getInt("database.port", 1433);
    }
    
    public String getDatabaseName() {
        return config.getString("database.database", "skypvpkits");
    }
    
    public String getDatabaseUsername() {
        return config.getString("database.username", "sa");
    }
    
    public String getDatabasePassword() {
        return config.getString("database.password", "password");
    }
    
    public int getDatabaseMaxConnections() {
        return config.getInt("database.max_connections", 10);
    }
    
    public int getDatabaseConnectionTimeout() {
        return config.getInt("database.connection_timeout", 30);
    }
    
    /**
     * 获取礼包配置
     */
    public boolean isKitEnabled(String kitType) {
        if (isBuiltInKit(kitType)) {
            return true;
        }
        return config.getBoolean("kits." + kitType + ".enabled", true);
    }
    
    public String getKitDisplayName(String kitType) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return ZXGY_DISPLAY_NAME;
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return YWXC_DISPLAY_NAME;
        }
        return config.getString("kits." + kitType + ".display_name", "§f未知礼包");
    }
    
    public List<String> getKitDescription(String kitType) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return ZXGY_DESCRIPTION;
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return YWXC_DESCRIPTION;
        }
        return config.getStringList("kits." + kitType + ".description");
    }
    
    public String getKitIcon(String kitType) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return "BOW";
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return "DIAMOND_CHESTPLATE";
        }
        return config.getString("kits." + kitType + ".icon", "CHEST");
    }
    
    public int getKitLevels(String kitType) {
        if (isBuiltInKit(kitType)) {
            return 1;
        }
        return config.getInt("kits." + kitType + ".levels", 1);
    }
    
    public List<Integer> getKitPermanentPrices(String kitType) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return ZXGY_PERMANENT_PRICES;
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return YWXC_PERMANENT_PRICES;
        }
        return config.getIntegerList("kits." + kitType + ".permanent_prices");
    }
    
    public double getKitLevelDiscount(String kitType, int level) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return 1.0D;
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return 0.8D;
        }
        List<Double> discounts = config.getDoubleList("kits." + kitType + ".level_discounts");
        if (level <= 0 || level > discounts.size()) {
            return 1.0; // 默认不打折
        }
        return discounts.get(level - 1);
    }
    
    public List<Double> getKitLevelDiscounts(String kitType) {
        if (ZXGY_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return ZXGY_LEVEL_DISCOUNTS;
        }
        if (YWXC_KIT_TYPE.equalsIgnoreCase(kitType)) {
            return YWXC_LEVEL_DISCOUNTS;
        }
        return config.getDoubleList("kits." + kitType + ".level_discounts");
    }
    
    public boolean isKitSupportPoints(String kitType) {
        if (isBuiltInKit(kitType)) {
            return true;
        }
        return config.getBoolean("kits." + kitType + ".support_points", true);
    }
    
    public boolean isKitSupportMonthly(String kitType) {
        if (isBuiltInKit(kitType)) {
            return false;
        }
        return config.getBoolean("kits." + kitType + ".support_monthly", true);
    }

    public List<String> getKitRewardItems(String kitType, int level) {
        if (isBuiltInKit(kitType)) {
            return Collections.emptyList();
        }
        return config.getStringList("kit_rewards." + kitType + "." + level + ".items");
    }

    private boolean isBuiltInKit(String kitType) {
        return ZXGY_KIT_TYPE.equalsIgnoreCase(kitType) || YWXC_KIT_TYPE.equalsIgnoreCase(kitType);
    }
    
    /**
     * 获取菜单配置
     */
    public String getMainMenuTitle() {
        return config.getString("menu.main.title", "§6§l礼包商城");
    }
    
    public int getMainMenuSize() {
        return config.getInt("menu.main.size", 54);
    }
    
    public String getMainMenuBackground() {
        return config.getString("menu.main.background", "STAINED_GLASS_PANE:7");
    }
    
    public String getKitDetailMenuTitle() {
        return config.getString("menu.kit_detail.title", "§6§l{kit_name} §6§l- 等级 {level}");
    }
    
    public int getKitDetailMenuSize() {
        return config.getInt("menu.kit_detail.size", 54);
    }
    
    public String getKitDetailMenuBackground() {
        return config.getString("menu.kit_detail.background", "STAINED_GLASS_PANE:7");
    }
} 
