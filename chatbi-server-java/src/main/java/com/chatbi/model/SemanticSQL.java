package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
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
}