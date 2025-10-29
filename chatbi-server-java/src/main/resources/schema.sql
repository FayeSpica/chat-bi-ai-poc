-- User whitelist table
CREATE TABLE IF NOT EXISTS user_whitelist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(191) NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_whitelist_user_id (user_id),
  KEY idx_user_whitelist_user_id (user_id)
);

-- Chat sessions table
CREATE TABLE IF NOT EXISTS chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(191) NOT NULL,
  title VARCHAR(255) DEFAULT NULL,
  archived TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_chat_session_user_id (user_id),
  KEY idx_chat_session_updated_at (updated_at),
  KEY idx_chat_session_archived (archived)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Chat messages table
CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL,
  content LONGTEXT NOT NULL,
  semantic_sql LONGTEXT NULL,
  sql_query LONGTEXT NULL,
  execution_result LONGTEXT NULL,
  debug_info LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_message_session_id (session_id),
  KEY idx_chat_message_created_at (created_at),
  CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
