from pydantic import BaseModel
from typing import List, Optional, Dict, Any

class SemanticSQL(BaseModel):
    """语义SQL结构"""
    tables: List[str]
    columns: List[str]
    conditions: List[Dict[str, Any]]
    aggregations: List[Dict[str, str]]
    joins: List[Dict[str, str]]
    order_by: Optional[List[Dict[str, str]]] = None
    group_by: Optional[List[str]] = None
    limit: Optional[int] = None

class ChatRequest(BaseModel):
    """聊天请求"""
    message: str
    conversation_id: Optional[str] = None

class ChatResponse(BaseModel):
    """聊天响应"""
    response: str
    sql_query: Optional[str] = None
    semantic_sql: Optional[SemanticSQL] = None
    conversation_id: str
    execution_result: Optional[Dict[str, Any]] = None
    debug_ollama: Optional[Dict[str, Any]] = None

class SQLExecutionRequest(BaseModel):
    """SQL执行请求"""
    sql_query: str
    conversation_id: Optional[str] = None

class SQLExecutionResponse(BaseModel):
    """SQL执行响应"""
    success: bool
    data: Optional[List[Dict[str, Any]]] = None
    error: Optional[str] = None
    row_count: Optional[int] = None

# 数据库后台管理相关模型
class DatabaseConnection(BaseModel):
    """数据库连接配置"""
    id: Optional[str] = None
    name: str
    host: str
    port: int = 3306
    username: str
    password: str
    database: str
    charset: str = "utf8mb4"
    description: Optional[str] = None
    is_active: bool = True
    created_at: Optional[str] = None
    updated_at: Optional[str] = None

class DatabaseConnectionCreate(BaseModel):
    """创建数据库连接请求"""
    name: str
    host: str
    port: int = 3306
    username: str
    password: str
    database: str
    charset: str = "utf8mb4"
    description: Optional[str] = None

class DatabaseConnectionUpdate(BaseModel):
    """更新数据库连接请求"""
    name: Optional[str] = None
    host: Optional[str] = None
    port: Optional[int] = None
    username: Optional[str] = None
    password: Optional[str] = None
    database: Optional[str] = None
    charset: Optional[str] = None
    description: Optional[str] = None
    is_active: Optional[bool] = None

class DatabaseConnectionTest(BaseModel):
    """测试数据库连接请求"""
    host: str
    port: int = 3306
    username: str
    password: str
    database: str
    charset: str = "utf8mb4"

class TableInfo(BaseModel):
    """表信息"""
    table_name: str
    table_comment: Optional[str] = None
    table_rows: Optional[int] = None
    table_size: Optional[str] = None
    engine: Optional[str] = None
    charset: Optional[str] = None

class ColumnInfo(BaseModel):
    """字段信息"""
    column_name: str
    data_type: str
    is_nullable: bool
    column_key: Optional[str] = None
    column_default: Optional[str] = None
    extra: Optional[str] = None
    column_comment: Optional[str] = None
    column_order: int

class TableSchema(BaseModel):
    """表结构"""
    table_name: str
    table_comment: Optional[str] = None
    columns: List[ColumnInfo]

class CommentUpdate(BaseModel):
    """注释更新请求"""
    table_name: str
    column_name: Optional[str] = None  # None表示更新表注释，有值表示更新字段注释
    comment: str
