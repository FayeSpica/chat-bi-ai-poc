package com.chatbi.service;

import com.chatbi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DatabaseAdminService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseAdminService.class);
    
    @Autowired
    private DatabaseConnectionService databaseConnectionService;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<TableInfo> getTables(String connectionId) {
        DatabaseConnection connection = databaseConnectionService.getConnection(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Database connection " + connectionId + " not found"));
        
        try (Connection conn = createConnection(connection)) {
            String sql = """
                SELECT 
                    TABLE_NAME as table_name,
                    TABLE_COMMENT as table_comment,
                    TABLE_ROWS as table_rows,
                    ROUND(((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024), 2) as table_size,
                    ENGINE as engine,
                    TABLE_COLLATION as charset
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME
            """;
            
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                TableInfo tableInfo = new TableInfo();
                tableInfo.setTableName(rs.getString("table_name"));
                tableInfo.setTableComment(rs.getString("table_comment"));
                tableInfo.setTableRows(rs.getInt("table_rows"));
                
                Object tableSize = rs.getObject("table_size");
                tableInfo.setTableSize(tableSize != null ? tableSize.toString() : null);
                
                tableInfo.setEngine(rs.getString("engine"));
                tableInfo.setCharset(rs.getString("charset"));
                return tableInfo;
            }, connection.getDatabaseName());
            
        } catch (SQLException e) {
            logger.error("Database connection error for {}: {}", connection.getName(), e.getMessage(), e);
            throw new RuntimeException("数据库连接失败: " + e.getMessage());
        }
    }

    public TableSchema getTableSchema(String connectionId, String tableName) {
        DatabaseConnection connection = databaseConnectionService.getConnection(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Database connection " + connectionId + " not found"));
        
        try (Connection conn = createConnection(connection)) {
            // Get table comment
            String tableCommentSql = """
                SELECT TABLE_COMMENT as table_comment
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
            """;
            
            String tableComment = jdbcTemplate.queryForObject(tableCommentSql, String.class, 
                connection.getDatabaseName(), tableName);
            
            // Get column information
            String columnsSql = """
                SELECT 
                    COLUMN_NAME as column_name,
                    DATA_TYPE as data_type,
                    IS_NULLABLE as is_nullable,
                    COLUMN_KEY as column_key,
                    COLUMN_DEFAULT as column_default,
                    EXTRA as extra,
                    COLUMN_COMMENT as column_comment,
                    ORDINAL_POSITION as column_order
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
            """;
            
            List<ColumnInfo> columns = jdbcTemplate.query(columnsSql, (rs, rowNum) -> {
                ColumnInfo columnInfo = new ColumnInfo();
                columnInfo.setColumnName(rs.getString("column_name"));
                columnInfo.setDataType(rs.getString("data_type"));
                columnInfo.setIsNullable("YES".equals(rs.getString("is_nullable")));
                columnInfo.setColumnKey(rs.getString("column_key"));
                columnInfo.setColumnDefault(rs.getString("column_default"));
                columnInfo.setExtra(rs.getString("extra"));
                columnInfo.setColumnComment(rs.getString("column_comment"));
                columnInfo.setColumnOrder(rs.getInt("column_order"));
                return columnInfo;
            }, connection.getDatabaseName(), tableName);
            
            TableSchema tableSchema = new TableSchema();
            tableSchema.setTableName(tableName);
            tableSchema.setTableComment(tableComment);
            tableSchema.setColumns(columns);
            
            return tableSchema;
            
        } catch (SQLException e) {
            logger.error("Database connection error for {}: {}", connection.getName(), e.getMessage(), e);
            throw new RuntimeException("数据库连接失败: " + e.getMessage());
        }
    }

    public boolean updateComment(String connectionId, CommentUpdate commentUpdate) {
        DatabaseConnection connection = databaseConnectionService.getConnection(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Database connection " + connectionId + " not found"));
        
        try (Connection conn = createConnection(connection)) {
            String sql;
            
            if (commentUpdate.getColumnName() != null) {
                // Update column comment
                String columnInfoSql = """
                    SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                    FROM information_schema.COLUMNS 
                    WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
                
                Map<String, Object> columnInfo = jdbcTemplate.queryForMap(columnInfoSql, 
                    connection.getDatabaseName(), commentUpdate.getTableName(), commentUpdate.getColumnName());
                
                String columnType = (String) columnInfo.get("COLUMN_TYPE");
                String isNullable = (String) columnInfo.get("IS_NULLABLE");
                String columnDefault = (String) columnInfo.get("COLUMN_DEFAULT");
                String extra = (String) columnInfo.get("EXTRA");
                
                String nullConstraint = "YES".equals(isNullable) ? "NULL" : "NOT NULL";
                String defaultConstraint = columnDefault != null ? "DEFAULT " + columnDefault : "";
                String extraConstraint = extra != null ? extra : "";
                
                sql = String.format("""
                    ALTER TABLE `%s` 
                    MODIFY COLUMN `%s` %s %s %s %s 
                    COMMENT '%s'
                """, commentUpdate.getTableName(), commentUpdate.getColumnName(), 
                    columnType, nullConstraint, defaultConstraint, extraConstraint, 
                    commentUpdate.getComment());
            } else {
                // Update table comment
                sql = String.format("""
                    ALTER TABLE `%s` 
                    COMMENT = '%s'
                """, commentUpdate.getTableName(), commentUpdate.getComment());
            }
            
            jdbcTemplate.execute(sql);
            return true;
            
        } catch (SQLException e) {
            logger.error("Error updating comment: {}", e.getMessage(), e);
            throw new RuntimeException("更新注释失败: " + e.getMessage());
        }
    }

    public Map<String, Object> executeCustomSql(String connectionId, String sql) {
        DatabaseConnection connection = databaseConnectionService.getConnection(connectionId)
            .orElseThrow(() -> new IllegalArgumentException("Database connection " + connectionId + " not found"));
        
        try (Connection conn = createConnection(connection)) {
            if (sql.trim().toUpperCase().startsWith("SELECT")) {
                List<Map<String, Object>> data = jdbcTemplate.queryForList(sql);
                return Map.of(
                    "success", true,
                    "data", data,
                    "row_count", data.size()
                );
            } else {
                int rowCount = jdbcTemplate.update(sql);
                return Map.of(
                    "success", true,
                    "data", null,
                    "row_count", rowCount
                );
            }
        } catch (Exception e) {
            logger.error("Error executing SQL: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "row_count", 0
            );
        }
    }

    public Optional<DatabaseConnection> getConnection(String connectionId) {
        return databaseConnectionService.getConnection(connectionId);
    }

    private Connection createConnection(DatabaseConnection connection) throws SQLException {
        String url = String.format("jdbc:mariadb://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false",
            connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getCharsetName());
        
        return DriverManager.getConnection(url, connection.getUsername(), connection.getPassword());
    }
}
