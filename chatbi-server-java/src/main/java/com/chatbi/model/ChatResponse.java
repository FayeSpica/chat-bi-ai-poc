package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

@Data
public class ChatResponse {
    private String response;
    
    @JsonProperty("sql_query")
    private String sqlQuery;
    
    @JsonProperty("semantic_sql")
    private SemanticSQL semanticSql;
    
    @JsonProperty("conversation_id")
    private String conversationId;
    
    @JsonProperty("execution_result")
    private Map<String, Object> executionResult;
    
    @JsonProperty("debug_ollama")
    private Map<String, Object> debugOllama;

    // Constructors
    public ChatResponse() {}

    public ChatResponse(String response, String sqlQuery, SemanticSQL semanticSql, 
                       String conversationId, Map<String, Object> executionResult, 
                       Map<String, Object> debugOllama) {
        this.response = response;
        this.sqlQuery = sqlQuery;
        this.semanticSql = semanticSql;
        this.conversationId = conversationId;
        this.executionResult = executionResult;
        this.debugOllama = debugOllama;
    }
}