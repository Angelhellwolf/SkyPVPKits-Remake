package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 配置管理器
 * 
 * @author AngelHell
 */
public class ConfigManager {
    
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
        return config.getBoolean("kits." + kitType + ".enabled", true);
    }
    
    public String getKitDisplayName(String kitType) {
        return config.getString("kits." + kitType + ".display_name", "§f未知礼包");
    }
    
    public List<String> getKitDescription(String kitType) {
        return config.getStringList("kits." + kitType + ".description");
    }
    
    public String getKitIcon(String kitType) {
        return config.getString("kits." + kitType + ".icon", "CHEST");
    }
    
    public int getKitLevels(String kitType) {
        return config.getInt("kits." + kitType + ".levels", 1);
    }
    
    public List<Integer> getKitPermanentPrices(String kitType) {
        return config.getIntegerList("kits." + kitType + ".permanent_prices");
    }
    
    public double getKitLevelDiscount(String kitType, int level) {
        List<Double> discounts = config.getDoubleList("kits." + kitType + ".level_discounts");
        if (level <= 0 || level > discounts.size()) {
            return 1.0; // 默认不打折
        }
        return discounts.get(level - 1);
    }
    
    public List<Double> getKitLevelDiscounts(String kitType) {
        return config.getDoubleList("kits." + kitType + ".level_discounts");
    }
    
    public boolean isKitSupportPoints(String kitType) {
        return config.getBoolean("kits." + kitType + ".support_points", true);
    }
    
    public boolean isKitSupportMonthly(String kitType) {
        return config.getBoolean("kits." + kitType + ".support_monthly", true);
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