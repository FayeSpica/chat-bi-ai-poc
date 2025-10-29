# 新增基于userId的会话功能
_Exported on 2025/10/30 at GMT+8 3:09:43 from Cursor (2.0.34)_

---

**User**

基于userId新增会话功能，每轮对话的历史都可以重现

---

**Cursor**

我需要先确认两点以便给出精准实现方案：

1. userId 来源应如何确定？
   - a) 由后端 `TokenInterceptor` 从令牌中解析得到
   - b) 前端在每次请求体中显式传 `userId`
   - c) 其它（请说明）

2. 前端是否也要一起实现多会话管理的 UI？
   - a) 要：左侧会话列表（新建/重命名/删除/继续），点击可恢复历史
   - b) 暂不要：先提供后端会话与消息的 API，UI 后续再做

---

**User**

1. a) 2. a)

---

**Cursor**

I'll draft a concise, end-to-end plan to add per-user sessions with full history, including backend schema/endpoints and the frontend session UI with create/rename/delete and restore.

---

**User**

Implement the plan as specified. To-do's from the plan have already been created. Do not create them again. Mark them as in_progress as you work, starting with the first one.

---

**Cursor**

I'll start by updating the first task "Add chat_session and chat_message tables and indexes" and then implement the database schema changes in both `schema.sql` and `db-init` files.

I'll open the database schema files to extend them safely for the new tables and indexes.

I'll add the new `chat_session` and `chat_message` tables with indexes to both the application schema and the Docker init schema.

```sql
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
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_message_session_id (session_id),
  KEY idx_chat_message_created_at (created_at),
  CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

```

```sql
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

-- 创建会话表
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

-- 创建会话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_message_session_id (session_id),
    KEY idx_chat_message_created_at (created_at),
    CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


```

I completed the database schema updates. Next I'll mark the first todo completed and begin implementing the backend models `ChatSession` and `ChatMessage`.

```java
package com.chatbi.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_id", columnList = "user_id"),
        @Index(name = "idx_chat_session_updated_at", columnList = "updated_at"),
        @Index(name = "idx_chat_session_archived", columnList = "archived")
})
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", length = 191, nullable = false)
    private String userId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "archived", nullable = false)
    private Boolean archived = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}


```

```java
package com.chatbi.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_id", columnList = "session_id"),
        @Index(name = "idx_chat_message_created_at", columnList = "created_at")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "role", length = 20, nullable = false)
    private String role; // user / assistant / system

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}


```

```java
package com.chatbi.repository;

import com.chatbi.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(String userId);

    Optional<ChatSession> findByIdAndUserId(Long id, String userId);
}


```

```java
package com.chatbi.repository;

import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    void deleteBySession(ChatSession session);
}


```

```java
package com.chatbi.service;

import com.chatbi.model.ChatSession;
import com.chatbi.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    public List<ChatSession> listSessions(String userId) {
        return chatSessionRepository.findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(userId);
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setArchived(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return chatSessionRepository.save(session);
    }

    public Optional<ChatSession> getByIdForUser(Long id, String userId) {
        return chatSessionRepository.findByIdAndUserId(id, userId);
    }

    public Optional<ChatSession> renameSession(Long id, String userId, String title) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        optional.ifPresent(s -> {
            s.setTitle(title);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
        });
        return optional;
    }

    public boolean archiveSession(Long id, String userId) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        if (optional.isPresent()) {
            ChatSession s = optional.get();
            s.setArchived(true);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
            return true;
        }
        return false;
    }

    public void touchUpdatedAt(ChatSession session) {
        session.setUpdatedAt(OffsetDateTime.now());
        chatSessionRepository.save(session);
    }
}


```

```java
package com.chatbi.service;

import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    public List<ChatMessage> listMessages(ChatSession session) {
        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    public ChatMessage appendUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }
}


```

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.UserToken;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    private String requireUserId(String loginToken) {
        UserToken parsed = TokenInterceptor.parseUserTokenFromJson(loginToken);
        if (parsed == null || parsed.getUserId() == null) {
            throw new IllegalArgumentException("缺少有效的用户标识");
        }
        return parsed.getUserId();
    }

    @GetMapping
    @EnableAuth
    public ResponseEntity<List<ChatSession>> listSessions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        String userId = requireUserId(loginToken);
        return ResponseEntity.ok(chatSessionService.listSessions(userId));
    }

    @PostMapping
    @EnableAuth
    public ResponseEntity<ChatSession> createSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body != null ? body.getOrDefault("title", null) : null;
        ChatSession created = chatSessionService.createSession(userId, title);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> renameSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body.get("title");
        Optional<ChatSession> updated = chatSessionService.renameSession(id, userId, title);
        return updated.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "会话不存在")));
    }

    @DeleteMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> deleteSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        boolean ok = chatSessionService.archiveSession(id, userId);
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "会话已删除"));
        } else {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
    }

    @GetMapping("/{id}/messages")
    @EnableAuth
    public ResponseEntity<?> listMessages(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        Optional<ChatSession> optional = chatSessionService.getByIdForUser(id, userId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
        List<ChatMessage> messages = chatMessageService.listMessages(optional.get());
        return ResponseEntity.ok(messages);
    }
}


```

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.*;
import com.chatbi.service.ChatService;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.DatabaseAdminService;
import com.chatbi.service.DatabaseManager;
import com.chatbi.service.SchemaMetadataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private DatabaseAdminService databaseAdminService;

    @Autowired
    private SchemaMetadataBuilder metadataBuilder;

    @Autowired
    private com.chatbi.service.UserWhitelistService userWhitelistService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "message", "ChatBI API Server",
                "version", "1.0.0",
                "status", "running"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "database", "connected",
                    "tables_count", tables.size()
            ));
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> legacyHealthCheck() {
        return healthCheck();
    }

    @PostMapping("/chat")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @Valid @RequestBody ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}",
                    request.getConversationId(), request.getMessage());

            // Resolve userId from token
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);
            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.status(403).body(new ChatResponse(
                        "用户未认证或缺少userId", null, null,
                        request.getConversationId(), null, null
                ));
            }

            // Resolve or create session
            Long sessionId = null;
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                try {
                    sessionId = Long.parseLong(request.getConversationId());
                } catch (NumberFormatException ignored) {
                    sessionId = null;
                }
            }

            ChatSession session;
            if (sessionId == null) {
                String title = request.getMessage();
                if (title != null && title.length() > 50) {
                    title = title.substring(0, 50);
                }
                session = chatSessionService.createSession(userToken.getUserId(), title);
            } else {
                session = chatSessionService.getByIdForUser(sessionId, userToken.getUserId())
                        .orElseGet(() -> chatSessionService.createSession(userToken.getUserId(), null));
            }

            // Save user message
            chatMessageService.appendUserMessage(session, request.getMessage());

            // Delegate to existing chat pipeline to get response content
            ChatResponse response = chatService.processChatMessage(request);

            // Save assistant message
            if (response != null && response.getMessage() != null) {
                chatMessageService.appendAssistantMessage(session, response.getMessage());
            }

            // Ensure response carries session id as conversation_id
            ChatResponse finalResponse = new ChatResponse(
                    response.getMessage(),
                    response.getSqlQuery(),
                    response.getSemanticSQL(),
                    String.valueOf(session.getId()),
                    response.getExecutionResult(),
                    response.getDebugOllama()
            );

            return ResponseEntity.ok(finalResponse);
        } catch (Exception e) {
            logger.error("/api/chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("处理聊天请求时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/execute-sql")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<SQLExecutionResponse> executeSql(@Valid @RequestBody SQLExecutionRequest request) {
        try {
            // Get database connection
            DatabaseConnection selectedConnection = null;
            if (request.getDatabaseConnectionId() != null && !request.getDatabaseConnectionId().trim().isEmpty()) {
                selectedConnection = databaseAdminService.getConnection(request.getDatabaseConnectionId())
                        .orElse(null);
                if (selectedConnection == null) {
                    logger.warn("Database connection not found: {}", request.getDatabaseConnectionId());
                }
            }

            // Execute SQL query
            SQLExecutionResponse result;
            if (selectedConnection != null) {
                result = databaseManager.executeQuery(request.getSqlQuery(), selectedConnection);
            } else {
                result = databaseManager.executeQuery(request.getSqlQuery());
            }

            // Update conversation history if conversation ID is provided
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                chatService.executeSqlAndUpdateResponse(request.getConversationId(), request.getSqlQuery());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", e.getMessage(), e);
            throw new RuntimeException("执行SQL时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversationHistory(@PathVariable String conversationId) {
        try {
            List<Map<String, Object>> history = chatService.getConversationHistory(conversationId);
            return ResponseEntity.ok(Map.of(
                    "conversation_id", conversationId,
                    "history", history
            ));
        } catch (Exception e) {
            logger.error("Error getting conversation history: {}", e.getMessage(), e);
            throw new RuntimeException("获取会话历史时发生错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            chatService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of("message", "会话历史已清除"));
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);
            throw new RuntimeException("清除会话历史时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ResponseEntity<Map<String, List<String>>> getTables() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            logger.error("Error getting tables: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库表时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables/{tableName}/schema")
    public ResponseEntity<Map<String, Object>> getTableSchema(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> schema = databaseManager.getTableSchema(tableName);
            return ResponseEntity.ok(Map.of(
                    "table_name", tableName,
                    "schema", schema
            ));
        } catch (Exception e) {
            logger.error("Error getting table schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/schema")
    public ResponseEntity<Map<String, Object>> getFullDatabaseSchema(
            @RequestParam(required = false) String connectionId) {
        try {
            List<String> tables;
            Map<String, Object> schema = new java.util.HashMap<>();

            if (connectionId != null && !connectionId.trim().isEmpty()) {
                // Use specific database connection
                Optional<DatabaseConnection> connection = databaseAdminService.getConnection(connectionId);
                if (connection.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                tables = databaseAdminService.getTables(connectionId).stream()
                        .map(TableInfo::getTableName)
                        .collect(java.util.stream.Collectors.toList());

                for (String table : tables) {
                    TableSchema tableSchema = databaseAdminService.getTableSchema(connectionId, table);
                    schema.put(table, tableSchema.getColumns());
                }
            } else {
                // Use active connection (default behavior)
                tables = databaseManager.getAllTables();
                for (String table : tables) {
                    schema.put(table, databaseManager.getTableSchema(table));
                }
            }

            return ResponseEntity.ok(Map.of("database_schema", schema));
        } catch (Exception e) {
            logger.error("Error getting full database schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取完整数据库结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getEnrichedMetadata() {
        try {
            Map<String, Object> metadata = metadataBuilder.buildDatabaseMetadata(null);
            return ResponseEntity.ok(Map.of("metadata", metadata));
        } catch (Exception e) {
            logger.error("Error getting metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/user/permissions")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        try {
            // 从请求属性中获取用户信息（由 TokenInterceptor 设置）
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);

            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.ok(Map.of(
                        "hasDatabaseAccess", false,
                        "canDeleteDatabase", false,
                        "role", "READER"
                ));
            }

            // 获取用户角色
            String role = userWhitelistService.getUserRole(userToken.getUserId());
            if (role == null) {
                role = "READER"; // 默认角色
            }

            // 判断权限
            boolean hasDatabaseAccess = "ADMIN".equals(role) || "OPERATOR".equals(role);
            boolean canDeleteDatabase = "ADMIN".equals(role);

            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", hasDatabaseAccess,
                    "canDeleteDatabase", canDeleteDatabase,
                    "role", role
            ));
        } catch (Exception e) {
            logger.error("Error getting user permissions: {}", e.getMessage(), e);
            // 发生错误时返回默认权限（最严格）
            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", false,
                    "canDeleteDatabase", false,
                    "role", "READER"
            ));
        }
    }
}
```

```typescript
export interface SemanticSQL {
  tables: string[];
  columns: string[];
  conditions: Array<{
    column: string;
    operator: string;
    value: any;
    table?: string;
  }>;
  aggregations: Array<{
    function: string;
    column: string;
    alias?: string;
  }>;
  joins: Array<{
    type: string;
    table1: string;
    table2: string;
    condition: string;
  }>;
  order_by?: Array<{
    column: string;
    direction: string;
  }>;
  group_by?: string[];
  limit?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  semantic_sql?: SemanticSQL;
  sql_query?: string;
  execution_result?: SQLExecutionResult;
  debug_info?: {
    request?: ChatRequest;
    response?: ChatResponse;
    ollama?: Record<string, any>;
    sql_execution?: SQLExecutionResult | null;
  };
}

export interface ChatRequest {
  message: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface ChatResponse {
  response: string;
  sql_query?: string;
  semantic_sql?: SemanticSQL;
  conversation_id: string;
  execution_result?: SQLExecutionResult;
  debug_ollama?: Record<string, any>;
}

export interface SQLExecutionRequest {
  sql_query: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface SQLExecutionResult {
  success: boolean;
  data?: Array<Record<string, any>>;
  error?: string;
  row_count?: number;
}

export interface DatabaseSchema {
  [tableName: string]: Array<{
    Field: string;
    Type: string;
    Null: string;
    Key: string;
    Default: any;
    Extra: string;
  }>;
}

// 数据库后台管理相关类型
export interface DatabaseConnection {
  id?: string;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  database_name: string;
  charset_name: string;
  description?: string;
  is_active: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface DatabaseConnectionCreate {
  name: string;
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
  description?: string;
}

export interface DatabaseConnectionUpdate {
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  database_name?: string;
  charset_name?: string;
  description?: string;
  is_active?: boolean;
}

export interface DatabaseConnectionTest {
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
}

export interface TableInfo {
  table_name: string;
  table_comment?: string;
  table_rows?: number;
  table_size?: string;
  engine?: string;
  charset_name?: string;
}

export interface ColumnInfo {
  column_name: string;
  data_type: string;
  is_nullable: boolean;
  column_key?: string;
  column_default?: string;
  extra?: string;
  column_comment?: string;
  column_order: number;
}

export interface TableSchema {
  table_name: string;
  table_comment?: string;
  columns: ColumnInfo[];
}

export interface CommentUpdate {
  table_name: string;
  column_name?: string;
  comment: string;
}

export interface UserToken {
  userId?: string;
  userName?: string;
  roleNames?: string[];
}

// 会话与消息（持久化）
export interface PersistedChatSession {
  id: number;
  userId: string;
  title?: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PersistedChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'system';
  content: string;
  createdAt: string;
}

```

