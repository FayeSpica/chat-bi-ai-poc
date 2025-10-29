-- User whitelist table
CREATE TABLE IF NOT EXISTS user_whitelist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(191) NULL,
  user_name VARCHAR(191) NULL,
  token_value VARCHAR(512) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_whitelist_user_id (user_id),
  UNIQUE KEY uk_user_whitelist_user_name (user_name),
  UNIQUE KEY uk_user_whitelist_token_value (token_value),
  KEY idx_user_whitelist_user_id (user_id),
  KEY idx_user_whitelist_user_name (user_name),
  KEY idx_user_whitelist_token_value (token_value)
);
