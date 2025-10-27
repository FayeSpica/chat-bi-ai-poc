package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

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

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }
}