```typescript
import axios from 'axios';
import { 
  ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResult, DatabaseSchema,
  DatabaseConnection, DatabaseConnectionCreate, DatabaseConnectionUpdate, DatabaseConnectionTest,
  TableInfo, TableSchema, CommentUpdate,
  PersistedChatSession, PersistedChatMessage
} from '../types';
import { setLoginToken, getLoginToken } from '../utils/cookie';

const API_BASE_URL = 'http://localhost:8000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000, // 增加到2分钟，适应大模型请求时间
  headers: {
    'Content-Type': 'application/json',
  },
});

const sampleToken = {
  userId: 'user3',
  userName: 'user3',
  roleNames: ['ADMIN', 'READER', 'OPERATOR']
};
setLoginToken(sampleToken);
console.log('样例token已设置:', sampleToken);

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 从cookie中获取login_token（已经是base64编码的UserToken JSON）
    // 直接发送base64编码的字符串
    const loginToken = getLoginToken();
    if (loginToken) {
      config.headers['Login-Token'] = loginToken;
    } else {
      // 即使为空也添加header
      config.headers['Login-Token'] = '';
    }
    
    console.log('API Request:', config.method?.toUpperCase(), config.url, 'Login-Token:', loginToken ? '(base64 encoded)' : '(empty)');
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 权限错误处理函数（将在 App.tsx 中设置）
let handleAuthError: ((status: 401 | 403) => void) | null = null;

export const setAuthErrorHandler = (handler: (status: 401 | 403) => void) => {
  handleAuthError = handler;
};

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
    
    // 处理 401/403 权限错误
    const status = error.response?.status;
    if (status === 401 || status === 403) {
      if (handleAuthError) {
        handleAuthError(status as 401 | 403);
      }
    }
    
    return Promise.reject(error);
  }
);

export const chatAPI = {
  // 发送聊天消息
  sendMessage: async (request: ChatRequest): Promise<ChatResponse> => {
    const response = await api.post('/chat', request);
    return response.data;
  },

  // 执行SQL查询
  executeSQL: async (request: SQLExecutionRequest): Promise<SQLExecutionResult> => {
    const response = await api.post('/execute-sql', request);
    return response.data;
  },

  // 获取会话历史
  getConversationHistory: async (conversationId: string) => {
    const response = await api.get(`/conversation/${conversationId}`);
    return response.data;
  },

  // 清除会话历史
  clearConversation: async (conversationId: string) => {
    const response = await api.delete(`/conversation/${conversationId}`);
    return response.data;
  },
};

// 会话与历史 API
export const sessionAPI = {
  listSessions: async (): Promise<PersistedChatSession[]> => {
    const res = await api.get('/sessions');
    return res.data;
  },
  createSession: async (title?: string): Promise<PersistedChatSession> => {
    const res = await api.post('/sessions', title ? { title } : {});
    return res.data;
  },
  renameSession: async (id: number, title: string): Promise<PersistedChatSession> => {
    const res = await api.patch(`/sessions/${id}`, { title });
    return res.data;
  },
  deleteSession: async (id: number): Promise<void> => {
    await api.delete(`/sessions/${id}`);
  },
  getSessionMessages: async (id: number): Promise<PersistedChatMessage[]> => {
    const res = await api.get(`/sessions/${id}/messages`);
    return res.data;
  },
};

export const databaseAPI = {
  // 获取所有表
  getTables: async (): Promise<string[]> => {
    const response = await api.get('/database/tables');
    return response.data.tables;
  },

  // 获取表结构
  getTableSchema: async (tableName: string) => {
    const response = await api.get(`/database/tables/${tableName}/schema`);
    return response.data;
  },

  // 获取完整数据库结构
  getFullDatabaseSchema: async (connectionId?: string): Promise<DatabaseSchema> => {
    const params = connectionId ? { connectionId } : {};
    const response = await api.get('/database/schema', { params });
    return response.data.database_schema;
  },
};

export const systemAPI = {
  // 健康检查
  healthCheck: async () => {
    const response = await api.get('/health', { timeout: 10000 });
    return response.data;
  },
  
  // 获取用户权限
  getUserPermissions: async (): Promise<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }> => {
    const response = await api.get('/user/permissions');
    return response.data;
  },
};

// 数据库后台管理API
export const databaseAdminAPI = {
  // 数据库连接管理
  getConnections: async (): Promise<DatabaseConnection[]> => {
    const response = await api.get('/admin/databases');
    return response.data;
  },

  getActiveConnection: async (): Promise<DatabaseConnection | null> => {
    try {
      const response = await api.get('/admin/databases/active');
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  createConnection: async (connection: DatabaseConnectionCreate): Promise<DatabaseConnection> => {
    const response = await api.post('/admin/databases', connection);
    return response.data;
  },

  getConnection: async (connectionId: string): Promise<DatabaseConnection> => {
    const response = await api.get(`/admin/databases/${connectionId}`);
    return response.data;
  },

  updateConnection: async (connectionId: string, update: DatabaseConnectionUpdate): Promise<DatabaseConnection> => {
    const response = await api.put(`/admin/databases/${connectionId}`, update);
    return response.data;
  },

  deleteConnection: async (connectionId: string): Promise<void> => {
    await api.delete(`/admin/databases/${connectionId}`);
  },

  testConnection: async (testData: DatabaseConnectionTest): Promise<{ success: boolean; message: string; version?: string }> => {
    const response = await api.post('/admin/databases/test', testData);
    return response.data;
  },

  // 数据库元数据管理
  getTables: async (connectionId: string): Promise<TableInfo[]> => {
    const response = await api.get(`/admin/databases/${connectionId}/tables`);
    return response.data;
  },

  getTableSchema: async (connectionId: string, tableName: string): Promise<TableSchema> => {
    const response = await api.get(`/admin/databases/${connectionId}/tables/${tableName}/schema`);
    return response.data;
  },

  updateComment: async (connectionId: string, commentUpdate: CommentUpdate): Promise<void> => {
    await api.put(`/admin/databases/${connectionId}/comments`, commentUpdate);
  },

  executeSQL: async (connectionId: string, sql: string): Promise<{ success: boolean; data?: any; error?: string; row_count?: number }> => {
    const response = await api.post(`/admin/databases/${connectionId}/execute-sql`, { sql });
    return response.data;
  },
};

export default api;

```

```typescriptreact
import React, { useEffect, useState } from 'react';
import { List, Button, Input, Modal, Space, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { PersistedChatSession } from '../types';
import { sessionAPI } from '../services/api';

interface SessionListProps {
  selectedSessionId?: number | null;
  onSelect: (sessionId: number) => void;
}

const SessionList: React.FC<SessionListProps> = ({ selectedSessionId, onSelect }) => {
  const [sessions, setSessions] = useState<PersistedChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [renaming, setRenaming] = useState<PersistedChatSession | null>(null);
  const [newTitle, setNewTitle] = useState('');

  const loadSessions = async () => {
    setLoading(true);
    try {
      const list = await sessionAPI.listSessions();
      setSessions(list);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const handleCreate = async () => {
    const created = await sessionAPI.createSession('新的会话');
    setSessions(prev => [created, ...prev]);
    onSelect(created.id);
  };

  const handleRename = async () => {
    if (!renaming) return;
    const updated = await sessionAPI.renameSession(renaming.id, newTitle || renaming.title || '会话');
    setSessions(prev => prev.map(s => (s.id === updated.id ? updated : s)));
    setRenaming(null);
    setNewTitle('');
  };

  const handleDelete = async (id: number) => {
    await sessionAPI.deleteSession(id);
    setSessions(prev => prev.filter(s => s.id !== id));
  };

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建会话</Button>
      </Space>
      <List
        loading={loading}
        dataSource={sessions}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', background: selectedSessionId === item.id ? '#e6f7ff' : undefined }}
            onClick={() => onSelect(item.id)}
            actions={[
              <Button key="rename" type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setRenaming(item); setNewTitle(item.title || ''); }} />,
              <Button key="delete" type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); handleDelete(item.id); }} />,
            ]}
          >
            <List.Item.Meta
              title={<Typography.Text ellipsis>{item.title || '未命名会话'}</Typography.Text>}
              description={new Date(item.updatedAt).toLocaleString()}
            />
          </List.Item>
        )}
      />

      <Modal
        title="重命名会话"
        open={!!renaming}
        onOk={handleRename}
        onCancel={() => { setRenaming(null); setNewTitle(''); }}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="请输入会话标题"
          maxLength={50}
        />
      </Modal>
    </div>
  );
};

export default SessionList;


```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Alert, Button } from 'antd';
import { ReloadOutlined, DatabaseOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, DatabaseConnection, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  const [systemError, setSystemError] = useState<string | null>(null);
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [refreshDatabaseSchema, setRefreshDatabaseSchema] = useState(0);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [availableConnections, setAvailableConnections] = useState<DatabaseConnection[]>([]);
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      setSystemError(null);
    } catch (error: any) {
      setSystemStatus('error');
      setSystemError(error.message || '系统连接失败');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      setAvailableConnections(connections);
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 触发数据库结构刷新
    setRefreshDatabaseSchema(prev => prev + 1);
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
  };

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    // 触发数据库结构刷新
    setRefreshDatabaseSchema(prev => prev + 1);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
    </Layout>
  );
};

export default App;

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, DatabaseConnection, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    // 触发数据库结构刷新
    setRefreshDatabaseSchema(prev => prev + 1);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
    </Layout>
  );
};

export default App;

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
    </Layout>
  );
};

export default App;

