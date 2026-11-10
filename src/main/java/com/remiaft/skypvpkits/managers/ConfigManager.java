package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private boolean allKitsFreeEnabled;
    private Instant allKitsFreeStartsAt;
    private Instant allKitsFreeEndsAt;
    private ZoneId allKitsFreeZone;
    private Set<String> allKitsFreeExcludedKits = Collections.emptySet();
    private boolean nationalDayEnabled;
    private Instant nationalDayStartsAt;
    private Instant nationalDayEndsAt;
    private ZoneId nationalDayZone;
    private String nationalDayPromotionId = "national-day-2026";
    
    public ConfigManager(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadAllKitsFreePromotion();
        loadNationalDayPromotion();
    }
    
    /**
     * 重载配置
     */
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        loadAllKitsFreePromotion();
        loadNationalDayPromotion();
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

    /**
     * 全服免费体验是一个虚拟权益，不会写入或覆盖玩家的充值礼包数据。
     * 仅每种礼包的最高等级参与体验，保持原有“高等级覆盖低等级”的领取规则。
     */
    public boolean isAllKitsFreeLevel(String kitType, int level) {
        return kitType != null
                && isAllKitsFreeActive()
                && isKitEnabled(kitType)
                && !allKitsFreeExcludedKits.contains(kitType.toLowerCase(Locale.ROOT))
                && level == getKitLevels(kitType);
    }

    public boolean isAllKitsFreeKitEligible(String kitType) {
        return kitType != null
                && isAllKitsFreeActive()
                && isKitEnabled(kitType)
                && !allKitsFreeExcludedKits.contains(kitType.toLowerCase(Locale.ROOT));
    }

    public boolean isAllKitsFreeActive() {
        if (!allKitsFreeEnabled || allKitsFreeStartsAt == null || allKitsFreeEndsAt == null) {
            return false;
        }
        Instant now = Instant.now();
        return !now.isBefore(allKitsFreeStartsAt) && now.isBefore(allKitsFreeEndsAt);
    }

    public String getAllKitsFreePromotionId() {
        return config.getString("promotions.all_kits_free.id", "all-kits-free");
    }

    public String getAllKitsFreeEndDisplay() {
        if (allKitsFreeEndsAt == null || allKitsFreeZone == null) {
            return "未知";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(ZonedDateTime.ofInstant(allKitsFreeEndsAt, allKitsFreeZone));
    }

    public long getAllKitsFreeEndEpochMillis() {
        return allKitsFreeEndsAt == null ? -1L : allKitsFreeEndsAt.toEpochMilli();
    }

    public String getAllKitsFreeZoneId() {
        return allKitsFreeZone == null ? "Asia/Hong_Kong" : allKitsFreeZone.getId();
    }

    public String getAllKitsFreeExcludedKitsDisplay() {
        return allKitsFreeExcludedKits.isEmpty()
                ? "无"
                : String.join(", ", allKitsFreeExcludedKits);
    }

    public int getDailyResetHour() {
        int hour = config.getInt("system.daily_reset_hour", 4);
        return Math.max(0, Math.min(23, hour));
    }

    private void loadAllKitsFreePromotion() {
        allKitsFreeEnabled = config.getBoolean("promotions.all_kits_free.enabled", false);
        allKitsFreeStartsAt = null;
        allKitsFreeEndsAt = null;
        LinkedHashSet<String> excludedKits = new LinkedHashSet<>();
        String excludedKitsPath = "promotions.all_kits_free.excluded_kits";
        List<String> configuredExcludedKits = config.contains(excludedKitsPath)
                ? config.getStringList(excludedKitsPath)
                : Arrays.asList("sun", "galaxy");
        for (String kitType : configuredExcludedKits) {
            if (kitType != null && !kitType.trim().isEmpty()) {
                excludedKits.add(kitType.trim().toLowerCase(Locale.ROOT));
            }
        }
        allKitsFreeExcludedKits = Collections.unmodifiableSet(excludedKits);

        String zoneName = config.getString("promotions.all_kits_free.timezone", "Asia/Hong_Kong");
        String startsAt = config.getString("promotions.all_kits_free.starts_at", "");
        String endsAt = config.getString("promotions.all_kits_free.ends_at", "");
        try {
            allKitsFreeZone = ZoneId.of(zoneName);
            allKitsFreeStartsAt = LocalDateTime.parse(startsAt).atZone(allKitsFreeZone).toInstant();
            allKitsFreeEndsAt = LocalDateTime.parse(endsAt).atZone(allKitsFreeZone).toInstant();
            if (!allKitsFreeEndsAt.isAfter(allKitsFreeStartsAt)) {
                throw new DateTimeException("结束时间必须晚于开始时间");
            }
        } catch (DateTimeException exception) {
            allKitsFreeEnabled = false;
            allKitsFreeZone = ZoneId.of("Asia/Hong_Kong");
            if (config.getBoolean("promotions.all_kits_free.enabled", false)) {
                plugin.getLogger().warning("全服礼包免费体验时间配置无效，活动已停用: " + exception.getMessage());
            }
        }
    }

    public boolean isNationalDayActive() {
        if (!nationalDayEnabled || nationalDayStartsAt == null || nationalDayEndsAt == null) {
            return false;
        }
        return isNationalDayActive(Instant.now());
    }

    public boolean isNationalDayActive(Instant now) {
        if (!nationalDayEnabled || nationalDayStartsAt == null || nationalDayEndsAt == null) return false;
        return !now.isBefore(nationalDayStartsAt) && now.isBefore(nationalDayEndsAt);
    }

    public String getNationalDayEndDisplay() {
        if (nationalDayEndsAt == null || nationalDayZone == null) {
            return "2026-10-07 23:59";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(ZonedDateTime.ofInstant(nationalDayEndsAt.minusSeconds(1), nationalDayZone));
    }

    public long getNationalDayEndEpochMillis() {
        return nationalDayEndsAt == null ? -1L : nationalDayEndsAt.toEpochMilli();
    }

    public String getNationalDayZoneId() {
        return nationalDayZone == null ? "Asia/Hong_Kong" : nationalDayZone.getId();
    }

    public String getNationalDayPromotionId() {
        return nationalDayPromotionId;
    }

    public boolean isNationalDayBaseKit(String kitType, int level) {
        if (!isNationalDayActive() || kitType == null) return false;
        String k = kitType.toLowerCase(Locale.ROOT);
        return ("moon".equals(k) && level == 3)
                || ("earth".equals(k) && level == 4)
                || ("mining".equals(k) && level == 3);
    }

    private void loadNationalDayPromotion() {
        nationalDayEnabled = config.getBoolean("promotions.national_day_2026.enabled", true);
        nationalDayStartsAt = null;
        nationalDayEndsAt = null;
        nationalDayPromotionId = config.getString("promotions.national_day_2026.id", "national-day-2026");
        String zoneName = config.getString("promotions.national_day_2026.timezone", "Asia/Hong_Kong");
        String startsAt = config.getString("promotions.national_day_2026.starts_at", "2026-10-01T00:00:00");
        String endsAt = config.getString("promotions.national_day_2026.ends_at", "2026-10-08T00:00:00");
        try {
            nationalDayZone = ZoneId.of(zoneName);
            nationalDayStartsAt = LocalDateTime.parse(startsAt).atZone(nationalDayZone).toInstant();
            nationalDayEndsAt = LocalDateTime.parse(endsAt).atZone(nationalDayZone).toInstant();
            if (!nationalDayEndsAt.isAfter(nationalDayStartsAt)) {
                throw new DateTimeException("结束时间必须晚于开始时间");
            }
        } catch (DateTimeException exception) {
            nationalDayEnabled = false;
            nationalDayZone = ZoneId.of("Asia/Hong_Kong");
            if (config.getBoolean("promotions.national_day_2026.enabled", true)) {
                plugin.getLogger().warning("国庆礼包免费体验时间配置无效，活动已停用: " + exception.getMessage());
            }
        }
    }

    public List<String> getKitRewardItems(String kitType, int level) {
        if (isBuiltInKit(kitType)) {
            return Collections.emptyList();
        }
        return config.getStringList("kit_rewards." + kitType + "." + level + ".items");
    }

    /**
     * 返回当前配置实际引用的所有 EZItem 模板。
     *
     * 菜单预览会频繁读取这些模板。集中收集后可在插件启用阶段完成一次反序列化，
     * 避免玩家第一次打开装备预览时在主线程触发昂贵的冷加载。
     */
    public List<String> getAllKitRewardItems() {
        Set<String> itemKeys = new LinkedHashSet<>();
        for (String kitType : KitManager.KIT_TYPES) {
            int levels = getKitLevels(kitType);
            for (int level = 1; level <= levels; level++) {
                for (String itemKey : getKitRewardItems(kitType, level)) {
                    if (itemKey == null) {
                        continue;
                    }
                    String normalizedKey = itemKey.trim();
                    if (!normalizedKey.isEmpty()) {
                        itemKeys.add(normalizedKey);
                    }
                }
            }
        }
        return new ArrayList<>(itemKeys);
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
