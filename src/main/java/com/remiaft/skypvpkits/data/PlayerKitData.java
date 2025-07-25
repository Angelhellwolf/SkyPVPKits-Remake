package com.remiaft.skypvpkits.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家礼包数据
 * 
 * @author AngelHell
 */
public class PlayerKitData {
    
    private final UUID playerUuid;
    private final Map<String, KitInfo> ownedKits;
    
    // 单个礼包数据构造器 - 用于数据库查询结果
    private String kitType;
    private int kitLevel;
    private boolean permanent;
    private LocalDateTime purchaseTime;
    private LocalDateTime expireTime;
    private LocalDateTime lastClaimTime;
    
    public PlayerKitData(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.ownedKits = new HashMap<>();
    }
    
    // 单个礼包数据构造器
    public PlayerKitData(UUID playerUuid, String kitType, int kitLevel, boolean permanent, 
                        LocalDateTime purchaseTime, LocalDateTime expireTime, LocalDateTime lastClaimTime) {
        this.playerUuid = playerUuid;
        this.ownedKits = new HashMap<>();
        this.kitType = kitType;
        this.kitLevel = kitLevel;
        this.permanent = permanent;
        this.purchaseTime = purchaseTime;
        this.expireTime = expireTime;
        this.lastClaimTime = lastClaimTime;
    }
    
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    public Map<String, KitInfo> getOwnedKits() {
        return ownedKits;
    }
    
    // 单个礼包数据的getter方法
    public String getKitType() {
        return kitType;
    }
    
    public int getKitLevel() {
        return kitLevel;
    }
    
    public boolean isPermanent() {
        return permanent;
    }
    
    public LocalDateTime getPurchaseTime() {
        return purchaseTime;
    }
    
    public LocalDateTime getExpireTime() {
        return expireTime;
    }
    
    public LocalDateTime getLastClaimTime() {
        return lastClaimTime;
    }
    
    // 检查是否已过期
    public boolean isExpired() {
        if (permanent) {
            return false;
        }
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }
    
    // 获取剩余天数
    public long getRemainingDays() {
        if (permanent) {
            return -1; // 永久
        }
        if (expireTime == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDateTime.now(), expireTime);
    }
    
    // 检查今天是否已领取（基于凌晨4点刷新）
    public boolean hasClaimedToday() {
        if (lastClaimTime == null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        
        // 计算当前游戏日的开始时间（凌晨4点）
        LocalDateTime currentGameDayStart;
        if (now.getHour() >= 4) {
            // 如果当前时间是4点或之后，今天的游戏日已经开始
            currentGameDayStart = now.toLocalDate().atTime(4, 0);
        } else {
            // 如果当前时间是4点之前，还在昨天的游戏日
            currentGameDayStart = now.toLocalDate().minusDays(1).atTime(4, 0);
        }
        
        // 计算上次领取时间对应的游戏日开始时间
        LocalDateTime lastClaimGameDayStart;
        if (lastClaimTime.getHour() >= 4) {
            lastClaimGameDayStart = lastClaimTime.toLocalDate().atTime(4, 0);
        } else {
            lastClaimGameDayStart = lastClaimTime.toLocalDate().minusDays(1).atTime(4, 0);
        }
        
        // 如果是同一个游戏日，则认为已经领取过了
        return currentGameDayStart.equals(lastClaimGameDayStart);
    }
    
    // 标记今天已领取
    public void markClaimedToday() {
        this.lastClaimTime = LocalDateTime.now();
    }
    
    public boolean hasKit(String kitName, int level) {
        String key = kitName + "_" + level;
        return ownedKits.containsKey(key) && ownedKits.get(key).isActive();
    }
    
    public void addKit(String kitName, int level, String type, long expiresAt) {
        String key = kitName + "_" + level;
        KitInfo kitInfo = new KitInfo(kitName, level, type, System.currentTimeMillis(), expiresAt);
        ownedKits.put(key, kitInfo);
    }
    
    public void removeKit(String kitName, int level) {
        String key = kitName + "_" + level;
        ownedKits.remove(key);
    }
    
    public static class KitInfo {
        private final String kitName;
        private final int level;
        private final String type; // "permanent" or "monthly"
        private final long purchasedAt;
        private final long expiresAt; // 0 for permanent
        
        public KitInfo(String kitName, int level, String type, long purchasedAt, long expiresAt) {
            this.kitName = kitName;
            this.level = level;
            this.type = type;
            this.purchasedAt = purchasedAt;
            this.expiresAt = expiresAt;
        }
        
        public String getKitName() {
            return kitName;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getType() {
            return type;
        }
        
        public long getPurchasedAt() {
            return purchasedAt;
        }
        
        public long getExpiresAt() {
            return expiresAt;
        }
        
        public boolean isActive() {
            if ("permanent".equals(type)) {
                return true;
            }
            return expiresAt == 0 || System.currentTimeMillis() < expiresAt;
        }
    }
} 