```

I added the DB schema for sessions/messages, then implemented backend entities, repos, services, and controllers, followed by frontend types, API methods, a session sidebar, and wiring to load/replay histories. Next, I updated the chat flow to persist per-user sessions and remember the last session.

- Backend
  - Added `chat_session` and `chat_message` tables to both `chatbi-server-java/src/main/resources/schema.sql` and `db-init/init-system.sql`.
  - Created JPA entities:
    - `chatbi-server-java/src/main/java/com/chatbi/model/ChatSession.java`
    - `chatbi-server-java/src/main/java/com/chatbi/model/ChatMessage.java`
  - Created repositories:
    - `chatbi-server-java/src/main/java/com/chatbi/repository/ChatSessionRepository.java`
    - `chatbi-server-java/src/main/java/com/chatbi/repository/ChatMessageRepository.java`
  - Implemented services:
    - `chatbi-server-java/src/main/java/com/chatbi/service/ChatSessionService.java`
    - `chatbi-server-java/src/main/java/com/chatbi/service/ChatMessageService.java`
  - Added session APIs:
    - `chatbi-server-java/src/main/java/com/chatbi/controller/SessionController.java`
      - GET `/api/sessions`, POST `/api/sessions`, PATCH `/api/sessions/{id}`, DELETE `/api/sessions/{id}`, GET `/api/sessions/{id}/messages`
  - Updated chat endpoint to bind to sessions and persist messages:
    - `chatbi-server-java/src/main/java/com/chatbi/controller/ChatController.java`
    - Accepts header `Login-Token`, resolves `userId`, creates/fetches session, stores user and assistant messages; returns `conversation_id` as the numeric session id.

- Frontend
  - Types extended in `chatbi-ui/src/types/index.ts` with `PersistedChatSession` and `PersistedChatMessage`.
  - API added in `chatbi-ui/src/services/api.ts` via `sessionAPI` with CRUD and history methods.
  - New sidebar component `chatbi-ui/src/components/SessionList.tsx` for session management (create/rename/delete/select).
  - Wired session usage in `chatbi-ui/src/App.tsx`:
    - Maintains `currentSessionId`, sets `conversation_id` when sending.
    - On first reply, syncs `conversation_id` from server to `currentSessionId`.
    - Loads session history through `sessionAPI.getSessionMessages`.
    - Persists last session id in `localStorage` and restores it on app load.
    - Replaced left sidebar content with `SessionList`.

Note:
- For now, frontend uses the existing `conversation_id` field which now carries the numeric session id string. No other UI changes are required to the chat message rendering.
- You can see the new session management in the left sidebar: create, rename, delete, and select sessions; selecting loads the full history.

- Completed todos
  - Add chat_session and chat_message tables and indexes
  - Create ChatSession and ChatMessage models with fields
  - Add ChatSessionRepository and ChatMessageRepository
  - Implement ChatSessionService and ChatMessageService with user checks
  - Add SessionController with CRUD and history APIs
  - Update ChatController to accept sessionId and persist messages
  - Add ChatSession and ChatMessage types to types/index.ts
  - Add session CRUD and history methods; extend sendMessage to sessionId
  - Create SessionList sidebar with CRUD and selection
  - Wire App/ChatInput/ChatMessage to use sessionId and load history
  - Persist last sessionId in localStorage and bootstrap selection

---

**User**

@ChatController.java 存在编译错误

---

**Cursor**

I'll open related backend classes to locate the compile error in ChatController.java, particularly the ChatResponse API used.

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.*;
import com.chatbi.service.ChatService;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.DatabaseAdminService;
import com.chatbi.service.DatabaseManager;
import com.chatbi.service.SchemaMetadataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private DatabaseAdminService databaseAdminService;

    @Autowired
    private SchemaMetadataBuilder metadataBuilder;

    @Autowired
    private com.chatbi.service.UserWhitelistService userWhitelistService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "message", "ChatBI API Server",
                "version", "1.0.0",
                "status", "running"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "database", "connected",
                    "tables_count", tables.size()
            ));
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> legacyHealthCheck() {
        return healthCheck();
    }

    @PostMapping("/chat")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @Valid @RequestBody ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}",
                    request.getConversationId(), request.getMessage());

            // Resolve userId from token
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);
            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.status(403).body(new ChatResponse(
                        "用户未认证或缺少userId", null, null,
                        request.getConversationId(), null, null
                ));
            }

            // Resolve or create session
            Long sessionId = null;
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                try {
                    sessionId = Long.parseLong(request.getConversationId());
                } catch (NumberFormatException ignored) {
                    sessionId = null;
                }
            }

            ChatSession session;
            if (sessionId == null) {
                String title = request.getMessage();
                if (title != null && title.length() > 50) {
                    title = title.substring(0, 50);
                }
                session = chatSessionService.createSession(userToken.getUserId(), title);
            } else {
                session = chatSessionService.getByIdForUser(sessionId, userToken.getUserId())
                        .orElseGet(() -> chatSessionService.createSession(userToken.getUserId(), null));
            }

            // Save user message
            chatMessageService.appendUserMessage(session, request.getMessage());

            // Delegate to existing chat pipeline to get response content
            ChatResponse response = chatService.processChatMessage(request);

            // Save assistant message
            if (response != null && response.getResponse() != null) {
                chatMessageService.appendAssistantMessage(session, response.getResponse());
            }

            // Ensure response carries session id as conversation_id
            ChatResponse finalResponse = new ChatResponse(
                    response.getResponse(),
                    response.getSqlQuery(),
                    response.getSemanticSql(),
                    String.valueOf(session.getId()),
                    response.getExecutionResult(),
                    response.getDebugOllama()
            );

            return ResponseEntity.ok(finalResponse);
        } catch (Exception e) {
            logger.error("/api/chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("处理聊天请求时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/execute-sql")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<SQLExecutionResponse> executeSql(@Valid @RequestBody SQLExecutionRequest request) {
        try {
            // Get database connection
            DatabaseConnection selectedConnection = null;
            if (request.getDatabaseConnectionId() != null && !request.getDatabaseConnectionId().trim().isEmpty()) {
                selectedConnection = databaseAdminService.getConnection(request.getDatabaseConnectionId())
                        .orElse(null);
                if (selectedConnection == null) {
                    logger.warn("Database connection not found: {}", request.getDatabaseConnectionId());
                }
            }

            // Execute SQL query
            SQLExecutionResponse result;
            if (selectedConnection != null) {
                result = databaseManager.executeQuery(request.getSqlQuery(), selectedConnection);
            } else {
                result = databaseManager.executeQuery(request.getSqlQuery());
            }

            // Update conversation history if conversation ID is provided
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                chatService.executeSqlAndUpdateResponse(request.getConversationId(), request.getSqlQuery());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", e.getMessage(), e);
            throw new RuntimeException("执行SQL时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversationHistory(@PathVariable String conversationId) {
        try {
            List<Map<String, Object>> history = chatService.getConversationHistory(conversationId);
            return ResponseEntity.ok(Map.of(
                    "conversation_id", conversationId,
                    "history", history
            ));
        } catch (Exception e) {
            logger.error("Error getting conversation history: {}", e.getMessage(), e);
            throw new RuntimeException("获取会话历史时发生错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            chatService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of("message", "会话历史已清除"));
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);
            throw new RuntimeException("清除会话历史时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ResponseEntity<Map<String, List<String>>> getTables() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            logger.error("Error getting tables: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库表时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables/{tableName}/schema")
    public ResponseEntity<Map<String, Object>> getTableSchema(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> schema = databaseManager.getTableSchema(tableName);
            return ResponseEntity.ok(Map.of(
                    "table_name", tableName,
                    "schema", schema
            ));
        } catch (Exception e) {
            logger.error("Error getting table schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/schema")
    public ResponseEntity<Map<String, Object>> getFullDatabaseSchema(
            @RequestParam(required = false) String connectionId) {
        try {
            List<String> tables;
            Map<String, Object> schema = new java.util.HashMap<>();

            if (connectionId != null && !connectionId.trim().isEmpty()) {
                // Use specific database connection
                Optional<DatabaseConnection> connection = databaseAdminService.getConnection(connectionId);
                if (connection.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                tables = databaseAdminService.getTables(connectionId).stream()
                        .map(TableInfo::getTableName)
                        .collect(java.util.stream.Collectors.toList());

                for (String table : tables) {
                    TableSchema tableSchema = databaseAdminService.getTableSchema(connectionId, table);
                    schema.put(table, tableSchema.getColumns());
                }
            } else {
                // Use active connection (default behavior)
                tables = databaseManager.getAllTables();
                for (String table : tables) {
                    schema.put(table, databaseManager.getTableSchema(table));
                }
            }

            return ResponseEntity.ok(Map.of("database_schema", schema));
        } catch (Exception e) {
            logger.error("Error getting full database schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取完整数据库结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getEnrichedMetadata() {
        try {
            Map<String, Object> metadata = metadataBuilder.buildDatabaseMetadata(null);
            return ResponseEntity.ok(Map.of("metadata", metadata));
        } catch (Exception e) {
            logger.error("Error getting metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/user/permissions")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        try {
            // 从请求属性中获取用户信息（由 TokenInterceptor 设置）
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);

            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.ok(Map.of(
                        "hasDatabaseAccess", false,
                        "canDeleteDatabase", false,
                        "role", "READER"
                ));
            }

            // 获取用户角色
            String role = userWhitelistService.getUserRole(userToken.getUserId());
            if (role == null) {
                role = "READER"; // 默认角色
            }

            // 判断权限
            boolean hasDatabaseAccess = "ADMIN".equals(role) || "OPERATOR".equals(role);
            boolean canDeleteDatabase = "ADMIN".equals(role);

            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", hasDatabaseAccess,
                    "canDeleteDatabase", canDeleteDatabase,
                    "role", role
            ));
        } catch (Exception e) {
            logger.error("Error getting user permissions: {}", e.getMessage(), e);
            // 发生错误时返回默认权限（最严格）
            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", false,
                    "canDeleteDatabase", false,
                    "role", "READER"
            ));
        }
    }
}
```

已修复。原因是 `ChatResponse` 的字段/方法名与控制器使用不一致：

- ChatResponse 使用 `response` 而非 `message`
- 语义 SQL 字段为 `semanticSql`（小写 l），对应 `getSemanticSql()`

我已在 `ChatController.java` 中将以下调用全部纠正：
- `response.getMessage()` → `response.getResponse()`
- `response.getSemanticSQL()` → `response.getSemanticSql()`

关键变更位置：
```142:149:chatbi-server-java/src/main/java/com/chatbi/controller/ChatController.java
// Save assistant message
if (response != null && response.getResponse() != null) {
    chatMessageService.appendAssistantMessage(session, response.getResponse());
}
```

```132:141:chatbi-server-java/src/main/java/com/chatbi/controller/ChatController.java
ChatResponse finalResponse = new ChatResponse(
        response.getResponse(),
        response.getSqlQuery(),
        response.getSemanticSql(),
        String.valueOf(session.getId()),
        response.getExecutionResult(),
        response.getDebugOllama()
);
```

现在该文件无编译/静态检查错误。

```java
// Save assistant message
if (response != null && response.getResponse() != null) {
    chatMessageService.appendAssistantMessage(session, response.getResponse());
}
```

```java
ChatResponse finalResponse = new ChatResponse(
        response.getResponse(),
        response.getSqlQuery(),
        response.getSemanticSql(),
        String.valueOf(session.getId()),
        response.getExecutionResult(),
        response.getDebugOllama()
);
```

---

**User**

前端的调整，请把之前的数据库结构放到对话右边

---

**Cursor**

I'll update the chat layout to place the `DatabaseSchema` panel on the right side: reintroduce `DatabaseSchema` import, add `handleSelectTable`, and render a right-hand `Sider` beside the chat content.

```typescriptreact
*** Begin Patch
*** Update File: /Users/fayespica/git/FayeSpica/chat-bi-ai-poc/chatbi-ui/src/App.tsx
@@
-import ChatInput from './components/ChatInput';
-import DatabaseSchema from './components/DatabaseSchema';
+import ChatInput from './components/ChatInput';
+import DatabaseSchema from './components/DatabaseSchema';
@@
-import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
+import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
@@
-  
+  
@@
-  
+  
+  const handleSelectTable = (tableName: string) => {
+    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
+    handleSendMessage(suggestionMessage);
+  };
@@
-        <Layout>
-          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
+        <Layout>
+          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
             <div className="chat-messages" style={{ flex: 1 }}>
               {messages.map((msg) => (
                 <ChatMessage
                   key={msg.id}
                   message={msg}
                   onExecuteSQL={handleExecuteSQL}
                   onSendMessage={handleSendMessage}
                   isExecuting={isExecuting}
                 />
               ))}
               
               {isLoading && (
                 <div className="message-item message-assistant">
                   <div className="message-content">
                     <div className="loading-dots">正在思考中</div>
                   </div>
                 </div>
               )}
               
               <div ref={messagesEndRef} />
             </div>
 
             <ChatInput
               onSendMessage={handleSendMessage}
               onClearChat={handleClearChat}
               disabled={isLoading || systemStatus !== 'healthy'}
             />
           </Content>
+          <Sider 
+            width={360}
+            collapsedWidth={0}
+            collapsible={false}
+            style={{ background: '#fff', borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}
+          >
+            <DatabaseSchema 
+              onSelectTable={handleSelectTable}
+              selectedDatabaseId={selectedDatabaseId}
+            />
+          </Sider>
         </Layout>
*** End Patch
```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
  };

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
          <Sider 
            width={360}
            collapsedWidth={0}
            collapsible={false}
            style={{ background: '#fff', borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}
          >
            <DatabaseSchema 
              onSelectTable={handleSelectTable}
              selectedDatabaseId={selectedDatabaseId}
            />
          </Sider>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
    </Layout>
  );
};

export default App;

```

已把数据库结构面板移动到对话区域的右侧：

- 在 `chatbi-ui/src/App.tsx` 中重新引入 `DatabaseSchema`，新增 `handleSelectTable`，并在中间的内层 `Layout` 中让 `Content`（聊天区）在左，新增一个右侧 `Sider` 展示 `DatabaseSchema`。
- 左侧保持为会话列表 `SessionList`，右侧为数据库结构，聊天区居中。

关键布局位置：
```413:444:chatbi-ui/src/App.tsx
<Layout>
  <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
    {/* 聊天消息 + 输入框 */}
  </Content>
  <Sider 
    width={360}
    collapsedWidth={0}
    collapsible={false}
    style={{ background: '#fff', borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}
  >
    <DatabaseSchema 
      onSelectTable={handleSelectTable}
      selectedDatabaseId={selectedDatabaseId}
    />
  </Sider>
</Layout>
```

- 右侧面板点击表名时，会自动在聊天框中注入“请帮我生成该表查询语句”的提示，继续当前会话。

```typescriptreact
<Layout>
  <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
    {/* 聊天消息 + 输入框 */}
  </Content>
  <Sider 
    width={360}
    collapsedWidth={0}
    collapsible={false}
    style={{ background: '#fff', borderLeft: '1px solid #f0f0f0', overflow: 'auto' }}
  >
    <DatabaseSchema 
      onSelectTable={handleSelectTable}
      selectedDatabaseId={selectedDatabaseId}
    />
  </Sider>
</Layout>
```

---

**User**

数据库结构从上方 数据库选择的位置 展开

---

**Cursor**

I'll update the chat UI to display the database schema in a top Drawer that opens from the Database Selector area: add a toggle button near the selector, implement a top Drawer with `DatabaseSchema`, remove the right-side panel previously added, and close the drawer after a table selection.

```typescriptreact
*** Begin Patch
*** Update File: /Users/fayespica/git/FayeSpica/chat-bi-ai-poc/chatbi-ui/src/App.tsx
@@
-import { Layout, message, Spin, Button } from 'antd';
+import { Layout, message, Spin, Button, Drawer } from 'antd';
@@
-import DatabaseAdmin from './components/DatabaseAdmin';
-import DatabaseSelector from './components/DatabaseSelector';
+import DatabaseAdmin from './components/DatabaseAdmin';
+import DatabaseSelector from './components/DatabaseSelector';
 import SessionList from './components/SessionList';
 import AccessDenied from './components/AccessDenied';
+import DatabaseSchema from './components/DatabaseSchema';
@@
   const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
   const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
   const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
     hasDatabaseAccess: false,
     canDeleteDatabase: false,
     role: 'READER'
   });
   const messagesEndRef = useRef<HTMLDivElement>(null);
