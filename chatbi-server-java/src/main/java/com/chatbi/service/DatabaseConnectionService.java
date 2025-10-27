package com.chatbi.service;

import com.chatbi.config.ChatbiProperties;
import com.chatbi.model.*;
import com.chatbi.repository.DatabaseConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionService.class);
    
    @Autowired
    private DatabaseConnectionRepository repository;
    
    @Autowired
    private ChatbiProperties properties;
    
    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    
    @Value("${spring.datasource.username}")
    private String datasourceUsername;
    
    @Value("${spring.datasource.password}")
    private String datasourcePassword;
    
    // Helper method to parse database info from JDBC URL
    private DatabaseInfo parseDatabaseInfo() {
        // Parse jdbc:mysql://host:port/database from datasourceUrl
        String url = datasourceUrl;
        if (url.startsWith("jdbc:mysql://") || url.startsWith("jdbc:mariadb://")) {
            url = url.substring(url.indexOf("://") + 3);
            String[] parts = url.split("/");
            String hostPort = parts[0];
            String database = parts.length > 1 ? parts[1].split("\\?")[0] : "test_db";
            
            String[] hostPortParts = hostPort.split(":");
            String host = hostPortParts[0];
            int port = hostPortParts.length > 1 ? Integer.parseInt(hostPortParts[1]) : 3306;
            
            return new DatabaseInfo(host, port, database);
        }
        return new DatabaseInfo("localhost", 3306, "test_db");
    }
    
    private static class DatabaseInfo {
        final String host;
        final int port;
        final String database;
        
        DatabaseInfo(String host, int port, String database) {
            this.host = host;
            this.port = port;
            this.database = database;
        }
    }

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
            createRequest.getDatabaseName(),
            createRequest.getCharsetName(),
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
        if (updateRequest.getDatabaseName() != null) {
            connection.setDatabaseName(updateRequest.getDatabaseName());
        }
        if (updateRequest.getCharsetName() != null) {
            connection.setCharsetName(updateRequest.getCharsetName());
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
                testRequest.getHost(), testRequest.getPort(), testRequest.getDatabaseName(), testRequest.getCharsetName());
            
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
        
        DatabaseInfo dbInfo = parseDatabaseInfo();
        DatabaseConnection defaultConnection = new DatabaseConnection(
            connectionId,
            "默认数据库",
            dbInfo.host,
            dbInfo.port,
            datasourceUsername,
            datasourcePassword,
            dbInfo.database,
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
        DatabaseInfo dbInfo = parseDatabaseInfo();
        return "默认数据库".equals(connection.getName()) &&
               dbInfo.host.equals(connection.getHost()) &&
               dbInfo.database.equals(connection.getDatabaseName());
    }
}