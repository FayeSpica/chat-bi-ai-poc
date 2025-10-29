-- 服务自身数据库初始化脚本
-- 这个文件会在MySQL容器启动时自动执行

-- 创建服务自身数据库
CREATE DATABASE IF NOT EXISTS chatbi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatbi_db;

-- 创建用户白名单表
CREATE TABLE IF NOT EXISTS user_whitelist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(191) NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'READER' COMMENT '用户角色: ADMIN/OPERATOR/READER',
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_whitelist_user_id (user_id),
    KEY idx_user_whitelist_user_id (user_id),
    KEY idx_user_whitelist_role (role)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 为现有数据添加默认角色（如果表已存在且有数据）
-- 注意：如果表已存在但 column 已存在，此语句会失败，需要手动处理
-- 可以通过检查是否已存在该列来避免错误：
-- SELECT COUNT(*) FROM information_schema.COLUMNS 
-- WHERE TABLE_SCHEMA = 'chatbi_db' AND TABLE_NAME = 'user_whitelist' AND COLUMN_NAME = 'role';
-- 
-- 如果不存在 role 列，执行以下语句：
-- ALTER TABLE user_whitelist 
-- ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'READER' COMMENT '用户角色: ADMIN/OPERATOR/READER' AFTER user_id;
-- ALTER TABLE user_whitelist ADD INDEX idx_user_whitelist_role (role);

-- 创建数据库连接配置表
CREATE TABLE IF NOT EXISTS database_connection (
    id VARCHAR(191) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL DEFAULT 3306,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    database_name VARCHAR(255) NOT NULL,
    charset_name VARCHAR(50) DEFAULT 'utf8mb4',
    description TEXT,
    is_active TINYINT(1) DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_database_connection_is_active (is_active),
    KEY idx_database_connection_name (name)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

