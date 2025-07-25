package com.remiaft.skypvpkits.database;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.data.PlayerKitData;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * H2数据库管理器
 */
public class DatabaseManager {
    
    private final SkyPVPKits plugin;
    private Connection connection;
    private final String databasePath;
    
    public DatabaseManager(SkyPVPKits plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath() + File.separator + "database" + File.separator + "skypvpkits";
    }
    
    /**
     * 初始化数据库连接
     */
    public boolean initialize() {
        try {
            // 确保数据库目录存在
            File dbDir = new File(plugin.getDataFolder(), "database");
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }
            
            // 复制SQL初始化文件到插件目录
            copyInitSqlFile();
            
            // 加载H2驱动
            Class.forName("org.h2.Driver");
            
            // 创建数据库连接
            String url = "jdbc:h2:" + databasePath + ";AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
            connection = DriverManager.getConnection(url, "sa", "");
            
            // 初始化数据库表
            initializeTables();
            
            plugin.getLogger().info("H2数据库连接成功!");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("H2数据库连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 复制SQL初始化文件
     */
    private void copyInitSqlFile() {
        try {
            File sqlFile = new File(plugin.getDataFolder(), "init.sql");
            if (!sqlFile.exists()) {
                InputStream inputStream = plugin.getResource("init.sql");
                if (inputStream != null) {
                    Files.copy(inputStream, sqlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("已复制SQL初始化文件");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("复制SQL文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 初始化数据库表
     */
    private void initializeTables() throws SQLException {
        // 修正的SQL语句，移除表定义中的INDEX
        String createPlayerKitsTable = 
            "CREATE TABLE IF NOT EXISTS player_kits (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    player_uuid VARCHAR(36) NOT NULL," +
            "    kit_type VARCHAR(20) NOT NULL," +
            "    kit_level INT NOT NULL," +
            "    is_permanent BOOLEAN DEFAULT TRUE," +
            "    purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "    expire_time TIMESTAMP NULL," +
            "    last_claim_time TIMESTAMP NULL" +
            ")";
        
        String createPurchaseHistoryTable = 
            "CREATE TABLE IF NOT EXISTS purchase_history (" +
            "    id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "    player_uuid VARCHAR(36) NOT NULL," +
            "    kit_type VARCHAR(20) NOT NULL," +
            "    kit_level INT NOT NULL," +
            "    purchase_type VARCHAR(10) NOT NULL," +
            "    price_amount INT NOT NULL," +
            "    currency_type VARCHAR(10) NOT NULL," +
            "    purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")";
        
        // 创建索引和约束的SQL语句
        String[] indexSqls = {
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_player_kit_unique ON player_kits (player_uuid, kit_type, kit_level)",
            "CREATE INDEX IF NOT EXISTS idx_expire ON player_kits (expire_time)",
            "CREATE INDEX IF NOT EXISTS idx_player_history ON purchase_history (player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_purchase_time ON purchase_history (purchase_time)"
        };
        
        try (Statement stmt = connection.createStatement()) {
            // 先创建表
            stmt.execute(createPlayerKitsTable);
            stmt.execute(createPurchaseHistoryTable);
            
            // 再创建索引
            for (String indexSql : indexSqls) {
                try {
                    stmt.execute(indexSql);
                } catch (SQLException e) {
                    plugin.getLogger().warning("创建索引时出现警告: " + e.getMessage());
                }
            }
            
            plugin.getLogger().info("数据库表初始化完成");
        }
    }
    
    /**
     * 异步获取玩家礼包数据
     */
    public CompletableFuture<List<PlayerKitData>> getPlayerKitDataAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerKitData> kitDataList = new ArrayList<>();
            
            String sql = 
                "SELECT kit_type, kit_level, is_permanent, purchase_time, " +
                "       expire_time, last_claim_time " +
                "FROM player_kits " +
                "WHERE player_uuid = ? AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP) " +
                "ORDER BY kit_type, kit_level";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        PlayerKitData data = new PlayerKitData(
                            playerUuid,
                            rs.getString("kit_type"),
                            rs.getInt("kit_level"),
                            rs.getBoolean("is_permanent"),
                            rs.getTimestamp("purchase_time").toLocalDateTime(),
                            rs.getTimestamp("expire_time") != null ? rs.getTimestamp("expire_time").toLocalDateTime() : null,
                            rs.getTimestamp("last_claim_time") != null ? rs.getTimestamp("last_claim_time").toLocalDateTime() : null
                        );
                        kitDataList.add(data);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("获取玩家礼包数据失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            return kitDataList;
        });
    }
    
    /**
     * 异步保存玩家礼包数据
     */
    public CompletableFuture<Boolean> savePlayerKitDataAsync(PlayerKitData kitData) {
        return CompletableFuture.supplyAsync(() -> {
            // 使用H2的MERGE语句代替MySQL的ON DUPLICATE KEY UPDATE
            String sql = 
                "MERGE INTO player_kits (player_uuid, kit_type, kit_level, is_permanent, " +
                "                        purchase_time, expire_time, last_claim_time) " +
                "KEY (player_uuid, kit_type, kit_level) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, kitData.getPlayerUuid().toString());
                stmt.setString(2, kitData.getKitType());
                stmt.setInt(3, kitData.getKitLevel());
                stmt.setBoolean(4, kitData.isPermanent());
                stmt.setTimestamp(5, Timestamp.valueOf(kitData.getPurchaseTime()));
                stmt.setTimestamp(6, kitData.getExpireTime() != null ? Timestamp.valueOf(kitData.getExpireTime()) : null);
                stmt.setTimestamp(7, kitData.getLastClaimTime() != null ? Timestamp.valueOf(kitData.getLastClaimTime()) : null);
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("保存玩家礼包数据失败: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * 异步保存购买历史
     */
    public CompletableFuture<Boolean> savePurchaseHistoryAsync(UUID playerUuid, String kitType, int level, 
                                                              String purchaseType, int amount, String currency) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = 
                "INSERT INTO purchase_history (player_uuid, kit_type, kit_level, purchase_type, " +
                "                            price_amount, currency_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, kitType);
                stmt.setInt(3, level);
                stmt.setString(4, purchaseType);
                stmt.setInt(5, amount);
                stmt.setString(6, currency);
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("保存购买历史失败: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * 定期清理过期数据
     */
    public void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredData();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60 * 60, 20L * 60 * 60); // 每小时执行一次
    }
    
    /**
     * 清理过期数据
     */
    private void cleanupExpiredData() {
        String sql = "DELETE FROM player_kits WHERE expire_time IS NOT NULL AND expire_time < CURRENT_TIMESTAMP";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("清理了 " + deleted + " 条过期礼包数据");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("清理过期数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 关闭数据库连接
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("关闭数据库连接时出错: " + e.getMessage());
        }
    }
    
    /**
     * 检查数据库连接
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
} 