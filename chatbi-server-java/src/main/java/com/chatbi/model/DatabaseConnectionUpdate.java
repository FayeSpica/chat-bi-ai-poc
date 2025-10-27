package com.chatbi.model;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class DatabaseConnectionUpdate {
    private String name;
    private String host;
    @Positive(message = "Port must be positive")
    private Integer port;
    private String username;
    private String password;
    @JsonProperty("database_name")
    private String databaseName;
    @JsonProperty("charset_name")
    private String charsetName;
    private String description;
    private Boolean isActive;

    // Constructors
    public DatabaseConnectionUpdate() {}
}