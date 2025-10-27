package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
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
}