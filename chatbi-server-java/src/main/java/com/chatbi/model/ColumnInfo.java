package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ColumnInfo {
    @JsonProperty("column_name")
    private String columnName;
    
    @JsonProperty("data_type")
    private String dataType;
    
    @JsonProperty("is_nullable")
    private Boolean isNullable;
    
    @JsonProperty("column_key")
    private String columnKey;
    
    @JsonProperty("column_default")
    private String columnDefault;
    
    private String extra;
    
    @JsonProperty("column_comment")
    private String columnComment;
    
    @JsonProperty("column_order")
    private Integer columnOrder;

    // Constructors
    public ColumnInfo() {}

    public ColumnInfo(String columnName, String dataType, Boolean isNullable, 
                      String columnKey, String columnDefault, String extra, 
                      String columnComment, Integer columnOrder) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.isNullable = isNullable;
        this.columnKey = columnKey;
        this.columnDefault = columnDefault;
        this.extra = extra;
        this.columnComment = columnComment;
        this.columnOrder = columnOrder;
    }
}