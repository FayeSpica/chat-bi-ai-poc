package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class TableSchema {
    @JsonProperty("table_name")
    private String tableName;
    
    @JsonProperty("table_comment")
    private String tableComment;
    
    private List<ColumnInfo> columns;

    // Constructors
    public TableSchema() {}

    public TableSchema(String tableName, String tableComment, List<ColumnInfo> columns) {
        this.tableName = tableName;
        this.tableComment = tableComment;
        this.columns = columns;
    }

    // Getters and Setters
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }
}