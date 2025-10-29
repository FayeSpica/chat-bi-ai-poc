package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
public class SQLExecutionResponse {
    private boolean success;
    private List<Map<String, Object>> data;
    private String error;
    
    @JsonProperty("row_count")
    private Integer rowCount;

    public SQLExecutionResponse(boolean success, List<Map<String, Object>> data, 
                               String error, Integer rowCount) {
        this.success = success;
        this.data = Objects.requireNonNullElseGet(data, ArrayList::new);
        this.error = Objects.requireNonNullElse(error, "");
        this.rowCount = rowCount;
    }
}
