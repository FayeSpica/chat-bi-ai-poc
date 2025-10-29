package com.chatbi.controller;

import com.chatbi.annotation.EnableAuth;
import com.chatbi.model.*;
import com.chatbi.service.DatabaseAdminService;
import com.chatbi.service.DatabaseConnectionService;
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
@RequestMapping("/api/admin")
@EnableAuth  // 示例：整个Controller下的所有接口都需要token验证
public class DatabaseAdminController {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseAdminController.class);
    
    @Autowired
    private DatabaseConnectionService databaseConnectionService;
    
    @Autowired
    private DatabaseAdminService databaseAdminService;

    @GetMapping("/databases")
    public ResponseEntity<List<DatabaseConnection>> getDatabaseConnections() {
        try {
            List<DatabaseConnection> connections = databaseConnectionService.getAllConnections();
            return ResponseEntity.ok(connections);
        } catch (Exception e) {
            logger.error("Error getting database connections: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库连接配置时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/databases/active")
    public ResponseEntity<DatabaseConnection> getActiveDatabaseConnection() {
        try {
            DatabaseConnection connection = databaseConnectionService.getActiveConnection();
            if (connection == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(connection);
        } catch (Exception e) {
            logger.error("Error getting active database connection: {}", e.getMessage(), e);
            throw new RuntimeException("获取活动数据库连接时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/databases")
    public ResponseEntity<DatabaseConnection> createDatabaseConnection(@Valid @RequestBody DatabaseConnectionCreate connData) {
        try {
            DatabaseConnection connection = databaseConnectionService.createConnection(connData);
            return ResponseEntity.ok(connection);
        } catch (Exception e) {
            logger.error("Error creating database connection: {}", e.getMessage(), e);
            throw new RuntimeException("创建数据库连接时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/databases/{connectionId}")
    public ResponseEntity<DatabaseConnection> getDatabaseConnection(@PathVariable String connectionId) {
        try {
            Optional<DatabaseConnection> connection = databaseConnectionService.getConnection(connectionId);
            if (connection.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(connection.get());
        } catch (Exception e) {
            logger.error("Error getting database connection: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库连接时发生错误: " + e.getMessage());
        }
    }

    @PutMapping("/databases/{connectionId}")
    public ResponseEntity<DatabaseConnection> updateDatabaseConnection(
            @PathVariable String connectionId, 
            @Valid @RequestBody DatabaseConnectionUpdate updateData) {
        try {
            DatabaseConnection connection = databaseConnectionService.updateConnection(connectionId, updateData);
            if (connection == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(connection);
        } catch (Exception e) {
            logger.error("Error updating database connection: {}", e.getMessage(), e);
            throw new RuntimeException("更新数据库连接时发生错误: " + e.getMessage());
        }
    }

    @DeleteMapping("/databases/{connectionId}")
    public ResponseEntity<Map<String, String>> deleteDatabaseConnection(@PathVariable String connectionId) {
        try {
            boolean success = databaseConnectionService.deleteConnection(connectionId);
            if (!success) {
                // Check if connection exists
                Optional<DatabaseConnection> connection = databaseConnectionService.getConnection(connectionId);
                if (connection.isEmpty()) {
                    return ResponseEntity.notFound().build();
                } else {
                    return ResponseEntity.badRequest().body(Map.of("message", "Cannot delete the last database connection or default connection"));
                }
            }
            return ResponseEntity.ok(Map.of("message", "Database connection deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting database connection: {}", e.getMessage(), e);
            throw new RuntimeException("删除数据库连接时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/databases/test")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection(@Valid @RequestBody DatabaseConnectionTest testData) {
        try {
            Map<String, Object> result = databaseConnectionService.testConnection(testData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error testing database connection: {}", e.getMessage(), e);
            throw new RuntimeException("测试数据库连接时发生错误: " + e.getMessage());
        }
    }

    @GetMapping("/databases/{connectionId}/tables")
    public ResponseEntity<List<TableInfo>> getDatabaseTables(@PathVariable String connectionId) {
        try {
            List<TableInfo> tables = databaseAdminService.getTables(connectionId);
            return ResponseEntity.ok(tables);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("数据库连接失败")) {
                return ResponseEntity.status(503).body(List.of());
            }
            return ResponseEntity.status(500).body(List.of());
        } catch (Exception e) {
            logger.error("Error getting database tables: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库表时发生未知错误: " + e.getMessage());
        }
    }

    @GetMapping("/databases/{connectionId}/tables/{tableName}/schema")
    public ResponseEntity<TableSchema> getTableSchema(
            @PathVariable String connectionId, 
            @PathVariable String tableName) {
        try {
            TableSchema schema = databaseAdminService.getTableSchema(connectionId, tableName);
            return ResponseEntity.ok(schema);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("数据库连接失败")) {
                return ResponseEntity.status(503).build();
            }
            return ResponseEntity.status(500).build();
        } catch (Exception e) {
            logger.error("Error getting table schema: {}", e.getMessage(), e);
            throw new RuntimeException("获取表结构时发生未知错误: " + e.getMessage());
        }
    }

    @PutMapping("/databases/{connectionId}/comments")
    public ResponseEntity<Map<String, String>> updateComment(
            @PathVariable String connectionId, 
            @Valid @RequestBody CommentUpdate commentUpdate) {
        try {
            boolean success = databaseAdminService.updateComment(connectionId, commentUpdate);
            if (success) {
                return ResponseEntity.ok(Map.of("message", "Comment updated successfully"));
            } else {
                return ResponseEntity.status(500).body(Map.of("message", "Failed to update comment"));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error updating comment: {}", e.getMessage(), e);
            throw new RuntimeException("更新注释时发生错误: " + e.getMessage());
        }
    }

    @PostMapping("/databases/{connectionId}/execute-sql")
    public ResponseEntity<Map<String, Object>> executeCustomSql(
            @PathVariable String connectionId, 
            @RequestBody Map<String, String> sqlRequest) {
        try {
            String sql = sqlRequest.get("sql");
            if (sql == null || sql.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "SQL query is required"));
            }
            
            Map<String, Object> result = databaseAdminService.executeCustomSql(connectionId, sql);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error executing custom SQL: {}", e.getMessage(), e);
            throw new RuntimeException("执行自定义SQL时发生错误: " + e.getMessage());
        }
    }
}