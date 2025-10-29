export interface SemanticSQL {
  tables: string[];
  columns: string[];
  conditions: Array<{
    column: string;
    operator: string;
    value: any;
    table?: string;
  }>;
  aggregations: Array<{
    function: string;
    column: string;
    alias?: string;
  }>;
  joins: Array<{
    type: string;
    table1: string;
    table2: string;
    condition: string;
  }>;
  order_by?: Array<{
    column: string;
    direction: string;
  }>;
  group_by?: string[];
  limit?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: Date;
  semantic_sql?: SemanticSQL;
  sql_query?: string;
  execution_result?: SQLExecutionResult;
  debug_info?: {
    request?: ChatRequest;
    response?: ChatResponse;
    ollama?: Record<string, any>;
    sql_execution?: SQLExecutionResult | null;
  };
}

export interface ChatRequest {
  message: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface ChatResponse {
  response: string;
  sql_query?: string;
  semantic_sql?: SemanticSQL;
  conversation_id: string;
  execution_result?: SQLExecutionResult;
  debug_ollama?: Record<string, any>;
}

export interface SQLExecutionRequest {
  sql_query: string;
  conversation_id?: string;
  database_connection_id?: string;
}

export interface SQLExecutionResult {
  success: boolean;
  data?: Array<Record<string, any>>;
  error?: string;
  row_count?: number;
}

export interface DatabaseSchema {
  [tableName: string]: Array<{
    Field: string;
    Type: string;
    Null: string;
    Key: string;
    Default: any;
    Extra: string;
  }>;
}

// 数据库后台管理相关类型
export interface DatabaseConnection {
  id?: string;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  database_name: string;
  charset_name: string;
  description?: string;
  is_active: boolean;
  created_at?: string;
  updated_at?: string;
}

export interface DatabaseConnectionCreate {
  name: string;
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
  description?: string;
}

export interface DatabaseConnectionUpdate {
  name?: string;
  host?: string;
  port?: number;
  username?: string;
  password?: string;
  database_name?: string;
  charset_name?: string;
  description?: string;
  is_active?: boolean;
}

export interface DatabaseConnectionTest {
  host: string;
  port?: number;
  username: string;
  password: string;
  database_name: string;
  charset_name?: string;
}

export interface TableInfo {
  table_name: string;
  table_comment?: string;
  table_rows?: number;
  table_size?: string;
  engine?: string;
  charset_name?: string;
}

export interface ColumnInfo {
  column_name: string;
  data_type: string;
  is_nullable: boolean;
  column_key?: string;
  column_default?: string;
  extra?: string;
  column_comment?: string;
  column_order: number;
}

export interface TableSchema {
  table_name: string;
  table_comment?: string;
  columns: ColumnInfo[];
}

export interface CommentUpdate {
  table_name: string;
  column_name?: string;
  comment: string;
}

export interface UserToken {
  userId?: string;
  userName?: string;
  roleNames?: string[];
}
