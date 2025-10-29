package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.model.*;
import com.chatbi.service.ChatService;
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
@CrossOrigin(origins = "*")
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
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}", 
                request.getConversationId(), request.getMessage());
            
            ChatResponse response = chatService.processChatMessage(request);
            return ResponseEntity.ok(response);
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
}