+  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
@@
   const handleClearChat = async () => {
@@
   };
 
   
 
   const handleDatabaseAdminClose = () => {
@@
   };
+
+  const handleSelectTable = (tableName: string) => {
+    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
+    handleSendMessage(suggestionMessage);
+    setShowSchemaDrawer(false);
+  };
@@
-          <DatabaseSelector
-            selectedDatabaseId={selectedDatabaseId}
-            onDatabaseChange={handleDatabaseChange}
-            disabled={isLoading || systemStatus !== 'healthy'}
-          />
+          <DatabaseSelector
+            selectedDatabaseId={selectedDatabaseId}
+            onDatabaseChange={handleDatabaseChange}
+            disabled={isLoading || systemStatus !== 'healthy'}
+          />
+          <Button
+            onClick={() => setShowSchemaDrawer(true)}
+            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
+          >
+            数据库结构
+          </Button>
           {userPermissions.hasDatabaseAccess && (
             <Button 
               type="primary" 
               icon={<SettingOutlined />}
               onClick={() => setShowDatabaseAdmin(true)}
             >
               数据库管理
             </Button>
           )}
@@
-        <Layout>
-          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
+        <Layout>
+          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
             <div className="chat-messages" style={{ flex: 1 }}>
@@
             <ChatInput
               onSendMessage={handleSendMessage}
               onClearChat={handleClearChat}
               disabled={isLoading || systemStatus !== 'healthy'}
             />
           </Content>
-        </Layout>
+        </Layout>
       </Layout>
@@
       {showDatabaseAdmin && (
         <DatabaseAdmin 
           onClose={handleDatabaseAdminClose}
           canDeleteDatabase={userPermissions.canDeleteDatabase}
         />
       )}
+      <Drawer
+        placement="top"
+        height={420}
+        open={showSchemaDrawer}
+        onClose={() => setShowSchemaDrawer(false)}
+        destroyOnClose
+        title="数据库结构"
+      >
+        <DatabaseSchema 
+          onSelectTable={handleSelectTable}
+          selectedDatabaseId={selectedDatabaseId}
+        />
+      </Drawer>
     </Layout>
   );
*** End Patch
```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
  };

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="top"
        height={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="top"
        height={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已按你的要求将“数据库结构”改为从上方（数据库选择处）展开：

- 在 `App.tsx` 的顶部右侧区域（`DatabaseSelector`旁）新增一个“数据库结构”按钮，点击后从页面顶部弹出 Drawer。
- Drawer 使用 `placement="top"`，包含 `DatabaseSchema` 组件，关闭时销毁内容，避免卡顿。
- 点击表名会自动向当前会话注入“生成该表查询语句”的提示，并自动关闭 Drawer。
- 之前新增的右侧 `Sider` 数据库结构面板已移除，界面保持为：左侧会话列表，中间聊天，顶部弹出数据库结构。

关键改动：
- 头部按钮与 Drawer:
```360:377:chatbi-ui/src/App.tsx
<DatabaseSelector ... />
<Button 
  onClick={() => setShowSchemaDrawer(true)}
  disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
>
  数据库结构
</Button>
```

```470:486:chatbi-ui/src/App.tsx
<Drawer
  placement="top"
  height={420}
  open={showSchemaDrawer}
  onClose={() => setShowSchemaDrawer(false)}
  destroyOnClose
  title="数据库结构"
>
  <DatabaseSchema 
    onSelectTable={handleSelectTable}
    selectedDatabaseId={selectedDatabaseId}
  />
</Drawer>
```

- 表点击回调（会关闭 Drawer）:
```280:284:chatbi-ui/src/App.tsx
const handleSelectTable = (tableName: string) => {
  const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
  handleSendMessage(suggestionMessage);
  setShowSchemaDrawer(false);
};
```

```typescriptreact
<DatabaseSelector ... />
<Button 
  onClick={() => setShowSchemaDrawer(true)}
  disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
>
  数据库结构
</Button>
```

```typescriptreact
<Drawer
  placement="top"
  height={420}
  open={showSchemaDrawer}
  onClose={() => setShowSchemaDrawer(false)}
  destroyOnClose
  title="数据库结构"
>
  <DatabaseSchema 
    onSelectTable={handleSelectTable}
    selectedDatabaseId={selectedDatabaseId}
  />
</Drawer>
```

```typescriptreact
const handleSelectTable = (tableName: string) => {
  const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
  handleSendMessage(suggestionMessage);
  setShowSchemaDrawer(false);
};
```

---

**User**

数据库结构不要全屏展开，只在右边浮动展开

---

**Cursor**

I'll update the Database Schema Drawer to open as a floating panel on the right instead of full-width top, by changing its placement to right and using a fixed width.

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已改为右侧浮动展开：

- 将顶部全宽 Drawer 调整为右侧浮层：placement 改为 'right'，设置 width=420，移除 height。
- 触发表按钮保持在数据库选择旁，点击后右侧弹出，不遮挡头部和聊天区。
- 选表后依旧自动注入提示并关闭浮层。

关键修改：
```470:486:chatbi-ui/src/App.tsx
<Drawer
  placement="right"
  width={420}
  open={showSchemaDrawer}
  onClose={() => setShowSchemaDrawer(false)}
  destroyOnClose
  title="数据库结构"
>
  <DatabaseSchema 
    onSelectTable={handleSelectTable}
    selectedDatabaseId={selectedDatabaseId}
  />
</Drawer>
```

```typescriptreact
<Drawer
  placement="right"
  width={420}
  open={showSchemaDrawer}
  onClose={() => setShowSchemaDrawer(false)}
  destroyOnClose
  title="数据库结构"
>
  <DatabaseSchema 
    onSelectTable={handleSelectTable}
    selectedDatabaseId={selectedDatabaseId}
  />
</Drawer>
```

---

**User**

会话仅ADMIN支持删除

---

**Cursor**

```java
*** Begin Patch
*** Update File: /Users/fayespica/git/FayeSpica/chat-bi-ai-poc/chatbi-server-java/src/main/java/com/chatbi/controller/SessionController.java
@@
-import com.chatbi.service.ChatSessionService;
+import com.chatbi.service.ChatSessionService;
+import com.chatbi.service.UserWhitelistService;
@@
     @Autowired
     private ChatMessageService chatMessageService;
+
+    @Autowired
+    private UserWhitelistService userWhitelistService;
@@
     @DeleteMapping("/{id}")
     @EnableAuth
     public ResponseEntity<?> deleteSession(
             @RequestHeader(value = "Login-Token", required = false) String loginToken,
             @PathVariable("id") Long id) {
         String userId = requireUserId(loginToken);
+        String role = userWhitelistService.getUserRole(userId);
+        if (!"ADMIN".equals(role)) {
+            return ResponseEntity.status(403).body(Map.of("error", "只有ADMIN可以删除会话"));
+        }
         boolean ok = chatSessionService.archiveSession(id, userId);
         if (ok) {
             return ResponseEntity.ok(Map.of("message", "会话已删除"));
         } else {
             return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
         }
     }
*** End Patch
```

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.UserToken;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.UserWhitelistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private UserWhitelistService userWhitelistService;

    private String requireUserId(String loginToken) {
        UserToken parsed = TokenInterceptor.parseUserTokenFromJson(loginToken);
        if (parsed == null || parsed.getUserId() == null) {
            throw new IllegalArgumentException("缺少有效的用户标识");
        }
        return parsed.getUserId();
    }

    @GetMapping
    @EnableAuth
    public ResponseEntity<List<ChatSession>> listSessions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        String userId = requireUserId(loginToken);
        return ResponseEntity.ok(chatSessionService.listSessions(userId));
    }

    @PostMapping
    @EnableAuth
    public ResponseEntity<ChatSession> createSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body != null ? body.getOrDefault("title", null) : null;
        ChatSession created = chatSessionService.createSession(userId, title);
        return ResponseEntity.ok(created);
    }

    @PatchMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> renameSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body) {
        String userId = requireUserId(loginToken);
        String title = body.get("title");
        Optional<ChatSession> updated = chatSessionService.renameSession(id, userId, title);
        return updated.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "会话不存在")));
    }

    @DeleteMapping("/{id}")
    @EnableAuth
    public ResponseEntity<?> deleteSession(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        String role = userWhitelistService.getUserRole(userId);
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "只有ADMIN可以删除会话"));
        }
        boolean ok = chatSessionService.archiveSession(id, userId);
        if (ok) {
            return ResponseEntity.ok(Map.of("message", "会话已删除"));
        } else {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
    }

    @GetMapping("/{id}/messages")
    @EnableAuth
    public ResponseEntity<?> listMessages(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @PathVariable("id") Long id) {
        String userId = requireUserId(loginToken);
        Optional<ChatSession> optional = chatSessionService.getByIdForUser(id, userId);
        if (optional.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "会话不存在"));
        }
        List<ChatMessage> messages = chatMessageService.listMessages(optional.get());
        return ResponseEntity.ok(messages);
    }
}



```

```typescriptreact
import React, { useEffect, useState } from 'react';
import { List, Button, Input, Modal, Space, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { PersistedChatSession } from '../types';
import { sessionAPI } from '../services/api';

interface SessionListProps {
  selectedSessionId?: number | null;
  onSelect: (sessionId: number) => void;
  canDelete?: boolean;
}

const SessionList: React.FC<SessionListProps> = ({ selectedSessionId, onSelect, canDelete }) => {
  const [sessions, setSessions] = useState<PersistedChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [renaming, setRenaming] = useState<PersistedChatSession | null>(null);
  const [newTitle, setNewTitle] = useState('');

  const loadSessions = async () => {
    setLoading(true);
    try {
      const list = await sessionAPI.listSessions();
      setSessions(list);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const handleCreate = async () => {
    const created = await sessionAPI.createSession('新的会话');
    setSessions(prev => [created, ...prev]);
    onSelect(created.id);
  };

  const handleRename = async () => {
    if (!renaming) return;
    const updated = await sessionAPI.renameSession(renaming.id, newTitle || renaming.title || '会话');
    setSessions(prev => prev.map(s => (s.id === updated.id ? updated : s)));
    setRenaming(null);
    setNewTitle('');
  };

  const handleDelete = async (id: number) => {
    await sessionAPI.deleteSession(id);
    setSessions(prev => prev.filter(s => s.id !== id));
  };

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建会话</Button>
      </Space>
      <List
        loading={loading}
        dataSource={sessions}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', background: selectedSessionId === item.id ? '#e6f7ff' : undefined }}
            onClick={() => onSelect(item.id)}
            actions={[
              <Button key="rename" type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setRenaming(item); setNewTitle(item.title || ''); }} />,
              canDelete ? (
                <Button key="delete" type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); handleDelete(item.id); }} />
              ) : null,
            ].filter(Boolean) as any}
          >
            <List.Item.Meta
              title={<Typography.Text ellipsis>{item.title || '未命名会话'}</Typography.Text>}
              description={new Date(item.updatedAt).toLocaleString()}
            />
          </List.Item>
        )}
      />

      <Modal
        title="重命名会话"
        open={!!renaming}
        onOk={handleRename}
        onCancel={() => { setRenaming(null); setNewTitle(''); }}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="请输入会话标题"
          maxLength={50}
        />
      </Modal>
    </div>
  );
};

export default SessionList;



```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt)
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已限制“删除会话”仅 ADMIN 可用，前后端同步完成：

- 后端权限校验
  - 更新 `SessionController.deleteSession`：删除前通过 `UserWhitelistService.getUserRole(userId)` 校验，仅 `ADMIN` 允许。非 ADMIN 返回 403。
  - 位置：
    ```38:62:chatbi-server-java/src/main/java/com/chatbi/controller/SessionController.java
    @Autowired private UserWhitelistService userWhitelistService;
    ...
    String role = userWhitelistService.getUserRole(userId);
    if (!"ADMIN".equals(role)) {
      return ResponseEntity.status(403).body(Map.of("error","只有ADMIN可以删除会话"));
    }
    ```

- 前端按钮可见性
  - `SessionList` 新增 `canDelete` 属性，仅在 `canDelete` 为真时展示删除按钮。
    - 文件：`chatbi-ui/src/components/SessionList.tsx`
  - 在 `App.tsx` 传入 `canDelete={userPermissions.role === 'ADMIN'}`，与后端策略一致。
    - 文件：`chatbi-ui/src/App.tsx`

