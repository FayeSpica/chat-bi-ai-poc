package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TableInfo {
    @JsonProperty("table_name")
    private String tableName;
    
    @JsonProperty("table_comment")
    private String tableComment;
    
    @JsonProperty("table_rows")
    private Integer tableRows;
    
    @JsonProperty("table_size")
    private String tableSize;
    
    private String engine;
    private String charset;

    // Constructors
    public TableInfo() {}

    public TableInfo(String tableName, String tableComment, Integer tableRows, 
                     String tableSize, String engine, String charset) {
        this.tableName = tableName;
        this.tableComment = tableComment;
        this.tableRows = tableRows;
        this.tableSize = tableSize;
        this.engine = engine;
        this.charset = charset;
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

    public Integer getTableRows() {
        return tableRows;
    }

    public void setTableRows(Integer tableRows) {
        this.tableRows = tableRows;
    }

    public String getTableSize() {
        return tableSize;
    }

    public void setTableSize(String tableSize) {
        this.tableSize = tableSize;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}