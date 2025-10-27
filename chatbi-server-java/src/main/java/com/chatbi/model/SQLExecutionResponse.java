package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SQLExecutionResponse {
    private boolean success;
    private List<Map<String, Object>> data;
    private String error;
    
    @JsonProperty("row_count")
    private Integer rowCount;

    // Constructors
    public SQLExecutionResponse() {}

    public SQLExecutionResponse(boolean success, List<Map<String, Object>> data, 
                               String error, Integer rowCount) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.rowCount = rowCount;
    }
}