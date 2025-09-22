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
}

export interface ChatRequest {
  message: string;
  conversation_id?: string;
}

export interface ChatResponse {
  response: string;
  sql_query?: string;
  semantic_sql?: SemanticSQL;
  conversation_id: string;
  execution_result?: SQLExecutionResult;
}

export interface SQLExecutionRequest {
  sql_query: string;
  conversation_id?: string;
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
