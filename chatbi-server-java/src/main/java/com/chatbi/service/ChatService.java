package com.chatbi.service;

import com.chatbi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    
    @Autowired
    private SemanticSQLConverter semanticSQLConverter;
    
    @Autowired
    private MySQLSQLGenerator mysqlSQLGenerator;
    
    @Autowired
    private DatabaseManager databaseManager;
    
    @Autowired
    private DatabaseConnectionService databaseConnectionService;
    
    private final Map<String, List<Map<String, Object>>> conversations = new ConcurrentHashMap<>();

    public ChatResponse processChatMessage(ChatRequest request) {
        try {
            logger.info("Incoming chat: conversation_id={}, message={}", 
                request.getConversationId(), request.getMessage());
            
            // Get or create conversation ID
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.trim().isEmpty()) {
                conversationId = UUID.randomUUID().toString();
            }
            
            // Get database connection
            DatabaseConnection selectedConnection = getSelectedConnection(request.getDatabaseConnectionId());
            
            // Initialize conversation history
            if (!conversations.containsKey(conversationId)) {
                conversations.put(conversationId, new ArrayList<>());
            }
            
            // Add user message to conversation history
            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", request.getMessage());
            conversations.get(conversationId).add(userMessage);
            
            // Convert natural language to semantic SQL (with short context: include previous one user input if exists)
            logger.info("Converting NL to semantic SQL: cid={}", conversationId);
            String shortContextInput = request.getMessage();
            List<Map<String, Object>> history = conversations.get(conversationId);
            if (history != null && history.size() >= 2) {
                // Find the most recent previous user message before the current one
                for (int i = history.size() - 2; i >= 0; i--) {
                    Object role = history.get(i).get("role");
                    if (role != null && "user".equals(role.toString())) {
                        Object prevContent = history.get(i).get("content");
                        if (prevContent != null) {
                            shortContextInput = "上一次用户输入（供参考）：" + prevContent.toString() + "\n当前用户输入：" + request.getMessage();
                        }
                        break;
                    }
                }
            }
            SemanticSQL semanticSQL = semanticSQLConverter.convertToSemanticSQL(shortContextInput, selectedConnection);
            Map<String, Object> debugOllama = semanticSQLConverter.getLastDebug();
            
            // Generate MySQL SQL statement
            String mysqlSQL = mysqlSQLGenerator.generateMySQLSQL(semanticSQL);
            logger.info("Generated MySQL SQL: cid={} sql={}", conversationId, mysqlSQL);
            
            // Generate response message
            String responseMessage = generateResponseMessage(request.getMessage(), semanticSQL, mysqlSQL);
            
            // Add assistant reply to conversation history
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", responseMessage);
            assistantMessage.put("semantic_sql", semanticSQL);
            assistantMessage.put("mysql_sql", mysqlSQL);
            conversations.get(conversationId).add(assistantMessage);
            
            return new ChatResponse(
                responseMessage,
                mysqlSQL,
                semanticSQL,
                conversationId,
                null,
                debugOllama
            );
            
        } catch (Exception e) {
            logger.error("process_chat_message error: {}", e.getMessage(), e);
            String errorMessage = "处理消息时发生错误: " + e.getMessage();
            Map<String, Object> debugOllama = semanticSQLConverter.getLastDebug();
            
            return new ChatResponse(
                errorMessage,
                null,
                null,
                request.getConversationId() != null ? request.getConversationId() : UUID.randomUUID().toString(),
                null,
                debugOllama
            );
        }
    }

    public Map<String, Object> executeSqlAndUpdateResponse(String conversationId, String sql) {
        try {
            // Get current active database connection
            DatabaseConnection activeConnection = databaseConnectionService.getActiveConnection();
            
            // Execute SQL query
            SQLExecutionResponse executionResult;
            if (activeConnection != null) {
                executionResult = databaseManager.executeQuery(sql, activeConnection);
            } else {
                executionResult = databaseManager.executeQuery(sql);
            }
            
            // Update the last assistant message in conversation history
            if (conversations.containsKey(conversationId) && 
                !conversations.get(conversationId).isEmpty()) {
                
                List<Map<String, Object>> history = conversations.get(conversationId);
                Map<String, Object> lastMessage = history.get(history.size() - 1);
                
                if ("assistant".equals(lastMessage.get("role"))) {
                    lastMessage.put("execution_result", executionResult);
                }
            }
            
            return Map.of(
                "success", executionResult.isSuccess(),
                "data", executionResult.getData(),
                "error", executionResult.getError(),
                "row_count", executionResult.getRowCount()
            );
            
        } catch (Exception e) {
            logger.error("Error executing SQL and updating response: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "row_count", 0
            );
        }
    }

    public List<Map<String, Object>> getConversationHistory(String conversationId) {
        return conversations.getOrDefault(conversationId, new ArrayList<>());
    }

    public void clearConversation(String conversationId) {
        conversations.remove(conversationId);
    }

    private DatabaseConnection getSelectedConnection(String databaseConnectionId) {
        if (databaseConnectionId != null && !databaseConnectionId.trim().isEmpty()) {
            Optional<DatabaseConnection> connection = databaseConnectionService.getConnection(databaseConnectionId);
            if (connection.isPresent()) {
                logger.info("Using specified database connection: {} ({})", 
                    connection.get().getName(), connection.get().getId());
                return connection.get();
            } else {
                logger.warn("Database connection not found: {}", databaseConnectionId);
            }
        }
        
        // Use default connection
        DatabaseConnection activeConnection = databaseConnectionService.getActiveConnection();
        logger.info("Using default database connection: {}", 
            activeConnection != null ? activeConnection.getName() : "None");
        return activeConnection;
    }

    private String generateResponseMessage(String userMessage, SemanticSQL semanticSQL, String mysqlSQL) {
        // Check if it's a query request
        String lowerMessage = userMessage.toLowerCase();
        if (lowerMessage.contains("查询") || lowerMessage.contains("显示") || 
            lowerMessage.contains("获取") || lowerMessage.contains("找出") || 
            lowerMessage.contains("统计") || lowerMessage.contains("计算") || 
            lowerMessage.contains("求和") || lowerMessage.contains("平均") || 
            lowerMessage.contains("最大") || lowerMessage.contains("最小")) {
            
            StringBuilder response = new StringBuilder();
            response.append("我已经将您的查询转换为SQL语句：\n\n");
            response.append("**语义SQL结构：**\n");
            response.append("- 涉及表: ").append(
                semanticSQL.getTables() != null && !semanticSQL.getTables().isEmpty() 
                    ? String.join(", ", semanticSQL.getTables()) 
                    : "未指定"
            ).append("\n");
            response.append("- 查询列: ").append(
                semanticSQL.getColumns() != null && !semanticSQL.getColumns().isEmpty() 
                    ? String.join(", ", semanticSQL.getColumns()) 
                    : "所有列"
            ).append("\n");
            
            if (semanticSQL.getConditions() != null && !semanticSQL.getConditions().isEmpty()) {
                response.append("- 筛选条件: ").append(semanticSQL.getConditions().size()).append("个条件\n");
            }
            
            if (semanticSQL.getAggregations() != null && !semanticSQL.getAggregations().isEmpty()) {
                response.append("- 聚合函数: ").append(semanticSQL.getAggregations().size()).append("个\n");
            }
            
            if (semanticSQL.getJoins() != null && !semanticSQL.getJoins().isEmpty()) {
                response.append("- 表连接: ").append(semanticSQL.getJoins().size()).append("个\n");
            }
            
            response.append("\n**生成的MySQL SQL：**\n```sql\n").append(mysqlSQL).append("\n```\n\n");
            response.append("您想要执行这个SQL查询吗？");
            
            return response.toString();
        } else {
            return "我理解您想要：" + userMessage + "\n\n" +
                   "我已经生成了相应的SQL查询语句。您需要我执行查询并返回结果吗？";
        }
    }
}