这样即使前端修改也无法越权删除，安全闭环生效。

```java
    @Autowired private UserWhitelistService userWhitelistService;
    ...
    String role = userWhitelistService.getUserRole(userId);
    if (!"ADMIN".equals(role)) {
      return ResponseEntity.status(403).body(Map.of("error","只有ADMIN可以删除会话"));
    }
    ```

- 前端按钮可见性
  - `SessionList` 新增 `canDelete` 属性，仅在 `canDelete` 为真时展示删除按钮。
    - 文件：`chatbi-ui/src/components/SessionList.tsx`
  - 在 `App.tsx` 传入 `canDelete={userPermissions.role === 'ADMIN'}`，与后端策略一致。
    - 文件：`chatbi-ui/src/App.tsx`

这样即使前端修改也无法越权删除，安全闭环生效。
```

---

**User**

会话历史缺少详细信息

---

**Cursor**

I'll add detailed fields for semantic SQL, generated SQL, and execution result to persisted messages, adjust backend save logic to store them, expose them via the session messages API, and update the frontend to render these details when loading history.

```sql
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
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_chat_message_session_id (session_id),
  KEY idx_chat_message_created_at (created_at),
  CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

```

```sql
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

-- 创建会话表
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

-- 创建会话消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content LONGTEXT NOT NULL,
    semantic_sql LONGTEXT NULL,
    sql_query LONGTEXT NULL,
    execution_result LONGTEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_chat_message_session_id (session_id),
    KEY idx_chat_message_created_at (created_at),
    CONSTRAINT fk_chat_message_session_id FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


```

```java
package com.chatbi.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_id", columnList = "session_id"),
        @Index(name = "idx_chat_message_created_at", columnList = "created_at")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "role", length = 20, nullable = false)
    private String role; // user / assistant / system

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Lob
    @Column(name = "semantic_sql", columnDefinition = "LONGTEXT")
    private String semanticSql; // JSON string

    @Lob
    @Column(name = "sql_query", columnDefinition = "LONGTEXT")
    private String sqlQuery;

    @Lob
    @Column(name = "execution_result", columnDefinition = "LONGTEXT")
    private String executionResult; // JSON string

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getSemanticSql() { return semanticSql; }
    public void setSemanticSql(String semanticSql) { this.semanticSql = semanticSql; }

    public String getSqlQuery() { return sqlQuery; }
    public void setSqlQuery(String sqlQuery) { this.sqlQuery = sqlQuery; }

    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }
}



```

```java
package com.chatbi.service;

import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.SemanticSQL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatbi.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<ChatMessage> listMessages(ChatSession session) {
        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    public ChatMessage appendUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content, SemanticSQL semanticSQL, String sqlQuery, java.util.Map<String, Object> executionResult) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        try {
            if (semanticSQL != null) {
                message.setSemanticSql(OBJECT_MAPPER.writeValueAsString(semanticSQL));
            }
            if (sqlQuery != null) {
                message.setSqlQuery(sqlQuery);
            }
            if (executionResult != null) {
                message.setExecutionResult(OBJECT_MAPPER.writeValueAsString(executionResult));
            }
        } catch (Exception ignored) {}
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }
}



```

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.*;
import com.chatbi.service.ChatService;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.DatabaseAdminService;
import com.chatbi.service.DatabaseManager;
import com.chatbi.service.SchemaMetadataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private DatabaseAdminService databaseAdminService;

    @Autowired
    private SchemaMetadataBuilder metadataBuilder;

    @Autowired
    private com.chatbi.service.UserWhitelistService userWhitelistService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "message", "ChatBI API Server",
                "version", "1.0.0",
                "status", "running"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "database", "connected",
                    "tables_count", tables.size()
            ));
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> legacyHealthCheck() {
        return healthCheck();
    }

    @PostMapping("/chat")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @Valid @RequestBody ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}",
                    request.getConversationId(), request.getMessage());

            // Resolve userId from token
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);
            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.status(403).body(new ChatResponse(
                        "用户未认证或缺少userId", null, null,
                        request.getConversationId(), null, null
                ));
            }

            // Resolve or create session
            Long sessionId = null;
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                try {
                    sessionId = Long.parseLong(request.getConversationId());
                } catch (NumberFormatException ignored) {
                    sessionId = null;
                }
            }

            ChatSession session;
            if (sessionId == null) {
                String title = request.getMessage();
                if (title != null && title.length() > 50) {
                    title = title.substring(0, 50);
                }
                session = chatSessionService.createSession(userToken.getUserId(), title);
            } else {
                session = chatSessionService.getByIdForUser(sessionId, userToken.getUserId())
                        .orElseGet(() -> chatSessionService.createSession(userToken.getUserId(), null));
            }

            // Save user message
            chatMessageService.appendUserMessage(session, request.getMessage());

            // Delegate to existing chat pipeline to get response content
            ChatResponse response = chatService.processChatMessage(request);

            // Save assistant message (with details)
            if (response != null && response.getResponse() != null) {
                chatMessageService.appendAssistantMessage(
                        session,
                        response.getResponse(),
                        response.getSemanticSql(),
                        response.getSqlQuery(),
                        response.getExecutionResult()
                );
            }

            // Ensure response carries session id as conversation_id
            ChatResponse finalResponse = new ChatResponse(
                    response.getResponse(),
                    response.getSqlQuery(),
                    response.getSemanticSql(),
                    String.valueOf(session.getId()),
                    response.getExecutionResult(),
                    response.getDebugOllama()
            );

            return ResponseEntity.ok(finalResponse);
        } catch (Exception e) {
            logger.error("/api/chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("处理聊天请求时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/execute-sql")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<SQLExecutionResponse> executeSql(@Valid @RequestBody SQLExecutionRequest request) {
        try {
            // Get database connection
            DatabaseConnection selectedConnection = null;
            if (request.getDatabaseConnectionId() != null && !request.getDatabaseConnectionId().trim().isEmpty()) {
                selectedConnection = databaseAdminService.getConnection(request.getDatabaseConnectionId())
                        .orElse(null);
                if (selectedConnection == null) {
                    logger.warn("Database connection not found: {}", request.getDatabaseConnectionId());
                }
            }

            // Execute SQL query
            SQLExecutionResponse result;
            if (selectedConnection != null) {
                result = databaseManager.executeQuery(request.getSqlQuery(), selectedConnection);
            } else {
                result = databaseManager.executeQuery(request.getSqlQuery());
            }

            // Update conversation history if conversation ID is provided
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                chatService.executeSqlAndUpdateResponse(request.getConversationId(), request.getSqlQuery());
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", e.getMessage(), e);
            throw new RuntimeException("执行SQL时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversationHistory(@PathVariable String conversationId) {
        try {
            List<Map<String, Object>> history = chatService.getConversationHistory(conversationId);
            return ResponseEntity.ok(Map.of(
                    "conversation_id", conversationId,
                    "history", history
            ));
        } catch (Exception e) {
            logger.error("Error getting conversation history: {}", e.getMessage(), e);
            throw new RuntimeException("获取会话历史时发生错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            chatService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of("message", "会话历史已清除"));
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);
            throw new RuntimeException("清除会话历史时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ResponseEntity<Map<String, List<String>>> getTables() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            logger.error("Error getting tables: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库表时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables/{tableName}/schema")
    public ResponseEntity<Map<String, Object>> getTableSchema(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> schema = databaseManager.getTableSchema(tableName);
            return ResponseEntity.ok(Map.of(
                    "table_name", tableName,
                    "schema", schema
            ));
        } catch (Exception e) {
            logger.error("Error getting table schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/schema")
    public ResponseEntity<Map<String, Object>> getFullDatabaseSchema(
            @RequestParam(required = false) String connectionId) {
        try {
            List<String> tables;
            Map<String, Object> schema = new java.util.HashMap<>();

            if (connectionId != null && !connectionId.trim().isEmpty()) {
                // Use specific database connection
                Optional<DatabaseConnection> connection = databaseAdminService.getConnection(connectionId);
                if (connection.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                tables = databaseAdminService.getTables(connectionId).stream()
                        .map(TableInfo::getTableName)
                        .collect(java.util.stream.Collectors.toList());

                for (String table : tables) {
                    TableSchema tableSchema = databaseAdminService.getTableSchema(connectionId, table);
                    schema.put(table, tableSchema.getColumns());
                }
            } else {
                // Use active connection (default behavior)
                tables = databaseManager.getAllTables();
                for (String table : tables) {
                    schema.put(table, databaseManager.getTableSchema(table));
                }
            }

            return ResponseEntity.ok(Map.of("database_schema", schema));
        } catch (Exception e) {
            logger.error("Error getting full database schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取完整数据库结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getEnrichedMetadata() {
        try {
            Map<String, Object> metadata = metadataBuilder.buildDatabaseMetadata(null);
            return ResponseEntity.ok(Map.of("metadata", metadata));
        } catch (Exception e) {
            logger.error("Error getting metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/user/permissions")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        try {
            // 从请求属性中获取用户信息（由 TokenInterceptor 设置）
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);

            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.ok(Map.of(
                        "hasDatabaseAccess", false,
                        "canDeleteDatabase", false,
                        "role", "READER"
                ));
            }

            // 获取用户角色
            String role = userWhitelistService.getUserRole(userToken.getUserId());
            if (role == null) {
                role = "READER"; // 默认角色
            }

            // 判断权限
            boolean hasDatabaseAccess = "ADMIN".equals(role) || "OPERATOR".equals(role);
            boolean canDeleteDatabase = "ADMIN".equals(role);

            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", hasDatabaseAccess,
                    "canDeleteDatabase", canDeleteDatabase,
                    "role", role
            ));
        } catch (Exception e) {
            logger.error("Error getting user permissions: {}", e.getMessage(), e);
            // 发生错误时返回默认权限（最严格）
            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", false,
                    "canDeleteDatabase", false,
                    "role", "READER"
            ));
        }
    }
}
```

