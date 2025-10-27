package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ChatRequest {
    @NotBlank(message = "Message is required")
    private String message;
    
    @JsonProperty("conversation_id")
    private String conversationId;
    
    @JsonProperty("database_connection_id")
    private String databaseConnectionId;

    // Constructors
    public ChatRequest() {}

    public ChatRequest(String message, String conversationId, String databaseConnectionId) {
        this.message = message;
        this.conversationId = conversationId;
        this.databaseConnectionId = databaseConnectionId;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getDatabaseConnectionId() {
        return databaseConnectionId;
    }

    public void setDatabaseConnectionId(String databaseConnectionId) {
        this.databaseConnectionId = databaseConnectionId;
    }
}