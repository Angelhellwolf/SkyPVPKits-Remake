package com.remiaft.skypvpkits;

import com.remiaft.skypvpkits.commands.InitializeItemsCommand;
import com.remiaft.skypvpkits.commands.KitsCommand;
import com.remiaft.skypvpkits.commands.OpenKitsCommand;
import com.remiaft.skypvpkits.commands.TestEZItemCommand;
import com.remiaft.skypvpkits.listeners.MenuListener;
import com.remiaft.skypvpkits.managers.ConfigManager;
import com.remiaft.skypvpkits.managers.DatabaseManager;
import com.remiaft.skypvpkits.managers.KitManager;
import com.remiaft.skypvpkits.managers.MenuManager;
import com.remiaft.skypvpkits.utils.ItemLoader;
import com.remiaft.skypvpkits.utils.EZItemIntegration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SkyPVPKits 主插件类
 * 
 * @author AngelHell
 * @version 1.0.0
 */
public final class SkyPVPKits extends JavaPlugin {
    
    private static SkyPVPKits instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private KitManager kitManager;
    private MenuManager menuManager;
    private ItemLoader itemLoader;
    private EZItemIntegration ezItemIntegration;

    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化管理器
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.kitManager = new KitManager(this);
        this.menuManager = new MenuManager(this);
        this.itemLoader = new ItemLoader(configManager.getConfig());
        this.ezItemIntegration = new EZItemIntegration(this);
        
        // 初始化数据库
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // 注册命令
        getCommand("kits").setExecutor(new KitsCommand(this));
        getCommand("openkits").setExecutor(new OpenKitsCommand(this));
        getCommand("initializeitems").setExecutor(new InitializeItemsCommand(this));
        getCommand("testezitem").setExecutor(new TestEZItemCommand(this));
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        
        getLogger().info("SkyPVPKits plugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        if (menuManager != null) {
            menuManager.stopAutoRefresh();
        }
        if (kitManager != null) {
            kitManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("SkyPVPKits plugin has been disabled!");
    }
    
    // Getters
    public static SkyPVPKits getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public KitManager getKitManager() {
        return kitManager;
    }
    
    public MenuManager getMenuManager() {
        return menuManager;
    }
    
    public ItemLoader getItemLoader() {
        return itemLoader;
    }
    
    public EZItemIntegration getEZItemIntegration() {
        return ezItemIntegration;
    }
} 