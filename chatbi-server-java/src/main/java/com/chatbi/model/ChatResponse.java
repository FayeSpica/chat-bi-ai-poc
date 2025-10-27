package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

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

    // Getters and Setters
    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    public SemanticSQL getSemanticSql() {
        return semanticSql;
    }

    public void setSemanticSql(SemanticSQL semanticSql) {
        this.semanticSql = semanticSql;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Map<String, Object> getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(Map<String, Object> executionResult) {
        this.executionResult = executionResult;
    }

    public Map<String, Object> getDebugOllama() {
        return debugOllama;
    }

    public void setDebugOllama(Map<String, Object> debugOllama) {
        this.debugOllama = debugOllama;
    }
}