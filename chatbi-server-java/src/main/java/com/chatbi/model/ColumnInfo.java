package com.chatbi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    // Getters and Setters
    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Boolean getIsNullable() {
        return isNullable;
    }

    public void setIsNullable(Boolean isNullable) {
        this.isNullable = isNullable;
    }

    public String getColumnKey() {
        return columnKey;
    }

    public void setColumnKey(String columnKey) {
        this.columnKey = columnKey;
    }

    public String getColumnDefault() {
        return columnDefault;
    }

    public void setColumnDefault(String columnDefault) {
        this.columnDefault = columnDefault;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getColumnComment() {
        return columnComment;
    }

    public void setColumnComment(String columnComment) {
        this.columnComment = columnComment;
    }

    public Integer getColumnOrder() {
        return columnOrder;
    }

    public void setColumnOrder(Integer columnOrder) {
        this.columnOrder = columnOrder;
    }
}