```typescript
export interface SemanticSQL {
  tables: string[];
  columns: string[];
  conditions: Array<{
    column: string;
    operator: string;
    value: any;
    table?: string;
  }>;
  aggregations: Array<{
    function: string;
    column: string;
    alias?: string;
  }>;
  joins: Array<{
    type: string;
    table1: string;
    table2: string;
    condition: string;
  }>;
  order_by?: Array<{
    column: string;
    direction: string;
  }>;
  group_by?: string[];
  limit?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  semantic_sql?: SemanticSQL;
  sql_query?: string;
  execution_result?: SQLExecutionResult;
  debug_info?: {
    request?: ChatRequest;
    response?: ChatResponse;
    ollama?: Record<string, any>;
    sql_execution?: SQLExecutionResult | null;
  };
}

export interface ChatRequest {
  message: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface ChatResponse {
  response: string;
  sql_query?: string;
  semantic_sql?: SemanticSQL;
  conversation_id: string;
  execution_result?: SQLExecutionResult;
  debug_ollama?: Record<string, any>;
}

export interface SQLExecutionRequest {
  sql_query: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface SQLExecutionResult {
  success: boolean;
  data?: Array<Record<string, any>>;
  error?: string;
  row_count?: number;
}

export interface DatabaseSchema {
  [tableName: string]: Array<{
    Field: string;
    Type: string;
    Null: string;
    Key: string;
    Default: any;
    Extra: string;
  }>;
}

// 数据库后台管理相关类型
export interface DatabaseConnection {
  id?: string;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  database_name: string;
  charset_name: string;
  description?: string;
  is_active: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface DatabaseConnectionCreate {
  name: string;
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
  description?: string;
}

export interface DatabaseConnectionUpdate {
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  database_name?: string;
  charset_name?: string;
  description?: string;
  is_active?: boolean;
}

export interface DatabaseConnectionTest {
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
}

export interface TableInfo {
  table_name: string;
  table_comment?: string;
  table_rows?: number;
  table_size?: string;
  engine?: string;
  charset_name?: string;
}

export interface ColumnInfo {
  column_name: string;
  data_type: string;
  is_nullable: boolean;
  column_key?: string;
  column_default?: string;
  extra?: string;
  column_comment?: string;
  column_order: number;
}

export interface TableSchema {
  table_name: string;
  table_comment?: string;
  columns: ColumnInfo[];
}

export interface CommentUpdate {
  table_name: string;
  column_name?: string;
  comment: string;
}

export interface UserToken {
  userId?: string;
  userName?: string;
  roleNames?: string[];
}

// 会话与消息（持久化）
export interface PersistedChatSession {
  id: number;
  userId: string;
  title?: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PersistedChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'system';
  content: string;
  createdAt: string;
  semanticSql?: any;
  sqlQuery?: string;
  executionResult?: any;
}

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => ({
      id: String(m.id),
      role: m.role as 'user' | 'assistant',
      content: m.content,
      timestamp: new Date(m.createdAt),
      semantic_sql: m.semanticSql,
      sql_query: m.sqlQuery,
      execution_result: m.executionResult
    }));
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

我已把“会话历史”补充为包含详细信息，前后端都已打通：

- 后端持久化更多字段
  - 表结构新增字段：`chat_message.semantic_sql`、`sql_query`、`execution_result`（LONGTEXT）。
    - `chatbi-server-java/src/main/resources/schema.sql`
    - `db-init/init-system.sql`
  - 实体更新：`ChatMessage` 增加上述三个字段及 getter/setter。
    - `chatbi-server-java/src/main/java/com/chatbi/model/ChatMessage.java`
  - 保存详情：`ChatController` 在生成回复后，调用增强版 `appendAssistantMessage(...)` 持久化内容、语义 SQL、生成 SQL、执行结果（序列化为 JSON）。
    - `chatbi-server-java/src/main/java/com/chatbi/controller/ChatController.java`
  - 序列化：`ChatMessageService` 使用 `ObjectMapper` 将 `SemanticSQL` 与执行结果 Map 序列化为 JSON 字符串存表。
    - `chatbi-server-java/src/main/java/com/chatbi/service/ChatMessageService.java`

- 前端读取并展示
  - 类型扩展：`PersistedChatMessage` 增加 `semanticSql`、`sqlQuery`、`executionResult`。
    - `chatbi-ui/src/types/index.ts`
  - 历史映射：加载会话消息时，将后端返回的 `semanticSql/sqlQuery/executionResult` 映射到现有渲染类型的 `semantic_sql/sql_query/execution_result`，这样历史记录里也能看到 SQL 与执行结果。
    - `chatbi-ui/src/App.tsx`

现在打开已有会话即可看到历史中的语义 SQL、生成 SQL 和执行结果信息，和实时对话的一致。

---

**User**

历史会话里，语义SQL结构没有显示

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    if (!currentSessionId) {
      setMessages([]);
      return;
    }
    try {
      await sessionAPI.deleteSession(currentSessionId);
      setMessages([] as ChatMessageType[]);
      setCurrentSessionId(null);
      setConversationId('');
      localStorage.removeItem('chatbi:lastSessionId');
      message.success('对话已删除');
    } catch (error: any) {
      message.error('删除对话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

我已修复：历史消息里的语义 SQL 是以 JSON 字符串返回，前端现在会在加载历史时将其解析为对象再传给消息渲染组件，因此会正确显示。

变更点
- 在 `chatbi-ui/src/App.tsx` 的历史映射中，新增对 `semanticSql` 和 `executionResult` 的 JSON 解析：
```474:492:chatbi-ui/src/App.tsx
const mapped: ChatMessageType[] = list.map(m => {
  let parsedSemantic: any = m.semanticSql;
  if (typeof parsedSemantic === 'string') {
    try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
  }
  let parsedExec: any = m.executionResult;
  if (typeof parsedExec === 'string') {
    try { parsedExec = JSON.parse(parsedExec); } catch {}
  }
  return {
    id: String(m.id),
    role: m.role as 'user' | 'assistant',
    content: m.content,
    timestamp: new Date(m.createdAt),
    semantic_sql: parsedSemantic,
    sql_query: m.sqlQuery || undefined,
    execution_result: parsedExec,
  } as ChatMessageType;
});
```

这样历史会话中会显示语义 SQL 结构，与实时回复一致。

```typescriptreact
const mapped: ChatMessageType[] = list.map(m => {
  let parsedSemantic: any = m.semanticSql;
  if (typeof parsedSemantic === 'string') {
    try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
  }
  let parsedExec: any = m.executionResult;
  if (typeof parsedExec === 'string') {
    try { parsedExec = JSON.parse(parsedExec); } catch {}
  }
  return {
    id: String(m.id),
    role: m.role as 'user' | 'assistant',
    content: m.content,
    timestamp: new Date(m.createdAt),
    semantic_sql: parsedSemantic,
    sql_query: m.sqlQuery || undefined,
    execution_result: parsedExec,
  } as ChatMessageType;
});
```

---

**User**

下方的清空对话改成新建会话

---

**Cursor**

```typescriptreact
import React, { useState } from 'react';
import { Input, Button, Space, message } from 'antd';
import { SendOutlined, PlusOutlined } from '@ant-design/icons';

const { TextArea } = Input;

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  onClearChat: () => void;
  disabled?: boolean;
  placeholder?: string;
}

