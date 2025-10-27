package com.chatbi.service;

import org.springframework.beans.factory.annotation.Value;
import com.chatbi.model.DatabaseConnection;
import com.chatbi.model.SemanticSQL;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SemanticSQLConverter {
    private static final Logger logger = LoggerFactory.getLogger(SemanticSQLConverter.class);
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
    
    @Value("${langchain4j.ollama.base-url}")
    private String ollamaBaseUrl;
    
    @Value("${langchain4j.ollama.model-name}")
    private String ollamaModelName;
    
    @Value("${langchain4j.ollama.timeout}")
    private String ollamaTimeout;
    
    @Autowired
    private SchemaMetadataBuilder metadataBuilder;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private ChatLanguageModel llm;
    private Map<String, Object> lastDebug;

    public SemanticSQL convertToSemanticSQL(String naturalLanguage, DatabaseConnection databaseConnection) {
        try {
            initializeLlm();
            
            // Build database metadata
            Map<String, Object> metadata = metadataBuilder.buildDatabaseMetadata(databaseConnection);
            String metadataSummary = metadataBuilder.summarizeMetadataForPrompt(metadata);
            
            String systemPrompt = buildSystemPrompt();
            String prompt = systemPrompt + "\n\n数据库元数据:\n" + metadataSummary + "\n\n用户查询：" + naturalLanguage;
            
            logger.info("Invoking Ollama: base={} model={}", ollamaBaseUrl, ollamaModelName);
            
            String response = llm.generate(prompt);
            
            // Save debug information
            lastDebug = Map.of(
                "provider", "ollama",
                "base_url", ollamaBaseUrl,
                "model", ollamaModelName,
                "prompt", prompt,
                "raw_response", response
            );
            
            // Extract JSON from response
            Matcher matcher = JSON_PATTERN.matcher(response);
            if (!matcher.find()) {
                throw new IllegalArgumentException("无法从响应中提取JSON格式的语义SQL");
            }
            
            String jsonStr = matcher.group();
            SemanticSQL semanticSQL = objectMapper.readValue(jsonStr, SemanticSQL.class);
            
            return semanticSQL;
            
        } catch (Exception e) {
            logger.error("convertToSemanticSQL failed: {}", e.getMessage(), e);
            
            // Save error debug information
            lastDebug = Map.of(
                "provider", "ollama",
                "base_url", ollamaBaseUrl,
                "model", ollamaModelName,
                "error", e.getMessage()
            );
            
            // Return default semantic SQL structure
            return new SemanticSQL(
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null
            );
        }
    }

    public Map<String, Object> getLastDebug() {
        return lastDebug;
    }

    private void initializeLlm() {
        if (llm == null) {
            // Parse timeout string (e.g., "120s" -> 120 seconds)
            long timeoutSeconds = 120; // default
            if (ollamaTimeout != null && ollamaTimeout.endsWith("s")) {
                try {
                    timeoutSeconds = Long.parseLong(ollamaTimeout.substring(0, ollamaTimeout.length() - 1));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid timeout format: {}, using default 120s", ollamaTimeout);
                }
            }
            
            llm = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .temperature(0.1)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        }
    }

    private String buildSystemPrompt() {
        return """
            你是一个专业的SQL语义转换器。你的任务是将用户的自然语言查询转换为结构化的语义SQL JSON格式。

            输出格式必须是严格的JSON，包含以下字段：
            {
                "tables": ["表名1", "表名2"],
                "columns": ["列名1", "列名2", "聚合函数(列名)"],
                "conditions": [
                    {"column": "列名", "operator": "操作符", "value": "值", "table": "表名"}
                ],
                "aggregations": [
                    {"function": "聚合函数", "column": "列名", "alias": "别名"}
                ],
                "joins": [
                    {"type": "连接类型", "table1": "表1", "table2": "表2", "condition": "连接条件"}
                ],
                "order_by": [{"column": "列名", "direction": "ASC/DESC"}],
                "group_by": ["分组列"],
                "limit": 数量限制
            }

            支持的操作符：=, !=, >, <, >=, <=, LIKE, IN, BETWEEN
            支持的聚合函数：COUNT, SUM, AVG, MAX, MIN
            支持的连接类型：INNER, LEFT, RIGHT, FULL

            示例：
            用户输入："查询所有用户的订单总金额，按用户ID分组"
            输出：
            {
                "tables": ["users", "orders"],
                "columns": ["users.id", "SUM(orders.amount) as total_amount"],
                "conditions": [],
                "aggregations": [{"function": "SUM", "column": "orders.amount", "alias": "total_amount"}],
                "joins": [{"type": "INNER", "table1": "users", "table2": "orders", "condition": "users.id = orders.user_id"}],
                "order_by": [],
                "group_by": ["users.id"],
                "limit": null
            }

            请严格按照JSON格式输出，不要包含任何其他文字。

            以下是数据库的表结构和字段元数据（用于更好地理解用户意图并选择正确的表与字段）。
            仅将其用于理解上下文，不要直接把元数据内容复制到输出字段中。
            """;
    }

    public boolean validateSemanticSQL(SemanticSQL semanticSQL) {
        try {
            // Basic validation
            if (semanticSQL.getTables() == null || semanticSQL.getTables().isEmpty()) {
                return false;
            }
            
            // Validate aggregations
            if (semanticSQL.getAggregations() != null) {
                for (Map<String, String> agg : semanticSQL.getAggregations()) {
                    String function = agg.get("function");
                    if (function == null || !java.util.List.of("COUNT", "SUM", "AVG", "MAX", "MIN").contains(function)) {
                        return false;
                    }
                }
            }
            
            // Validate operators
            if (semanticSQL.getConditions() != null) {
                for (Map<String, Object> condition : semanticSQL.getConditions()) {
                    String operator = (String) condition.get("operator");
                    if (operator == null || !java.util.List.of("=", "!=", ">", "<", ">=", "<=", "LIKE", "IN", "BETWEEN").contains(operator)) {
                        return false;
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
}