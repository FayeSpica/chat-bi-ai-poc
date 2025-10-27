package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
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
}