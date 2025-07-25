-- SkyPVPKits H2数据库初始化脚本
-- 创建时间: 2024
-- 作者: AngelHell

-- 玩家礼包数据表
CREATE TABLE IF NOT EXISTS player_kits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    kit_type VARCHAR(20) NOT NULL,
    kit_level INT NOT NULL,
    is_permanent BOOLEAN DEFAULT TRUE,
    purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_time TIMESTAMP NULL,
    last_claim_time TIMESTAMP NULL
);

-- 创建索引（为MERGE语句创建唯一索引）
CREATE UNIQUE INDEX IF NOT EXISTS idx_player_kit_unique ON player_kits (player_uuid, kit_type, kit_level);
CREATE INDEX IF NOT EXISTS idx_expire ON player_kits (expire_time);

-- 购买历史表
CREATE TABLE IF NOT EXISTS purchase_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    kit_type VARCHAR(20) NOT NULL,
    kit_level INT NOT NULL,
    purchase_type VARCHAR(10) NOT NULL,  -- 'permanent' 或 'monthly'
    price_amount INT NOT NULL,
    currency_type VARCHAR(10) NOT NULL,  -- 'points' 或 'coins'
    purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_player_history ON purchase_history (player_uuid);
CREATE INDEX IF NOT EXISTS idx_purchase_time ON purchase_history (purchase_time);

-- 插入初始化完成标记（使用H2兼容的语法）
MERGE INTO purchase_history (player_uuid, kit_type, kit_level, purchase_type, price_amount, currency_type) 
KEY (player_uuid, kit_type, kit_level)
VALUES ('00000000-0000-0000-0000-000000000000', 'system', 0, 'init', 0, 'system'); 