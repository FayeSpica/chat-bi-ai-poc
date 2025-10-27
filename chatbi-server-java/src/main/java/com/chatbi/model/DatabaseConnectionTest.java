package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DatabaseConnectionTest {
    @NotBlank(message = "Host is required")
    private String host;
    
    @NotNull(message = "Port is required")
    @Positive(message = "Port must be positive")
    private Integer port = 3306;
    
    @NotBlank(message = "Username is required")
    private String username;
    
    @NotBlank(message = "Password is required")
    private String password;
    
    @NotBlank(message = "Database is required")
    @JsonProperty("database_name")
    private String databaseName;
    
    @JsonProperty("charset_name")
    private String charsetName = "utf8mb4";

    // Constructors
    public DatabaseConnectionTest() {}

    public DatabaseConnectionTest(String host, Integer port, String username, 
                                 String password, String databaseName, String charsetName) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
        this.charsetName = charsetName;
    }

    // Getters and Setters
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }
}