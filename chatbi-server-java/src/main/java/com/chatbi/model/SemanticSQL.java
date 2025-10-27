package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class SemanticSQL {
    @NotNull
    private List<String> tables;
    
    @NotNull
    private List<String> columns;
    
    @NotNull
    private List<Map<String, Object>> conditions;
    
    @NotNull
    private List<Map<String, String>> aggregations;
    
    @NotNull
    private List<Map<String, String>> joins;
    
    private List<Map<String, String>> orderBy;
    
    private List<String> groupBy;
    
    private Integer limit;

    // Constructors
    public SemanticSQL() {}

    public SemanticSQL(List<String> tables, List<String> columns, List<Map<String, Object>> conditions,
                      List<Map<String, String>> aggregations, List<Map<String, String>> joins,
                      List<Map<String, String>> orderBy, List<String> groupBy, Integer limit) {
        this.tables = tables;
        this.columns = columns;
        this.conditions = conditions;
        this.aggregations = aggregations;
        this.joins = joins;
        this.orderBy = orderBy;
        this.groupBy = groupBy;
        this.limit = limit;
    }

    // Getters and Setters
    public List<String> getTables() {
        return tables;
    }

    public void setTables(List<String> tables) {
        this.tables = tables;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getConditions() {
        return conditions;
    }

    public void setConditions(List<Map<String, Object>> conditions) {
        this.conditions = conditions;
    }

    public List<Map<String, String>> getAggregations() {
        return aggregations;
    }

    public void setAggregations(List<Map<String, String>> aggregations) {
        this.aggregations = aggregations;
    }

    public List<Map<String, String>> getJoins() {
        return joins;
    }

    public void setJoins(List<Map<String, String>> joins) {
        this.joins = joins;
    }

    @JsonProperty("order_by")
    public List<Map<String, String>> getOrderBy() {
        return orderBy;
    }

    @JsonProperty("order_by")
    public void setOrderBy(List<Map<String, String>> orderBy) {
        this.orderBy = orderBy;
    }

    @JsonProperty("group_by")
    public List<String> getGroupBy() {
        return groupBy;
    }

    @JsonProperty("group_by")
    public void setGroupBy(List<String> groupBy) {
        this.groupBy = groupBy;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}