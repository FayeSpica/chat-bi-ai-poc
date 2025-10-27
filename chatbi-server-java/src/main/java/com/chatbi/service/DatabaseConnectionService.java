package com.chatbi.service;

import com.chatbi.config.ChatbiProperties;
import com.chatbi.model.*;
import com.chatbi.repository.DatabaseConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionService.class);
    
    @Autowired
    private DatabaseConnectionRepository repository;
    
    @Autowired
    private ChatbiProperties properties;

    public DatabaseConnection createConnection(DatabaseConnectionCreate createRequest) {
        String connectionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        DatabaseConnection connection = new DatabaseConnection(
            connectionId,
            createRequest.getName(),
            createRequest.getHost(),
            createRequest.getPort(),
            createRequest.getUsername(),
            createRequest.getPassword(),
            createRequest.getDatabase(),
            createRequest.getCharset(),
            createRequest.getDescription(),
            true,
            now,
            now
        );
        
        return repository.save(connection);
    }

    public List<DatabaseConnection> getAllConnections() {
        List<DatabaseConnection> connections = repository.findAll();
        if (connections.isEmpty()) {
            createDefaultConnection();
            connections = repository.findAll();
        }
        return connections;
    }

    public Optional<DatabaseConnection> getConnection(String connectionId) {
        return repository.findById(connectionId);
    }

    public DatabaseConnection getActiveConnection() {
        Optional<DatabaseConnection> activeConnection = repository.findFirstByIsActiveTrue();
        if (activeConnection.isPresent()) {
            return activeConnection.get();
        }
        
        // If no active connection, create default and return it
        createDefaultConnection();
        return repository.findFirstByIsActiveTrue().orElse(null);
    }

    public DatabaseConnection updateConnection(String connectionId, DatabaseConnectionUpdate updateRequest) {
        Optional<DatabaseConnection> optionalConnection = repository.findById(connectionId);
        if (optionalConnection.isEmpty()) {
            return null;
        }
        
        DatabaseConnection connection = optionalConnection.get();
        
        if (updateRequest.getName() != null) {
            connection.setName(updateRequest.getName());
        }
        if (updateRequest.getHost() != null) {
            connection.setHost(updateRequest.getHost());
        }
        if (updateRequest.getPort() != null) {
            connection.setPort(updateRequest.getPort());
        }
        if (updateRequest.getUsername() != null) {
            connection.setUsername(updateRequest.getUsername());
        }
        if (updateRequest.getPassword() != null) {
            connection.setPassword(updateRequest.getPassword());
        }
        if (updateRequest.getDatabase() != null) {
            connection.setDatabase(updateRequest.getDatabase());
        }
        if (updateRequest.getCharset() != null) {
            connection.setCharset(updateRequest.getCharset());
        }
        if (updateRequest.getDescription() != null) {
            connection.setDescription(updateRequest.getDescription());
        }
        if (updateRequest.getIsActive() != null) {
            connection.setIsActive(updateRequest.getIsActive());
        }
        
        connection.setUpdatedAt(LocalDateTime.now());
        
        return repository.save(connection);
    }

    public boolean deleteConnection(String connectionId) {
        List<DatabaseConnection> allConnections = repository.findAll();
        if (allConnections.size() <= 1) {
            return false; // Cannot delete the last connection
        }
        
        Optional<DatabaseConnection> connection = repository.findById(connectionId);
        if (connection.isEmpty()) {
            return false;
        }
        
        // Check if it's the default connection
        DatabaseConnection conn = connection.get();
        if (isDefaultConnection(conn)) {
            return false; // Cannot delete default connection
        }
        
        repository.deleteById(connectionId);
        return true;
    }

    public Map<String, Object> testConnection(DatabaseConnectionTest testRequest) {
        try {
            String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false&serverTimezone=UTC",
                testRequest.getHost(), testRequest.getPort(), testRequest.getDatabase(), testRequest.getCharset());
            
            try (Connection connection = DriverManager.getConnection(url, testRequest.getUsername(), testRequest.getPassword())) {
                // Test query
                try (var statement = connection.createStatement();
                     var resultSet = statement.executeQuery("SELECT VERSION()")) {
                    
                    if (resultSet.next()) {
                        String version = resultSet.getString(1);
                        return Map.of(
                            "success", true,
                            "message", "连接成功",
                            "version", version
                        );
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database connection test failed: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "message", "连接失败: " + e.getMessage()
            );
        }
        
        return Map.of(
            "success", false,
            "message", "连接测试失败"
        );
    }

    private void createDefaultConnection() {
        String connectionId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        DatabaseConnection defaultConnection = new DatabaseConnection(
            connectionId,
            "默认数据库",
            properties.getDatabase().getHost(),
            properties.getDatabase().getPort(),
            properties.getDatabase().getUsername(),
            properties.getDatabase().getPassword(),
            properties.getDatabase().getName(),
            "utf8mb4",
            "ChatBI系统默认数据库连接",
            true,
            now,
            now
        );
        
        repository.save(defaultConnection);
        logger.info("Created default database connection: {}", defaultConnection.getName());
    }

    private boolean isDefaultConnection(DatabaseConnection connection) {
        return "默认数据库".equals(connection.getName()) &&
               properties.getDatabase().getHost().equals(connection.getHost()) &&
               properties.getDatabase().getName().equals(connection.getDatabase());
    }
}