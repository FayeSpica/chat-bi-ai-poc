package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SQLExecutionRequest {
    @NotBlank(message = "SQL query is required")
    @JsonProperty("sql_query")
    private String sqlQuery;
    
    @JsonProperty("conversation_id")
    private String conversationId;
    
    @JsonProperty("database_connection_id")
    private String databaseConnectionId;

    // Constructors
    public SQLExecutionRequest() {}

    public SQLExecutionRequest(String sqlQuery, String conversationId, String databaseConnectionId) {
        this.sqlQuery = sqlQuery;
        this.conversationId = conversationId;
        this.databaseConnectionId = databaseConnectionId;
    }
}