package com.remiaft.skypvpkits.managers;

import com.remiaft.skypvpkits.SkyPVPKits;
import com.remiaft.skypvpkits.data.PlayerKitData;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final SkyPVPKits plugin;
    private Connection connection;
    private String databaseType;

    public DatabaseManager(SkyPVPKits plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        try {
            FileConfiguration config = plugin.getConfig();
            databaseType = config.getString("database.type", "sqlite").toLowerCase();
            
            String url;
            String username = null;
            String password = null;
            
            if ("sqlite".equals(databaseType)) {
                // SQLite配置
                String dbFile = config.getString("database.sqlite_file", "database.db");
                File dataFolder = plugin.getDataFolder();
                if (!dataFolder.exists()) {
                    dataFolder.mkdirs();
                }
                File dbFileObj = new File(dataFolder, dbFile);
                url = "jdbc:sqlite:" + dbFileObj.getAbsolutePath();
                
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection(url);
                
            } else if ("mysql".equals(databaseType)) {
                // MySQL配置
                String host = config.getString("database.mysql.host", "localhost");
                int port = config.getInt("database.mysql.port", 3306);
                String database = config.getString("database.mysql.database", "skypvpkits");
                username = config.getString("database.mysql.username", "root");
                password = config.getString("database.mysql.password", "");

                url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
                
                Class.forName("com.mysql.jdbc.Driver");
                connection = DriverManager.getConnection(url, username, password);
                
            } else {
                throw new IllegalArgumentException("不支持的数据库类型: " + databaseType);
            }

            // 创建表
            createTables();
            
            plugin.getLogger().info("数据库连接成功! 类型: " + databaseType.toUpperCase());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "数据库连接失败:", e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        String playerKitsTable;
        String purchaseHistoryTable;
        
        if ("sqlite".equals(databaseType)) {
            // SQLite语法
            playerKitsTable = "CREATE TABLE IF NOT EXISTS player_kits (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "kit_type TEXT NOT NULL," +
                    "kit_level INTEGER NOT NULL," +
                    "is_permanent INTEGER DEFAULT 0," +
                    "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at TIMESTAMP NULL," +
                    "last_claim_time TIMESTAMP NULL," +
                    "is_active INTEGER DEFAULT 1" +
                    ")";

            purchaseHistoryTable = "CREATE TABLE IF NOT EXISTS purchase_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "kit_type TEXT NOT NULL," +
                    "kit_level INTEGER NOT NULL," +
                    "is_permanent INTEGER DEFAULT 0," +
                    "price DECIMAL(10,2) NOT NULL," +
                    "currency TEXT DEFAULT 'POINTS'," +
                    "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "given_by TEXT NULL" +
                    ")";
        } else {
            // MySQL语法
            playerKitsTable = "CREATE TABLE IF NOT EXISTS player_kits (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "kit_type VARCHAR(50) NOT NULL," +
                    "kit_level INT NOT NULL," +
                    "is_permanent BOOLEAN DEFAULT FALSE," +
                    "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "expires_at TIMESTAMP NULL," +
                    "last_claim_time TIMESTAMP NULL," +
                    "is_active BOOLEAN DEFAULT TRUE," +
                    "INDEX idx_player_uuid (player_uuid)," +
                    "INDEX idx_kit_type (kit_type)" +
                    ")";

            purchaseHistoryTable = "CREATE TABLE IF NOT EXISTS purchase_history (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "kit_type VARCHAR(50) NOT NULL," +
                    "kit_level INT NOT NULL," +
                    "is_permanent BOOLEAN DEFAULT FALSE," +
                    "price DECIMAL(10,2) NOT NULL," +
                    "currency VARCHAR(20) DEFAULT 'POINTS'," +
                    "purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "given_by VARCHAR(36) NULL," +
                    "INDEX idx_player_uuid (player_uuid)," +
                    "INDEX idx_purchased_at (purchased_at)" +
                    ")";
        }

        try (PreparedStatement stmt1 = connection.prepareStatement(playerKitsTable);
             PreparedStatement stmt2 = connection.prepareStatement(purchaseHistoryTable)) {
            
            stmt1.executeUpdate();
            stmt2.executeUpdate();
            
            // 为SQLite创建索引
            if ("sqlite".equals(databaseType)) {
                try (PreparedStatement indexStmt1 = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_kits(player_uuid)");
                     PreparedStatement indexStmt2 = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_kit_type ON player_kits(kit_type)");
                     PreparedStatement indexStmt3 = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_purchase_player_uuid ON purchase_history(player_uuid)");
                     PreparedStatement indexStmt4 = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_purchase_purchased_at ON purchase_history(purchased_at)")) {
                    
                    indexStmt1.executeUpdate();
                    indexStmt2.executeUpdate();
                    indexStmt3.executeUpdate();
                    indexStmt4.executeUpdate();
                }
            }
        }
    }

    public CompletableFuture<List<PlayerKitData>> getPlayerKitDataAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerKitData> dataList = new ArrayList<>();
            
            String query;
            if ("sqlite".equals(databaseType)) {
                query = "SELECT * FROM player_kits WHERE player_uuid = ? AND is_active = 1";
            } else {
                query = "SELECT * FROM player_kits WHERE player_uuid = ? AND is_active = TRUE";
            }
            
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    String kitType = rs.getString("kit_type");
                    int level = rs.getInt("kit_level");
                    boolean isPermanent;
                    
                    if ("sqlite".equals(databaseType)) {
                        isPermanent = rs.getInt("is_permanent") == 1;
                    } else {
                        isPermanent = rs.getBoolean("is_permanent");
                    }
                    
                    Timestamp purchasedAt = rs.getTimestamp("purchased_at");
                    Timestamp expiresAt = rs.getTimestamp("expires_at");
                    Timestamp lastClaimTime = rs.getTimestamp("last_claim_time");
                    
                    // 转换时间戳为LocalDateTime
                    LocalDateTime purchaseTime = purchasedAt != null ? purchasedAt.toLocalDateTime() : null;
                    LocalDateTime expireTime = expiresAt != null ? expiresAt.toLocalDateTime() : null;
                    LocalDateTime lastClaim = lastClaimTime != null ? lastClaimTime.toLocalDateTime() : null;
                    
                    PlayerKitData data = new PlayerKitData(playerUuid, kitType, level, isPermanent, 
                                                         purchaseTime, expireTime, lastClaim);
                    dataList.add(data);
                }
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "加载玩家礼包数据时出错:", e);
            }
            
            return dataList;
        });
    }

    public CompletableFuture<Boolean> savePlayerKitDataAsync(PlayerKitData data) {
        return CompletableFuture.supplyAsync(() -> {
            String updateQuery = "UPDATE player_kits SET last_claim_time = ? WHERE player_uuid = ? AND kit_type = ? AND kit_level = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                // 更新最后领取时间
                if (data.getLastClaimTime() != null) {
                    stmt.setTimestamp(1, Timestamp.valueOf(data.getLastClaimTime()));
                } else {
                    stmt.setNull(1, Types.TIMESTAMP);
                }
                stmt.setString(2, data.getPlayerUuid().toString());
                stmt.setString(3, data.getKitType());
                stmt.setInt(4, data.getKitLevel());
                
                int updated = stmt.executeUpdate();
                return updated > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "保存玩家礼包数据时出错:", e);
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> insertPlayerKitDataAsync(PlayerKitData data) {
        return CompletableFuture.supplyAsync(() -> {
            String insertQuery = "INSERT INTO player_kits (player_uuid, kit_type, kit_level, is_permanent, purchased_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setString(1, data.getPlayerUuid().toString());
                stmt.setString(2, data.getKitType());
                stmt.setInt(3, data.getKitLevel());
                
                if ("sqlite".equals(databaseType)) {
                    stmt.setInt(4, data.isPermanent() ? 1 : 0);
                } else {
                    stmt.setBoolean(4, data.isPermanent());
                }
                
                if (data.getPurchaseTime() != null) {
                    stmt.setTimestamp(5, Timestamp.valueOf(data.getPurchaseTime()));
                } else {
                    stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                }
                
                if (data.getExpireTime() != null) {
                    stmt.setTimestamp(6, Timestamp.valueOf(data.getExpireTime()));
                } else {
                    stmt.setNull(6, Types.TIMESTAMP);
                }
                
                int inserted = stmt.executeUpdate();
                return inserted > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "插入玩家礼包数据时出错:", e);
                return false;
            }
        });
    }

    public void savePurchaseHistoryAsync(UUID playerUuid, String kitType, int level, boolean isPermanent, int price, String currency, String givenBy) {
        CompletableFuture.runAsync(() -> {
            String insertQuery = "INSERT INTO purchase_history (player_uuid, kit_type, kit_level, is_permanent, price, currency, given_by) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(insertQuery)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, kitType);
                stmt.setInt(3, level);
                
                if ("sqlite".equals(databaseType)) {
                    stmt.setInt(4, isPermanent ? 1 : 0);
                } else {
                    stmt.setBoolean(4, isPermanent);
                }
                
                stmt.setInt(5, price);
                stmt.setString(6, currency);
                stmt.setString(7, givenBy);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "保存购买历史时出错:", e);
            }
        });
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * 删除玩家的礼包等级数据
     */
    public CompletableFuture<Boolean> removePlayerKitDataAsync(UUID playerUuid, String kitType, int level) {
        return CompletableFuture.supplyAsync(() -> {
            String deleteQuery = "DELETE FROM player_kits WHERE player_uuid = ? AND kit_type = ? AND kit_level = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, kitType);
                stmt.setInt(3, level);
                
                int deleted = stmt.executeUpdate();
                return deleted > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "删除玩家礼包数据时出错:", e);
                return false;
            }
        });
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("数据库连接已关闭");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "关闭数据库连接时出错:", e);
            }
        }
    }
} 