package com.chatbi.service;

import com.chatbi.model.SemanticSQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MySQLSQLGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLSQLGenerator.class);

    public String generateMySQLSQL(SemanticSQL semanticSQL) {
        try {
            if (semanticSQL.getTables() == null || semanticSQL.getTables().isEmpty()) {
                return "SELECT 1; -- No tables specified";
            }
            
            List<String> sqlParts = new ArrayList<>();
            
            // SELECT clause
            String selectClause = buildSelectClause(semanticSQL);
            sqlParts.add("SELECT " + selectClause);
            
            // FROM clause
            String fromClause = buildFromClause(semanticSQL);
            sqlParts.add("FROM " + fromClause);
            
            // JOIN clauses
            if (semanticSQL.getJoins() != null && !semanticSQL.getJoins().isEmpty()) {
                List<String> joinClauses = buildJoinClauses(semanticSQL);
                sqlParts.addAll(joinClauses);
            }
            
            // WHERE clause
            if (semanticSQL.getConditions() != null && !semanticSQL.getConditions().isEmpty()) {
                String whereClause = buildWhereClause(semanticSQL);
                sqlParts.add("WHERE " + whereClause);
            }
            
            // GROUP BY clause
            if (semanticSQL.getGroupBy() != null && !semanticSQL.getGroupBy().isEmpty()) {
                String groupByClause = buildGroupByClause(semanticSQL);
                sqlParts.add("GROUP BY " + groupByClause);
            }
            
            // ORDER BY clause
            if (semanticSQL.getOrderBy() != null && !semanticSQL.getOrderBy().isEmpty()) {
                String orderByClause = buildOrderByClause(semanticSQL);
                sqlParts.add("ORDER BY " + orderByClause);
            }
            
            // LIMIT clause
            if (semanticSQL.getLimit() != null) {
                sqlParts.add("LIMIT " + semanticSQL.getLimit());
            }
            
            return String.join(" ", sqlParts);
            
        } catch (Exception e) {
            logger.error("Error generating MySQL SQL: {}", e.getMessage(), e);
            return "SELECT 1; -- Error generating SQL: " + e.getMessage();
        }
    }

    private String buildSelectClause(SemanticSQL semanticSQL) {
        if (semanticSQL.getColumns() != null && !semanticSQL.getColumns().isEmpty()) {
            return String.join(", ", semanticSQL.getColumns());
        } else {
            return "*";
        }
    }

    private String buildFromClause(SemanticSQL semanticSQL) {
        // If there are JOINs, only keep the first table in FROM, others in JOIN
        if (semanticSQL.getJoins() != null && !semanticSQL.getJoins().isEmpty()) {
            return semanticSQL.getTables().get(0);
        } else {
            return String.join(", ", semanticSQL.getTables());
        }
    }

    private List<String> buildJoinClauses(SemanticSQL semanticSQL) {
        List<String> joinClauses = new ArrayList<>();
        for (Map<String, String> join : semanticSQL.getJoins()) {
            String joinType = join.getOrDefault("type", "INNER").toUpperCase();
            String table1 = join.get("table1");
            String table2 = join.get("table2");
            String condition = join.get("condition");
            
            if (table1 != null && table2 != null && condition != null) {
                // If table1 is the first table in FROM clause, directly JOIN table2
                if (table1.equals(semanticSQL.getTables().get(0))) {
                    joinClauses.add(joinType + " JOIN " + table2 + " ON " + condition);
                } else {
                    // Otherwise need to JOIN table1 first, then table2
                    joinClauses.add(joinType + " JOIN " + table1 + " ON " + condition);
                }
            }
        }
        return joinClauses;
    }

    private String buildWhereClause(SemanticSQL semanticSQL) {
        List<String> conditions = new ArrayList<>();
        for (Map<String, Object> condition : semanticSQL.getConditions()) {
            String column = (String) condition.get("column");
            String operator = (String) condition.getOrDefault("operator", "=");
            Object value = condition.get("value");
            
            if (column != null && value != null) {
                String valueStr = sqlQuote(value);
                
                // Handle different operators
                if ("IN".equalsIgnoreCase(operator)) {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> valueList = (List<Object>) value;
                        String inValues = valueList.stream()
                            .map(this::sqlQuote)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                        valueStr = "(" + inValues + ")";
                    } else {
                        valueStr = "(" + valueStr + ")";
                    }
                } else if ("BETWEEN".equalsIgnoreCase(operator)) {
                    if (value instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> valueList = (List<Object>) value;
                        if (valueList.size() == 2) {
                            valueStr = sqlQuote(valueList.get(0)) + " AND " + sqlQuote(valueList.get(1));
                        }
                    }
                } else if ("LIKE".equalsIgnoreCase(operator)) {
                    valueStr = sqlQuote(value);
                }
                
                conditions.add(column + " " + operator + " " + valueStr);
            }
        }
        return String.join(" AND ", conditions);
    }

    private String buildGroupByClause(SemanticSQL semanticSQL) {
        return String.join(", ", semanticSQL.getGroupBy());
    }

    private String buildOrderByClause(SemanticSQL semanticSQL) {
        List<String> orderItems = new ArrayList<>();
        for (Map<String, String> order : semanticSQL.getOrderBy()) {
            String column = order.get("column");
            String direction = order.getOrDefault("direction", "ASC").toUpperCase();
            if (column != null) {
                orderItems.add(column + " " + direction);
            }
        }
        return String.join(", ", orderItems);
    }

    private String sqlQuote(Object value) {
        if (value instanceof String) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        return String.valueOf(value);
    }
}