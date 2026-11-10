package com.remiaft.skypvpkits;

import com.remiaft.skypvpkits.commands.KitsCommand;
import com.remiaft.skypvpkits.commands.OpenKitsCommand;
import com.remiaft.skypvpkits.listeners.MenuListener;
import com.remiaft.skypvpkits.managers.ConfigManager;
import com.remiaft.skypvpkits.managers.DatabaseManager;
import com.remiaft.skypvpkits.managers.KitManager;
import com.remiaft.skypvpkits.managers.MenuManager;
import com.remiaft.skypvpkits.utils.ItemLoader;
import com.remiaft.skypvpkits.utils.EZItemIntegration;
import com.remiaft.skypvpkits.bridge.AngelRewardsBridge;
import org.bukkit.command.PluginCommand;
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
    private AngelRewardsBridge angelRewards;

    @Override
    public void onEnable() {
        instance = this;
        
        // 保存默认配置文件
        saveDefaultConfig();
        
        // 初始化管理器
        this.configManager = new ConfigManager(this);
        try { configureRewardProvider(); }
        catch (RuntimeException failure) {
            getLogger().severe("Reward provider validation failed; shop disabled (KIT-PROVIDER-01).");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.databaseManager = new DatabaseManager(this);
        
        // 初始化数据库
        if (!databaseManager.initialize()) {
            getLogger().severe("Failed to initialize database! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.itemLoader = new ItemLoader(configManager.getConfig());
        this.ezItemIntegration = new EZItemIntegration(this);
        preloadEZItemTemplates();
        this.kitManager = new KitManager(this);
        this.menuManager = new MenuManager(this);

        if (configManager.isAllKitsFreeActive()) {
            getLogger().info("全服礼包免费体验已开启，结束时间: "
                    + configManager.getAllKitsFreeEndDisplay());
            getLogger().info("全服礼包免费体验排除礼包: "
                    + configManager.getAllKitsFreeExcludedKitsDisplay());
        }
        
        // 注册命令
        KitsCommand kitsCommand = new KitsCommand(this);
        OpenKitsCommand openKitsCommand = new OpenKitsCommand(this);
        registerCommand("kits", kitsCommand, kitsCommand);
        registerCommand("skypvpkits", kitsCommand, kitsCommand);
        registerCommand("openkits", openKitsCommand, openKitsCommand);
        
        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (configManager.isNationalDayActive())
                for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) kitManager.checkNationalDayGems(player);
        }, 100L, 1200L);
        getLogger().info("国庆礼包活动窗口：2026-10-01 至 " + configManager.getNationalDayEndDisplay());
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

    private void registerCommand(String commandName,
                                 org.bukkit.command.CommandExecutor executor,
                                 org.bukkit.command.TabCompleter tabCompleter) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            getLogger().severe("Command not found in plugin.yml: " + commandName);
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
    }

    public void reloadPluginData() {
        if (configManager != null) {
            configManager.reload();
        } else {
            reloadConfig();
        }
        configureRewardProvider();

        if (kitManager != null) {
            kitManager.reload();
        }

        if (itemLoader != null) {
            itemLoader.clearCache();
        }
        itemLoader = new ItemLoader(getConfig());

        if (ezItemIntegration != null) {
            ezItemIntegration.reinitialize();
            preloadEZItemTemplates();
        }
    }

    private void preloadEZItemTemplates() {
        if (angelRewards != null) return;
        if (ezItemIntegration == null || configManager == null || !ezItemIntegration.isEZItemEnabled()) {
            return;
        }

        long startedAt = System.nanoTime();
        int loaded = ezItemIntegration.preloadTemplates(configManager.getAllKitRewardItems());
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        getLogger().info("Preloaded " + loaded + " EZItem templates in " + elapsedMillis
                + " ms to prevent first-menu tick stalls");
    }

    public AngelRewardsBridge getAngelRewards() { return angelRewards; }

    private void configureRewardProvider() {
        String name = getConfig().getString("item-provider", "EZITEM");
        if ("EZITEM".equalsIgnoreCase(name)) { angelRewards = null; return; }
        if (!"ANGELITEM".equalsIgnoreCase(name)) throw new IllegalArgumentException("Unknown item-provider");
        try {
            AngelRewardsBridge candidate = new AngelRewardsBridge();
            for (String family : KitManager.KIT_TYPES) {
                if (!configManager.isKitEnabled(family)) continue;
                if ("zxgy".equals(family) || "ywxc".equals(family)) {
                    com.remiaft.skypvpkits.bridge.SkyPVPItemBridge.previewSet(family);
                    continue;
                }
                for (int level = 1; level <= configManager.getKitLevels(family); level++) candidate.preview(family, level);
            }
            angelRewards = candidate;
            getLogger().info("Shop reward provider: AngelItem versioned catalog (via AngelKits)");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("AngelItem reward API unavailable", failure); }
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
