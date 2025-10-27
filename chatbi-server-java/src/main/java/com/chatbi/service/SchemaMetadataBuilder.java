package com.chatbi.service;

import com.chatbi.model.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchemaMetadataBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SchemaMetadataBuilder.class);
    private static final int SAMPLE_ROWS_PER_TABLE = 5;
    
    @Autowired
    private DatabaseManager databaseManager;
    
    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    public Map<String, Object> buildDatabaseMetadata(DatabaseConnection databaseConnection) {
        List<String> tables = databaseManager.getAllTables(databaseConnection);
        
        Map<String, Object> metadata = new HashMap<>();
        
        // Database info
        Map<String, String> dbInfo = new HashMap<>();
        if (databaseConnection != null) {
            dbInfo.put("host", databaseConnection.getHost());
            dbInfo.put("name", databaseConnection.getDatabase());
        } else {
            // Use default configuration
            dbInfo.put("host", "localhost");
            dbInfo.put("name", "test_db");
        }
        metadata.put("db", dbInfo);
        
        // Tables metadata
        Map<String, Object> tablesMetadata = new HashMap<>();
        for (String tableName : tables) {
            try {
                Map<String, Object> tableMetadata = buildTableMetadata(tableName, databaseConnection);
                tablesMetadata.put(tableName, tableMetadata);
            } catch (Exception e) {
                logger.warn("Failed to build metadata for table {}: {}", tableName, e.getMessage());
            }
        }
        metadata.put("tables", tablesMetadata);
        
        return metadata;
    }

    private Map<String, Object> buildTableMetadata(String tableName, DatabaseConnection databaseConnection) {
        Map<String, Object> tableMetadata = new HashMap<>();
        
        // Get table comment
        String tableComment = getTableComment(tableName, databaseConnection);
        tableMetadata.put("comment", tableComment);
        
        // Get columns with comments
        List<Map<String, Object>> columns = getColumnsWithComments(tableName, databaseConnection);
        
        // Get sample rows
        List<Map<String, Object>> sampleRows = getSampleRows(tableName, SAMPLE_ROWS_PER_TABLE, databaseConnection);
        
        // Extract sample values for each column
        Map<String, List<Object>> columnSamples = new HashMap<>();
        for (Map<String, Object> column : columns) {
            String columnName = (String) column.get("name");
            columnSamples.put(columnName, new ArrayList<>());
        }
        
        for (Map<String, Object> row : sampleRows) {
            for (Map<String, Object> column : columns) {
                String columnName = (String) column.get("name");
                if (row.containsKey(columnName)) {
                    Object value = row.get(columnName);
                    List<Object> samples = columnSamples.get(columnName);
                    if (!samples.contains(value) && samples.size() < 5) {
                        samples.add(value);
                    }
                }
            }
        }
        
        // Add samples to columns
        for (Map<String, Object> column : columns) {
            String columnName = (String) column.get("name");
            column.put("samples", columnSamples.get(columnName));
        }
        
        tableMetadata.put("columns", columns);
        tableMetadata.put("samples", sampleRows);
        
        return tableMetadata;
    }

    private String getTableComment(String tableName, DatabaseConnection databaseConnection) {
        try {
            String sql = """
                SELECT table_comment FROM information_schema.tables 
                WHERE table_schema=? AND table_name=?
            """;
            
            String dbName = databaseConnection != null ? databaseConnection.getDatabase() : "test_db";
            return databaseManager.getJdbcTemplate().queryForObject(sql, String.class, dbName, tableName);
        } catch (Exception e) {
            logger.debug("Failed to get table comment for {}: {}", tableName, e.getMessage());
            return "";
        }
    }

    private List<Map<String, Object>> getColumnsWithComments(String tableName, DatabaseConnection databaseConnection) {
        try {
            String sql = """
                SELECT column_name, column_type, is_nullable, column_key, column_default, extra, column_comment 
                FROM information_schema.columns 
                WHERE table_schema=? AND table_name=? 
                ORDER BY ordinal_position
            """;
            
            String dbName = databaseConnection != null ? databaseConnection.getDatabase() : "test_db";
            return databaseManager.getJdbcTemplate().queryForList(sql, dbName, tableName);
        } catch (Exception e) {
            logger.debug("Failed to get columns for {}: {}", tableName, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> getSampleRows(String tableName, int limit, DatabaseConnection databaseConnection) {
        try {
            String sql = "SELECT * FROM " + tableName + " LIMIT " + limit;
            return databaseManager.getJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            logger.debug("Failed to get sample rows for {}: {}", tableName, e.getMessage());
            return new ArrayList<>();
        }
    }

    public String summarizeMetadataForPrompt(Map<String, Object> metadata) {
        try {
            StringBuilder result = new StringBuilder();
            
            @SuppressWarnings("unchecked")
            Map<String, Object> tables = (Map<String, Object>) metadata.get("tables");
            
            for (Map.Entry<String, Object> tableEntry : tables.entrySet()) {
                String tableName = tableEntry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> table = (Map<String, Object>) tableEntry.getValue();
                
                String comment = (String) table.get("comment");
                if (comment == null || comment.trim().isEmpty()) {
                    comment = inferTableMeaning(tableName);
                }
                if (comment.length() > 60) {
                    comment = comment.substring(0, 57) + "...";
                }
                
                result.append("- 表 ").append(tableName).append(": ").append(comment).append("\n");
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");
                if (columns != null && !columns.isEmpty()) {
                    List<String> columnLines = new ArrayList<>();
                    for (int i = 0; i < Math.min(columns.size(), 12); i++) {
                        Map<String, Object> column = columns.get(i);
                        String columnName = (String) column.get("name");
                        String columnType = (String) column.get("type");
                        String columnComment = (String) column.get("comment");
                        if (columnComment == null || columnComment.trim().isEmpty()) {
                            columnComment = inferColumnMeaning(columnName);
                        }
                        if (columnComment.length() > 40) {
                            columnComment = columnComment.substring(0, 37) + "...";
                        }
                        
                        @SuppressWarnings("unchecked")
                        List<Object> samples = (List<Object>) column.get("samples");
                        String samplePart = "";
                        if (samples != null && !samples.isEmpty()) {
                            String preview = samples.stream()
                                .limit(2)
                                .map(Object::toString)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                            samplePart = "，样例: " + preview;
                        }
                        
                        columnLines.add(columnName + "(" + columnType + "): " + columnComment + samplePart);
                    }
                    if (!columnLines.isEmpty()) {
                        result.append("  字段: ").append(String.join("; ", columnLines)).append("\n");
                    }
                }
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> samples = (List<Map<String, Object>>) table.get("samples");
                if (samples != null && !samples.isEmpty()) {
                    Map<String, Object> firstSample = samples.get(0);
                    String sampleStr = firstSample.entrySet().stream()
                        .limit(3)
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
                    result.append("  样例: ").append(sampleStr).append("\n");
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            logger.warn("Failed to summarize metadata: {}", e.getMessage());
            return "(metadata unavailable)";
        }
    }

    private String inferTableMeaning(String tableName) {
        String name = tableName.toLowerCase();
        if (name.contains("user")) {
            return "用户相关数据";
        }
        if (name.contains("order")) {
            return "订单/交易相关数据";
        }
        if (name.contains("product") || name.contains("item")) {
            return "商品/物品相关数据";
        }
        if (name.contains("log") || name.contains("event")) {
            return "日志/事件记录";
        }
        return "业务相关数据表";
    }

    private String inferColumnMeaning(String columnName) {
        if (columnName == null) return "字段含义未注明";
        
        String name = columnName.toLowerCase();
        if (name.equals("id") || name.endsWith("_id")) {
            return "主键/外键标识";
        }
        if (name.contains("name")) {
            return "名称/标题";
        }
        if (name.contains("email")) {
            return "电子邮箱";
        }
        if (name.contains("city") || name.contains("address")) {
            return "城市/地址";
        }
        if (name.contains("amount") || name.contains("total") || 
            name.contains("price") || name.contains("cost")) {
            return "金额/数值";
        }
        if (name.contains("qty") || name.contains("quantity") || name.contains("count")) {
            return "数量";
        }
        if (name.contains("date") || name.contains("time") || name.endsWith("_at")) {
            return "日期/时间";
        }
        if (name.contains("status") || name.contains("state")) {
            return "状态";
        }
        if (name.contains("category") || name.contains("type")) {
            return "类别/类型";
        }
        return "字段含义未注明";
    }
}