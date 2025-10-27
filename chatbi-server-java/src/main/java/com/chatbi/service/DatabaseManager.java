package com.chatbi.service;

import com.chatbi.model.DatabaseConnection;
import com.chatbi.model.SQLExecutionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private DatabaseConnectionService databaseConnectionService;

    public SQLExecutionResponse executeQuery(String sql, DatabaseConnection connection) {
        try {
            logger.info("Executing SQL query: {}", sql);
            
            JdbcTemplate template;
            if (connection != null) {
                // Create a new JdbcTemplate with the specific connection's datasource
                String url = String.format("jdbc:mariadb://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false&serverTimezone=UTC",
                    connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getCharsetName());
                
                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setUrl(url);
                dataSource.setUsername(connection.getUsername());
                dataSource.setPassword(connection.getPassword());
                dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
                
                template = new JdbcTemplate(dataSource);
            } else {
                // Use default JdbcTemplate
                template = jdbcTemplate;
            }
            
            if (sql.trim().toUpperCase().startsWith("SELECT")) {
                List<Map<String, Object>> data = template.queryForList(sql);
                return new SQLExecutionResponse(true, data, null, data.size());
            } else {
                int rowCount = template.update(sql);
                return new SQLExecutionResponse(true, null, null, rowCount);
            }
        } catch (Exception e) {
            logger.error("Error executing SQL query: {}", e.getMessage(), e);
            return new SQLExecutionResponse(false, null, e.getMessage(), 0);
        }
    }

    public SQLExecutionResponse executeQuery(String sql) {
        DatabaseConnection activeConnection = databaseConnectionService.getActiveConnection();
        if (activeConnection != null) {
            return executeQuery(sql, activeConnection);
        } else {
            return executeQuery(sql, null);
        }
    }

    public List<String> getAllTables(DatabaseConnection connection) {
        try {
            String sql = "SHOW TABLES";
            JdbcTemplate template;
            if (connection != null) {
                // Create a new JdbcTemplate with the specific connection's datasource
                String url = String.format("jdbc:mariadb://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false&serverTimezone=UTC",
                    connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getCharsetName());
                
                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setUrl(url);
                dataSource.setUsername(connection.getUsername());
                dataSource.setPassword(connection.getPassword());
                dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
                
                template = new JdbcTemplate(dataSource);
            } else {
                template = jdbcTemplate;
            }
            return template.queryForList(sql, String.class);
        } catch (Exception e) {
            logger.error("Error getting tables: {}", e.getMessage(), e);
            return List.of();
        }
    }

    public List<String> getAllTables() {
        DatabaseConnection activeConnection = databaseConnectionService.getActiveConnection();
        return getAllTables(activeConnection);
    }

    public List<Map<String, Object>> getTableSchema(String tableName, DatabaseConnection connection) {
        try {
            String sql = "DESCRIBE " + tableName;
            JdbcTemplate template;
            if (connection != null) {
                // Create a new JdbcTemplate with the specific connection's datasource
                String url = String.format("jdbc:mariadb://%s:%d/%s?useUnicode=true&characterEncoding=%s&useSSL=false&serverTimezone=UTC",
                    connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getCharsetName());
                
                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setUrl(url);
                dataSource.setUsername(connection.getUsername());
                dataSource.setPassword(connection.getPassword());
                dataSource.setDriverClassName("org.mariadb.jdbc.Driver");
                
                template = new JdbcTemplate(dataSource);
            } else {
                template = jdbcTemplate;
            }
            return template.queryForList(sql);
        } catch (Exception e) {
            logger.error("Error getting schema for table {}: {}", tableName, e.getMessage(), e);
            return List.of();
        }
    }

    public List<Map<String, Object>> getTableSchema(String tableName) {
        DatabaseConnection activeConnection = databaseConnectionService.getActiveConnection();
        return getTableSchema(tableName, activeConnection);
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}