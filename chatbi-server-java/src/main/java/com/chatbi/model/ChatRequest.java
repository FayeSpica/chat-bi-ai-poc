package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
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
}