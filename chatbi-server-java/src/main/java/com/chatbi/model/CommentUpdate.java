package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class CommentUpdate {
    @NotBlank(message = "Table name is required")
    @JsonProperty("table_name")
    private String tableName;
    
    @JsonProperty("column_name")
    private String columnName; // null means updating table comment
    
    @NotBlank(message = "Comment is required")
    private String comment;

    // Constructors
    public CommentUpdate() {}

    public CommentUpdate(String tableName, String columnName, String comment) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.comment = comment;
    }

    // Getters and Setters
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}