const ChatInput: React.FC<ChatInputProps> = ({
  onSendMessage,
  onClearChat,
  disabled = false,
  placeholder = "请输入您想要查询的问题，例如：查询所有用户的订单总金额"
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSend = async () => {
    if (!inputValue.trim()) {
      message.warning('请输入消息内容');
      return;
    }

    if (disabled) return;

    setIsLoading(true);
    try {
      await onSendMessage(inputValue.trim());
      setInputValue('');
    } catch (error) {
      message.error('发送消息失败');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleClear = () => {
    if (disabled) return;
    onClearChat();
    setInputValue('');
  };

  return (
    <div className="chat-input-area">
      <Space direction="vertical" style={{ width: '100%' }}>
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder={placeholder}
          disabled={disabled}
          autoSize={{ minRows: 2, maxRows: 6 }}
          style={{ fontSize: '14px' }}
        />
        
        <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
          <Button
            icon={<PlusOutlined />}
            onClick={handleClear}
            disabled={disabled}
          >
            新建会话
          </Button>
          
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={isLoading}
            disabled={disabled || !inputValue.trim()}
          >
            发送
          </Button>
        </Space>
      </Space>
    </div>
  );
};

export default ChatInput;

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 尝试恢复上次会话
    const lastSession = localStorage.getItem('chatbi:lastSessionId');
    if (lastSession) {
      const idNum = parseInt(lastSession, 10);
      if (!Number.isNaN(idNum)) {
        handleSelectSession(idNum);
        return;
      }
    }
    // 无会话时显示欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已将底部“清空对话”改为“新建会话”：

- 按钮与文案
  - `chatbi-ui/src/components/ChatInput.tsx`：将按钮图标改为 Plus，文案改为“新建会话”。

- 行为逻辑
  - `chatbi-ui/src/App.tsx`：`handleClearChat` 改为创建一个新会话并自动选中（`sessionAPI.createSession('新的会话')` → `handleSelectSession`），并提示“已新建会话”。

这样不会清除或删除当前会话记录，直接开启一个新的会话继续聊天。

---

**User**

用户一个会话都没有时，前端自动创建新会话

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    setMessages(mapped);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已实现：当用户没有任何会话时，前端会自动创建一个新会话并选中。

- 在 `chatbi-ui/src/App.tsx` 的初始化 `useEffect` 中：
  - 优先恢复 `localStorage` 中的上次会话。
  - 若无记录，则调用 `sessionAPI.listSessions()`：
    - 有会话：自动选中第一个。
    - 无会话：自动 `createSession('新的会话')` 并选中。
  - 异常时回退显示欢迎消息。

这确保首次进入或清空会话列表后，都会有一个可用的当前会话。

---

**User**

每个会话最前面都应该显示欢迎消息，并给出样例

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content:
        '欢迎使用ChatBI智能聊天系统！\n\n' +
        '我可以把自然语言转换为可执行的 SQL，并展示结果。\n\n' +
        '你可以试试这些示例：\n' +
        '- 查询最近10条订单\n' +
        '- 统计每个城市的用户数量\n' +
        '- 查看 products 表的前10行数据',
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已实现：每个会话最前面固定显示欢迎消息并附带示例。

- 在 `chatbi-ui/src/App.tsx` 的 `loadSessionMessages` 中，加载历史后会自动在最前面插入一条欢迎消息：
  - 内容包含用途说明和3个示例：
    - 查询最近10条订单
    - 统计每个城市的用户数量
    - 查看 products 表的前10行数据
- 新建会话或切换会话时都会经过 `loadSessionMessages`，因此每个会话顶部都会看到这条欢迎提示。

---

**User**

示例应该联动当前的数据库表

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
        // 加载该连接的表名
        try {
          const tables = await databaseAdminAPI.getTables(activeConnection.id);
          setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
        } catch {}
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- 查询最近10条订单',
          '- 统计每个城市的用户数量',
          '- 查看任意表的前10行数据',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- 查看 ${name} 表的前10行数据`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- 查询最近10条订单',
          '- 统计每个城市的用户数量',
          '- 查看任意表的前10行数据',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- 查看 ${name} 表的前10行数据`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已改为“示例联动当前数据库表”：

- 新增表名状态并按所选数据库连接加载表
  - 在 `App.tsx` 增加 `tableNames`，初始化和切换连接时调用 `databaseAdminAPI.getTables(connectionId)` 并保存表名。
  - 切换连接时自动刷新示例数据源。

- 欢迎消息动态生成示例
  - 在 `loadSessionMessages` 里，基于 `tableNames` 生成 1-3 条示例，如：
    - 如果有表名：`- 查看 users 表的前10行数据`（取前3个表）
    - 没有表名时，回退为通用示例

关键片段：
```80:99:chatbi-ui/src/App.tsx
const handleDatabaseChange = (connectionId: string) => {
  setSelectedDatabaseId(connectionId);
  (async () => {
    try {
      const tables = await databaseAdminAPI.getTables(connectionId);
      setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
    } catch {
      setTableNames([]);
    }
  })();
};
```

```118:130:chatbi-ui/src/App.tsx
if (activeConnection) {
  if (activeConnection.id) {
    setSelectedDatabaseId(activeConnection.id);
    const tables = await databaseAdminAPI.getTables(activeConnection.id);
    setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
  } else {
    setSelectedDatabaseId(undefined);
    setTableNames([]);
  }
}
```

```506:534:chatbi-ui/src/App.tsx
const examples: string[] = (() => {
  if (!tableNames || tableNames.length === 0) {
    return [
      '- 查询最近10条订单',
      '- 统计每个城市的用户数量',
      '- 查看任意表的前10行数据',
    ];
  }
  const picks = tableNames.slice(0, 3);
  return picks.map(name => `- 查看 ${name} 表的前10行数据`);
})();
const welcomeMessage: ChatMessageType = {
  ...,
  content: [
    '欢迎使用ChatBI智能聊天系统！',
    '',
    '我可以把自然语言转换为可执行的 SQL，并展示结果。',
    '',
    '你可以试试这些示例：',
    ...examples,
  ].join('\\n'),
};
```

效果：每个会话顶部的示例会随当前数据库连接的表动态变化。

```typescriptreact
const handleDatabaseChange = (connectionId: string) => {
  setSelectedDatabaseId(connectionId);
  (async () => {
    try {
      const tables = await databaseAdminAPI.getTables(connectionId);
      setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
    } catch {
      setTableNames([]);
    }
  })();
};
```

```typescriptreact
if (activeConnection) {
  if (activeConnection.id) {
    setSelectedDatabaseId(activeConnection.id);
    const tables = await databaseAdminAPI.getTables(activeConnection.id);
    setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
  } else {
    setSelectedDatabaseId(undefined);
    setTableNames([]);
  }
}
```

```typescriptreact
const examples: string[] = (() => {
  if (!tableNames || tableNames.length === 0) {
    return [
      '- 查询最近10条订单',
      '- 统计每个城市的用户数量',
      '- 查看任意表的前10行数据',
    ];
  }
  const picks = tableNames.slice(0, 3);
  return picks.map(name => `- 查看 ${name} 表的前10行数据`);
})();
const welcomeMessage: ChatMessageType = {
  ...,
  content: [
    '欢迎使用ChatBI智能聊天系统！',
    '',
    '我可以把自然语言转换为可执行的 SQL，并展示结果。',
    '',
    '你可以试试这些示例：',
    ...examples,
  ].join('\\n'),
};
```

---

**User**

示例添加点击能力，点击后自动发送消息

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- [查询最近10条订单](#query)',
          '- [统计每个城市的用户数量](#query)',
          '- [查看任意表的前10行数据](#query)',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- [查看 ${name} 表的前10行数据](#query)`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已为欢迎示例添加点击发送能力：

- 渲染层已支持点击示例发送消息
  - `chatbi-ui/src/components/ChatMessage.tsx` 中对 markdown 链接 `(#query)` 已做拦截，点击后会把链接文本作为消息发送。

- 欢迎消息改为可点击示例
  - `chatbi-ui/src/App.tsx` 在构建欢迎消息时，将示例改为 markdown 链接格式：
    - 无表名时：
      - `- [查询最近10条订单](#query)`
      - `- [统计每个城市的用户数量](#query)`
      - `- [查看任意表的前10行数据](#query)`
    - 有表名时：前三张表生成
      - `- [查看 users 表的前10行数据](#query)` 等

点击这些示例会直接触发发送，对话区立即开始基于该示例检索。

---

**User**

历史消息看不到查询结果 和 debug信息

---

**Cursor**

```sql
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

```

```sql
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

-- 创建会话表
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

-- 创建会话消息表
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


```

```java
package com.chatbi.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_id", columnList = "session_id"),
        @Index(name = "idx_chat_message_created_at", columnList = "created_at")
})
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "role", length = 20, nullable = false)
    private String role; // user / assistant / system

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Lob
    @Column(name = "semantic_sql", columnDefinition = "LONGTEXT")
    private String semanticSql; // JSON string

    @Lob
    @Column(name = "sql_query", columnDefinition = "LONGTEXT")
    private String sqlQuery;

    @Lob
    @Column(name = "execution_result", columnDefinition = "LONGTEXT")
    private String executionResult; // JSON string

    @Lob
    @Column(name = "debug_info", columnDefinition = "LONGTEXT")
    private String debugInfo; // JSON string

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ChatSession getSession() { return session; }
    public void setSession(ChatSession session) { this.session = session; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getSemanticSql() { return semanticSql; }
    public void setSemanticSql(String semanticSql) { this.semanticSql = semanticSql; }

    public String getSqlQuery() { return sqlQuery; }
    public void setSqlQuery(String sqlQuery) { this.sqlQuery = sqlQuery; }

    public String getExecutionResult() { return executionResult; }
    public void setExecutionResult(String executionResult) { this.executionResult = executionResult; }

    public String getDebugInfo() { return debugInfo; }
    public void setDebugInfo(String debugInfo) { this.debugInfo = debugInfo; }
}



```

```java
package com.chatbi.service;

import com.chatbi.model.ChatMessage;
import com.chatbi.model.ChatSession;
import com.chatbi.model.SemanticSQL;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chatbi.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ChatMessageService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionService chatSessionService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public List<ChatMessage> listMessages(ChatSession session) {
        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    public ChatMessage appendUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("user");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public ChatMessage appendAssistantMessage(ChatSession session, String content, SemanticSQL semanticSQL, String sqlQuery, java.util.Map<String, Object> executionResult, java.util.Map<String, Object> debugInfo) {
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole("assistant");
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        try {
            if (semanticSQL != null) {
                message.setSemanticSql(OBJECT_MAPPER.writeValueAsString(semanticSQL));
            }
            if (sqlQuery != null) {
                message.setSqlQuery(sqlQuery);
            }
            if (executionResult != null) {
                message.setExecutionResult(OBJECT_MAPPER.writeValueAsString(executionResult));
            }
            if (debugInfo != null) {
                message.setDebugInfo(OBJECT_MAPPER.writeValueAsString(debugInfo));
            }
        } catch (Exception ignored) {}
        ChatMessage saved = chatMessageRepository.save(message);
        chatSessionService.touchUpdatedAt(session);
        return saved;
    }

    public void appendExecutionResultToLastAssistant(ChatSession session, java.util.Map<String, Object> executionResult) {
        List<ChatMessage> messages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("assistant".equals(msg.getRole())) {
                try {
                    if (executionResult != null) {
                        msg.setExecutionResult(OBJECT_MAPPER.writeValueAsString(executionResult));
                    }
                } catch (Exception ignored) {}
                chatMessageRepository.save(msg);
                chatSessionService.touchUpdatedAt(session);
                break;
            }
        }
    }
}



```

```java
package com.chatbi.service;

import com.chatbi.model.ChatSession;
import com.chatbi.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    public List<ChatSession> listSessions(String userId) {
        return chatSessionRepository.findByUserIdAndArchivedFalseOrderByUpdatedAtDesc(userId);
    }

    public ChatSession createSession(String userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setArchived(false);
        session.setCreatedAt(OffsetDateTime.now());
        session.setUpdatedAt(OffsetDateTime.now());
        return chatSessionRepository.save(session);
    }

    public Optional<ChatSession> getByIdForUser(Long id, String userId) {
        return chatSessionRepository.findByIdAndUserId(id, userId);
    }

    public Optional<ChatSession> getById(Long id) {
        return chatSessionRepository.findById(id);
    }

    public Optional<ChatSession> renameSession(Long id, String userId, String title) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        optional.ifPresent(s -> {
            s.setTitle(title);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
        });
        return optional;
    }

    public boolean archiveSession(Long id, String userId) {
        Optional<ChatSession> optional = chatSessionRepository.findByIdAndUserId(id, userId);
        if (optional.isPresent()) {
            ChatSession s = optional.get();
            s.setArchived(true);
            s.setUpdatedAt(OffsetDateTime.now());
            chatSessionRepository.save(s);
            return true;
        }
        return false;
    }

    public void touchUpdatedAt(ChatSession session) {
        session.setUpdatedAt(OffsetDateTime.now());
        chatSessionRepository.save(session);
    }
}



```

```java
package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.interceptor.TokenInterceptor;
import com.chatbi.model.*;
import com.chatbi.service.ChatService;
import com.chatbi.service.ChatMessageService;
import com.chatbi.service.ChatSessionService;
import com.chatbi.service.DatabaseAdminService;
import com.chatbi.service.DatabaseManager;
import com.chatbi.service.SchemaMetadataBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private DatabaseAdminService databaseAdminService;

    @Autowired
    private SchemaMetadataBuilder metadataBuilder;

    @Autowired
    private com.chatbi.service.UserWhitelistService userWhitelistService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatMessageService chatMessageService;

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "message", "ChatBI API Server",
                "version", "1.0.0",
                "status", "running"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of(
                    "status", "healthy",
                    "database", "connected",
                    "tables_count", tables.size()
            ));
        } catch (Exception e) {
            logger.error("Health check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(503).body(Map.of(
                    "status", "unhealthy",
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, Object>> legacyHealthCheck() {
        return healthCheck();
    }

    @PostMapping("/chat")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<ChatResponse> chat(
            @RequestHeader(value = "Login-Token", required = false) String loginToken,
            @Valid @RequestBody ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}",
                    request.getConversationId(), request.getMessage());

            // Resolve userId from token
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);
            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.status(403).body(new ChatResponse(
                        "用户未认证或缺少userId", null, null,
                        request.getConversationId(), null, null
                ));
            }

            // Resolve or create session
            Long sessionId = null;
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                try {
                    sessionId = Long.parseLong(request.getConversationId());
                } catch (NumberFormatException ignored) {
                    sessionId = null;
                }
            }

            ChatSession session;
            if (sessionId == null) {
                String title = request.getMessage();
                if (title != null && title.length() > 50) {
                    title = title.substring(0, 50);
                }
                session = chatSessionService.createSession(userToken.getUserId(), title);
            } else {
                session = chatSessionService.getByIdForUser(sessionId, userToken.getUserId())
                        .orElseGet(() -> chatSessionService.createSession(userToken.getUserId(), null));
            }

            // Save user message
            chatMessageService.appendUserMessage(session, request.getMessage());

            // Delegate to existing chat pipeline to get response content
            ChatResponse response = chatService.processChatMessage(request);

            // Save assistant message (with details)
            if (response != null && response.getResponse() != null) {
                chatMessageService.appendAssistantMessage(
                        session,
                        response.getResponse(),
                        response.getSemanticSql(),
                        response.getSqlQuery(),
                        response.getExecutionResult(),
                        response.getDebugOllama()
                );
            }

            // Ensure response carries session id as conversation_id
            ChatResponse finalResponse = new ChatResponse(
                    response.getResponse(),
                    response.getSqlQuery(),
                    response.getSemanticSql(),
                    String.valueOf(session.getId()),
                    response.getExecutionResult(),
                    response.getDebugOllama()
            );

            return ResponseEntity.ok(finalResponse);
        } catch (Exception e) {
            logger.error("/api/chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("处理聊天请求时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/execute-sql")
    @EnableAuth  // 示例：此接口需要token验证
    public ResponseEntity<SQLExecutionResponse> executeSql(@Valid @RequestBody SQLExecutionRequest request) {
        try {
            // Get database connection
            DatabaseConnection selectedConnection = null;
            if (request.getDatabaseConnectionId() != null && !request.getDatabaseConnectionId().trim().isEmpty()) {
                selectedConnection = databaseAdminService.getConnection(request.getDatabaseConnectionId())
                        .orElse(null);
                if (selectedConnection == null) {
                    logger.warn("Database connection not found: {}", request.getDatabaseConnectionId());
                }
            }

            // Execute SQL query
            SQLExecutionResponse result;
            if (selectedConnection != null) {
                result = databaseManager.executeQuery(request.getSqlQuery(), selectedConnection);
            } else {
                result = databaseManager.executeQuery(request.getSqlQuery());
            }

            // Update conversation history if conversation ID is provided
            if (request.getConversationId() != null && !request.getConversationId().trim().isEmpty()) {
                chatService.executeSqlAndUpdateResponse(request.getConversationId(), request.getSqlQuery());
                try {
                    Long sid = Long.parseLong(request.getConversationId());
                    chatSessionService.getById(sid).ifPresent(s -> {
                        java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
                        resultMap.put("success", result.isSuccess());
                        resultMap.put("data", result.getData());
                        resultMap.put("error", result.getError());
                        resultMap.put("row_count", result.getRowCount());
                        chatMessageService.appendExecutionResultToLastAssistant(s, resultMap);
                    });
                } catch (NumberFormatException ignored) {}
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", e.getMessage(), e);
            throw new RuntimeException("执行SQL时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversationHistory(@PathVariable String conversationId) {
        try {
            List<Map<String, Object>> history = chatService.getConversationHistory(conversationId);
            return ResponseEntity.ok(Map.of(
                    "conversation_id", conversationId,
                    "history", history
            ));
        } catch (Exception e) {
            logger.error("Error getting conversation history: {}", e.getMessage(), e);
            throw new RuntimeException("获取会话历史时发生错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, String>> clearConversation(@PathVariable String conversationId) {
        try {
            chatService.clearConversation(conversationId);
            return ResponseEntity.ok(Map.of("message", "会话历史已清除"));
        } catch (Exception e) {
            logger.error("Error clearing conversation: {}", e.getMessage(), e);
            throw new RuntimeException("清除会话历史时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables")
    public ResponseEntity<Map<String, List<String>>> getTables() {
        try {
            List<String> tables = databaseManager.getAllTables();
            return ResponseEntity.ok(Map.of("tables", tables));
        } catch (Exception e) {
            logger.error("Error getting tables: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库表时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/tables/{tableName}/schema")
    public ResponseEntity<Map<String, Object>> getTableSchema(@PathVariable String tableName) {
        try {
            List<Map<String, Object>> schema = databaseManager.getTableSchema(tableName);
            return ResponseEntity.ok(Map.of(
                    "table_name", tableName,
                    "schema", schema
            ));
        } catch (Exception e) {
            logger.error("Error getting table schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/database/schema")
    public ResponseEntity<Map<String, Object>> getFullDatabaseSchema(
            @RequestParam(required = false) String connectionId) {
        try {
            List<String> tables;
            Map<String, Object> schema = new java.util.HashMap<>();

            if (connectionId != null && !connectionId.trim().isEmpty()) {
                // Use specific database connection
                Optional<DatabaseConnection> connection = databaseAdminService.getConnection(connectionId);
                if (connection.isEmpty()) {
                    return ResponseEntity.notFound().build();
                }
                tables = databaseAdminService.getTables(connectionId).stream()
                        .map(TableInfo::getTableName)
                        .collect(java.util.stream.Collectors.toList());

                for (String table : tables) {
                    TableSchema tableSchema = databaseAdminService.getTableSchema(connectionId, table);
                    schema.put(table, tableSchema.getColumns());
                }
            } else {
                // Use active connection (default behavior)
                tables = databaseManager.getAllTables();
                for (String table : tables) {
                    schema.put(table, databaseManager.getTableSchema(table));
                }
            }

            return ResponseEntity.ok(Map.of("database_schema", schema));
        } catch (Exception e) {
            logger.error("Error getting full database schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取完整数据库结构时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/metadata")
    public ResponseEntity<Map<String, Object>> getEnrichedMetadata() {
        try {
            Map<String, Object> metadata = metadataBuilder.buildDatabaseMetadata(null);
            return ResponseEntity.ok(Map.of("metadata", metadata));
        } catch (Exception e) {
            logger.error("Error getting metadata: {}", e.getMessage(), e);
            throw new RuntimeException("获取元数据时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/user/permissions")
    public ResponseEntity<Map<String, Object>> getUserPermissions(
            @RequestHeader(value = "Login-Token", required = false) String loginToken) {
        try {
            // 从请求属性中获取用户信息（由 TokenInterceptor 设置）
            UserToken userToken = TokenInterceptor.parseUserTokenFromJson(loginToken);

            if (userToken == null || userToken.getUserId() == null) {
                return ResponseEntity.ok(Map.of(
                        "hasDatabaseAccess", false,
                        "canDeleteDatabase", false,
                        "role", "READER"
                ));
            }

            // 获取用户角色
            String role = userWhitelistService.getUserRole(userToken.getUserId());
            if (role == null) {
                role = "READER"; // 默认角色
            }

            // 判断权限
            boolean hasDatabaseAccess = "ADMIN".equals(role) || "OPERATOR".equals(role);
            boolean canDeleteDatabase = "ADMIN".equals(role);

            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", hasDatabaseAccess,
                    "canDeleteDatabase", canDeleteDatabase,
                    "role", role
            ));
        } catch (Exception e) {
            logger.error("Error getting user permissions: {}", e.getMessage(), e);
            // 发生错误时返回默认权限（最严格）
            return ResponseEntity.ok(Map.of(
                    "hasDatabaseAccess", false,
                    "canDeleteDatabase", false,
                    "role", "READER"
            ));
        }
    }
}
```

```typescript
export interface SemanticSQL {
  tables: string[];
  columns: string[];
  conditions: Array<{
    column: string;
    operator: string;
    value: any;
    table?: string;
  }>;
  aggregations: Array<{
    function: string;
    column: string;
    alias?: string;
  }>;
  joins: Array<{
    type: string;
    table1: string;
    table2: string;
    condition: string;
  }>;
  order_by?: Array<{
    column: string;
    direction: string;
  }>;
  group_by?: string[];
  limit?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  semantic_sql?: SemanticSQL;
  sql_query?: string;
  execution_result?: SQLExecutionResult;
  debug_info?: {
    request?: ChatRequest;
    response?: ChatResponse;
    ollama?: Record<string, any>;
    sql_execution?: SQLExecutionResult | null;
  };
}

export interface ChatRequest {
  message: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface ChatResponse {
  response: string;
  sql_query?: string;
  semantic_sql?: SemanticSQL;
  conversation_id: string;
  execution_result?: SQLExecutionResult;
  debug_ollama?: Record<string, any>;
}

export interface SQLExecutionRequest {
  sql_query: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface SQLExecutionResult {
  success: boolean;
  data?: Array<Record<string, any>>;
  error?: string;
  row_count?: number;
}

export interface DatabaseSchema {
  [tableName: string]: Array<{
    Field: string;
    Type: string;
    Null: string;
    Key: string;
    Default: any;
    Extra: string;
  }>;
}

// 数据库后台管理相关类型
export interface DatabaseConnection {
  id?: string;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  database_name: string;
  charset_name: string;
  description?: string;
  is_active: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface DatabaseConnectionCreate {
  name: string;
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
  description?: string;
}

export interface DatabaseConnectionUpdate {
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  database_name?: string;
  charset_name?: string;
  description?: string;
  is_active?: boolean;
}

export interface DatabaseConnectionTest {
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
}

export interface TableInfo {
  table_name: string;
  table_comment?: string;
  table_rows?: number;
  table_size?: string;
  engine?: string;
  charset_name?: string;
}

export interface ColumnInfo {
  column_name: string;
  data_type: string;
  is_nullable: boolean;
  column_key?: string;
  column_default?: string;
  extra?: string;
  column_comment?: string;
  column_order: number;
}

export interface TableSchema {
  table_name: string;
  table_comment?: string;
  columns: ColumnInfo[];
}

export interface CommentUpdate {
  table_name: string;
  column_name?: string;
  comment: string;
}

export interface UserToken {
  userId?: string;
  userName?: string;
  roleNames?: string[];
}

// 会话与消息（持久化）
export interface PersistedChatSession {
  id: number;
  userId: string;
  title?: string | null;
  archived: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PersistedChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'system';
  content: string;
  createdAt: string;
  semanticSql?: any;
  sqlQuery?: string;
  executionResult?: any;
  debugInfo?: any;
}

```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      let parsedDebug: any = m.debugInfo;
      if (typeof parsedDebug === 'string') {
        try { parsedDebug = JSON.parse(parsedDebug); } catch {}
      }
      const debug_info = parsedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
        debug_info,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- [查询最近10条订单](#query)',
          '- [统计每个城市的用户数量](#query)',
          '- [查看任意表的前10行数据](#query)',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- [查看 ${name} 表的前10行数据](#query)`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

我已让“历史消息”也能看到查询结果和调试信息，并确保执行后会话里保存这些详情：

Backend
- 持久化字段拓展
  - 在 `chat_message` 表新增 `debug_info`，原有 `execution_result`/`semantic_sql`/`sql_query` 已存在。
  - 更新 `ChatMessage` 实体，新增 `debugInfo`。
- 保存详细信息
  - 生成回复时：`ChatController` 通过增强版 `appendAssistantMessage(...)` 同时保存 `semanticSql`、`sqlQuery`、`executionResult`、`debugOllama`。
  - 执行 SQL 时：`/api/execute-sql` 执行成功后，将结果写回该会话最后一条助手消息的 `execution_result`，保证历史记录里能看到表格与调试卡片。
    - 解析 `conversation_id` 为会话 ID，更新该会话的最后一条 assistant 消息。

Frontend
- 历史加载解析
  - `App.tsx` 在加载历史时，解析后端返回的 `semanticSql`、`executionResult`、`debugInfo`（若为 JSON 字符串则 JSON.parse），并映射到现有渲染字段：
    - `semantic_sql`、`sql_query`、`execution_result`、`debug_info`。
  - 若无 `debugInfo` 但有 `executionResult`，会把其挂到 `debug_info.sql_execution`，使“调试信息”折叠面板中也显示执行结果。
- 渲染侧已有支持
  - `ChatMessage.tsx` 已支持显示 SQL 结果（表/图）与“调试信息”折叠面板，历史消息现在同样生效。

这样，切换会话或刷新后，历史消息会展示当时生成的 SQL、执行结果、以及调试信息。

---

**User**

展开会话时自动刷新

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);
  const [sessionListRefresh, setSessionListRefresh] = useState(0);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      let parsedDebug: any = m.debugInfo;
      if (typeof parsedDebug === 'string') {
        try { parsedDebug = JSON.parse(parsedDebug); } catch {}
      }
      const debug_info = parsedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
        debug_info,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- [查询最近10条订单](#query)',
          '- [统计每个城市的用户数量](#query)',
          '- [查看任意表的前10行数据](#query)',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- [查看 ${name} 表的前10行数据](#query)`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => {
              const willExpand = sidebarCollapsed;
              setSidebarCollapsed(!sidebarCollapsed);
              if (willExpand) {
                setSessionListRefresh(prev => prev + 1);
              }
            }}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            key={sessionListRefresh}
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已实现展开会话时自动刷新：

- 在 `App.tsx` 中增加 `sessionListRefresh` 计数。
- 点击侧栏折叠按钮时，若是从折叠到展开，会自增 `sessionListRefresh`。
- 将 `sessionListRefresh` 作为 `SessionList` 的 `key`，使展开时组件重新挂载，从而触发 `SessionList` 内部的 `loadSessions()` 拉取最新列表。

关键改动：
```jsx
// state
const [sessionListRefresh, setSessionListRefresh] = useState(0);

// toggle collapse
onClick={() => {
  const willExpand = sidebarCollapsed;
  setSidebarCollapsed(!sidebarCollapsed);
  if (willExpand) {
    setSessionListRefresh(prev => prev + 1);
  }
}}

// SessionList
<SessionList
  key={sessionListRefresh}
  selectedSessionId={currentSessionId}
  onSelect={handleSelectSession}
  canDelete={userPermissions.role === 'ADMIN'}
/>
```

展开左侧会话列表时，会自动刷新会话数据。

```javascriptreact
// state
const [sessionListRefresh, setSessionListRefresh] = useState(0);

// toggle collapse
onClick={() => {
  const willExpand = sidebarCollapsed;
  setSidebarCollapsed(!sidebarCollapsed);
  if (willExpand) {
    setSessionListRefresh(prev => prev + 1);
  }
}}

// SessionList
<SessionList
  key={sessionListRefresh}
  selectedSessionId={currentSessionId}
  onSelect={handleSelectSession}
  canDelete={userPermissions.role === 'ADMIN'}
/>
```

---

**User**

历史消息 调试信息没有正确显示

---

**Cursor**

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);
  const [sessionListRefresh, setSessionListRefresh] = useState(0);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      let parsedDebug: any = m.debugInfo;
      if (typeof parsedDebug === 'string') {
        try { parsedDebug = JSON.parse(parsedDebug); } catch {}
      }
      // 兼容历史存储：如果 debugInfo 直接是 ollama 字段集合，则包一层 { ollama: ... }
      let normalizedDebug: any = parsedDebug;
      if (parsedDebug && typeof parsedDebug === 'object' && !('ollama' in parsedDebug)) {
        const possibleOllamaKeys = ['provider','model','base_url','prompt','raw_response','error'];
        const hasOllamaShape = possibleOllamaKeys.some(k => k in parsedDebug);
        if (hasOllamaShape) {
          normalizedDebug = { ollama: parsedDebug };
        }
      }
      const debug_info = normalizedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
        debug_info,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- [查询最近10条订单](#query)',
          '- [统计每个城市的用户数量](#query)',
          '- [查看任意表的前10行数据](#query)',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- [查看 ${name} 表的前10行数据](#query)`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => {
              const willExpand = sidebarCollapsed;
              setSidebarCollapsed(!sidebarCollapsed);
              if (willExpand) {
                setSessionListRefresh(prev => prev + 1);
              }
            }}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            key={sessionListRefresh}
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;

```

已修复历史消息的调试信息显示问题。

原因
- 历史里保存的 debugInfo 是直接存放的 Ollama 字段集合（如 provider/model/prompt），但前端渲染组件期望结构为 message.debug_info.ollama。
- 因此旧记录没有 ollama 包裹层，导致“调试信息”面板无法识别。

修复点
- 前端历史映射时自动规范化 debugInfo：
  - 若 debugInfo 是 JSON 字符串则先 JSON.parse。
  - 若对象中不含 ollama，但包含 provider/model/base_url/prompt/raw_response/error 任一键，则包裹为 { ollama: debugInfo }。
  - 若有 executionResult 但无 debugInfo，则构造 { sql_execution: executionResult } 以便面板显示执行结果。

关键代码
```500:520:chatbi-ui/src/App.tsx
let parsedDebug: any = m.debugInfo;
if (typeof parsedDebug === 'string') {
  try { parsedDebug = JSON.parse(parsedDebug); } catch {}
}
// 兼容历史：无 ollama 包裹但有 ollama 形状字段时，自动包裹
let normalizedDebug: any = parsedDebug;
if (parsedDebug && typeof parsedDebug === 'object' && !('ollama' in parsedDebug)) {
  const possibleOllamaKeys = ['provider','model','base_url','prompt','raw_response','error'];
  const hasOllamaShape = possibleOllamaKeys.some(k => k in parsedDebug);
  if (hasOllamaShape) {
    normalizedDebug = { ollama: parsedDebug };
  }
}
const debug_info = normalizedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
```

效果
- 历史消息的“调试信息”折叠面板现在能正确显示 Ollama 信息和 SQL 执行结果。

```typescriptreact
let parsedDebug: any = m.debugInfo;
if (typeof parsedDebug === 'string') {
  try { parsedDebug = JSON.parse(parsedDebug); } catch {}
}
// 兼容历史：无 ollama 包裹但有 ollama 形状字段时，自动包裹
let normalizedDebug: any = parsedDebug;
if (parsedDebug && typeof parsedDebug === 'object' && !('ollama' in parsedDebug)) {
  const possibleOllamaKeys = ['provider','model','base_url','prompt','raw_response','error'];
  const hasOllamaShape = possibleOllamaKeys.some(k => k in parsedDebug);
  if (hasOllamaShape) {
    normalizedDebug = { ollama: parsedDebug };
  }
}
const debug_info = normalizedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
```

---

**User**

删除左边的蓝色新建会话按钮

---

**Cursor**

```typescriptreact
import React, { useEffect, useState } from 'react';
import { List, Button, Input, Modal, Space, Typography } from 'antd';
import { EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { PersistedChatSession } from '../types';
import { sessionAPI } from '../services/api';

interface SessionListProps {
  selectedSessionId?: number | null;
  onSelect: (sessionId: number) => void;
  canDelete?: boolean;
}

const SessionList: React.FC<SessionListProps> = ({ selectedSessionId, onSelect, canDelete }) => {
  const [sessions, setSessions] = useState<PersistedChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [renaming, setRenaming] = useState<PersistedChatSession | null>(null);
  const [newTitle, setNewTitle] = useState('');

  const loadSessions = async () => {
    setLoading(true);
    try {
      const list = await sessionAPI.listSessions();
      setSessions(list);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  

  const handleRename = async () => {
    if (!renaming) return;
    const updated = await sessionAPI.renameSession(renaming.id, newTitle || renaming.title || '会话');
    setSessions(prev => prev.map(s => (s.id === updated.id ? updated : s)));
    setRenaming(null);
    setNewTitle('');
  };

  const handleDelete = async (id: number) => {
    await sessionAPI.deleteSession(id);
    setSessions(prev => prev.filter(s => s.id !== id));
  };

  return (
    <div style={{ padding: 16 }}>
      <List
        loading={loading}
        dataSource={sessions}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', background: selectedSessionId === item.id ? '#e6f7ff' : undefined }}
            onClick={() => onSelect(item.id)}
            actions={[
              <Button key="rename" type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setRenaming(item); setNewTitle(item.title || ''); }} />,
              canDelete ? (
                <Button key="delete" type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); handleDelete(item.id); }} />
              ) : null,
            ].filter(Boolean) as any}
          >
            <List.Item.Meta
              title={<Typography.Text ellipsis>{item.title || '未命名会话'}</Typography.Text>}
              description={new Date(item.updatedAt).toLocaleString()}
            />
          </List.Item>
        )}
      />

      <Modal
        title="重命名会话"
        open={!!renaming}
        onOk={handleRename}
        onCancel={() => { setRenaming(null); setNewTitle(''); }}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="请输入会话标题"
          maxLength={50}
        />
      </Modal>
    </div>
  );
};

export default SessionList;



```

已移除左侧会话列表顶部的蓝色“新建会话”按钮，并清理相关代码。当前创建新会话可通过底部输入区的“新建会话”按钮完成。

