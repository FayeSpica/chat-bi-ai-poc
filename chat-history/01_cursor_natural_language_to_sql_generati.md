# Natural language to SQL generation project
_Exported on 2025/9/22 at GMT+8 21:36:51 from Cursor (1.6.42)_

---

**User**

使用python/langchain/fastapi/ollama等包，进行自然语言转语义SQL，再由语义SQL(JSON)生成MySQL SQL语句，并分为chatbi-server/chatbi-ui前后端分离的两个项目

---

**Cursor**

我将为您创建一个基于Python/LangChain/FastAPI/Ollama的自然语言转语义SQL系统，包含前后端分离的架构。让我先了解当前项目结构，然后创建完整的解决方案。


现在我将创建一个完整的自然语言转语义SQL系统，包含前后端分离架构。让我先规划任务：


首先创建后端项目结构：


```pip-requirements
fastapi==0.104.1
uvicorn==0.24.0
langchain==0.0.350
langchain-community==0.0.2
ollama==0.1.7
pydantic==2.5.0
python-multipart==0.0.6
python-dotenv==1.0.0
pymysql==1.1.0
sqlalchemy==2.0.23
sqlparse==0.4.4
openai==1.3.7
```

```plaintext
# Ollama配置
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama2

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=password
DB_NAME=test_db

# API配置
API_HOST=0.0.0.0
API_PORT=8000
```

```python
import os
from dotenv import load_dotenv

load_dotenv()

class Settings:
    # Ollama配置
    OLLAMA_BASE_URL: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    OLLAMA_MODEL: str = os.getenv("OLLAMA_MODEL", "llama2")
    
    # 数据库配置
    DB_HOST: str = os.getenv("DB_HOST", "localhost")
    DB_PORT: int = int(os.getenv("DB_PORT", "3306"))
    DB_USER: str = os.getenv("DB_USER", "root")
    DB_PASSWORD: str = os.getenv("DB_PASSWORD", "password")
    DB_NAME: str = os.getenv("DB_NAME", "test_db")
    
    # API配置
    API_HOST: str = os.getenv("API_HOST", "0.0.0.0")
    API_PORT: int = int(os.getenv("API_PORT", "8000"))
    
    @property
    def database_url(self) -> str:
        return f"mysql+pymysql://{self.DB_USER}:{self.DB_PASSWORD}@{self.DB_HOST}:{self.DB_PORT}/{self.DB_NAME}"

settings = Settings()
```

```python
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
```

现在实现语义SQL转换的核心逻辑：


```python
import json
import re
from typing import Dict, List, Any, Optional
from langchain.llms import Ollama
from langchain.schema import HumanMessage, SystemMessage
from langchain.callbacks.manager import CallbackManagerForLLMRun
from app.config import settings
from app.models import SemanticSQL

class SemanticSQLConverter:
    """自然语言转语义SQL转换器"""
    
    def __init__(self):
        self.llm = Ollama(
            base_url=settings.OLLAMA_BASE_URL,
            model=settings.OLLAMA_MODEL,
            temperature=0.1
        )
        
        self.system_prompt = """你是一个专业的SQL语义转换器。你的任务是将用户的自然语言查询转换为结构化的语义SQL JSON格式。

输出格式必须是严格的JSON，包含以下字段：
{
    "tables": ["表名1", "表名2"],
    "columns": ["列名1", "列名2", "聚合函数(列名)"],
    "conditions": [
        {"column": "列名", "operator": "操作符", "value": "值", "table": "表名"}
    ],
    "aggregations": [
        {"function": "聚合函数", "column": "列名", "alias": "别名"}
    ],
    "joins": [
        {"type": "连接类型", "table1": "表1", "table2": "表2", "condition": "连接条件"}
    ],
    "order_by": [{"column": "列名", "direction": "ASC/DESC"}],
    "group_by": ["分组列"],
    "limit": 数量限制
}

支持的操作符：=, !=, >, <, >=, <=, LIKE, IN, BETWEEN
支持的聚合函数：COUNT, SUM, AVG, MAX, MIN
支持的连接类型：INNER, LEFT, RIGHT, FULL

示例：
用户输入："查询所有用户的订单总金额，按用户ID分组"
输出：
{
    "tables": ["users", "orders"],
    "columns": ["users.id", "SUM(orders.amount) as total_amount"],
    "conditions": [],
    "aggregations": [{"function": "SUM", "column": "orders.amount", "alias": "total_amount"}],
    "joins": [{"type": "INNER", "table1": "users", "table2": "orders", "condition": "users.id = orders.user_id"}],
    "order_by": [],
    "group_by": ["users.id"],
    "limit": null
}

请严格按照JSON格式输出，不要包含任何其他文字。"""

    def convert_to_semantic_sql(self, natural_language: str) -> SemanticSQL:
        """将自然语言转换为语义SQL"""
        try:
            prompt = f"{self.system_prompt}\n\n用户查询：{natural_language}"
            
            # 调用LLM生成语义SQL
            response = self.llm.invoke(prompt)
            
            # 提取JSON部分
            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if not json_match:
                raise ValueError("无法从响应中提取JSON格式的语义SQL")
            
            json_str = json_match.group()
            semantic_data = json.loads(json_str)
            
            # 验证并创建SemanticSQL对象
            semantic_sql = SemanticSQL(**semantic_data)
            
            return semantic_sql
            
        except Exception as e:
            # 如果转换失败，返回一个默认的语义SQL结构
            return SemanticSQL(
                tables=[],
                columns=[],
                conditions=[],
                aggregations=[],
                joins=[]
            )

    def validate_semantic_sql(self, semantic_sql: SemanticSQL) -> bool:
        """验证语义SQL的有效性"""
        try:
            # 基本验证
            if not semantic_sql.tables:
                return False
                
            # 验证聚合函数
            for agg in semantic_sql.aggregations:
                if agg.get("function") not in ["COUNT", "SUM", "AVG", "MAX", "MIN"]:
                    return False
                    
            # 验证操作符
            for condition in semantic_sql.conditions:
                if condition.get("operator") not in ["=", "!=", ">", "<", ">=", "<=", "LIKE", "IN", "BETWEEN"]:
                    return False
                    
            return True
            
        except Exception:
            return False

class MySQLSQLGenerator:
    """MySQL SQL生成器"""
    
    def __init__(self):
        self.converter = SemanticSQLConverter()
    
    def generate_mysql_sql(self, semantic_sql: SemanticSQL) -> str:
        """将语义SQL转换为MySQL SQL语句"""
        try:
            sql_parts = []
            
            # SELECT子句
            select_clause = self._build_select_clause(semantic_sql)
            sql_parts.append(f"SELECT {select_clause}")
            
            # FROM子句
            from_clause = self._build_from_clause(semantic_sql)
            sql_parts.append(f"FROM {from_clause}")
            
            # JOIN子句
            if semantic_sql.joins:
                join_clauses = self._build_join_clauses(semantic_sql)
                sql_parts.extend(join_clauses)
            
            # WHERE子句
            if semantic_sql.conditions:
                where_clause = self._build_where_clause(semantic_sql)
                sql_parts.append(f"WHERE {where_clause}")
            
            # GROUP BY子句
            if semantic_sql.group_by:
                group_by_clause = self._build_group_by_clause(semantic_sql)
                sql_parts.append(f"GROUP BY {group_by_clause}")
            
            # ORDER BY子句
            if semantic_sql.order_by:
                order_by_clause = self._build_order_by_clause(semantic_sql)
                sql_parts.append(f"ORDER BY {order_by_clause}")
            
            # LIMIT子句
            if semantic_sql.limit:
                sql_parts.append(f"LIMIT {semantic_sql.limit}")
            
            return " ".join(sql_parts)
            
        except Exception as e:
            return f"SELECT 1; -- Error generating SQL: {str(e)}"
    
    def _build_select_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建SELECT子句"""
        if semantic_sql.columns:
            return ", ".join(semantic_sql.columns)
        else:
            return "*"
    
    def _build_from_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建FROM子句"""
        return ", ".join(semantic_sql.tables)
    
    def _build_join_clauses(self, semantic_sql: SemanticSQL) -> List[str]:
        """构建JOIN子句"""
        join_clauses = []
        for join in semantic_sql.joins:
            join_type = join.get("type", "INNER").upper()
            table1 = join.get("table1", "")
            table2 = join.get("table2", "")
            condition = join.get("condition", "")
            
            if table1 and table2 and condition:
                join_clauses.append(f"{join_type} JOIN {table2} ON {condition}")
        
        return join_clauses
    
    def _build_where_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建WHERE子句"""
        conditions = []
        for condition in semantic_sql.conditions:
            column = condition.get("column", "")
            operator = condition.get("operator", "=")
            value = condition.get("value", "")
            
            if column and value:
                # 处理不同类型的值
                if operator.upper() == "IN":
                    if isinstance(value, list):
                        value_str = f"({', '.join([f\"'{v}'\" if isinstance(v, str) else str(v) for v in value])})"
                    else:
                        value_str = f"({value})"
                elif operator.upper() == "BETWEEN":
                    if isinstance(value, list) and len(value) == 2:
                        value_str = f"'{value[0]}' AND '{value[1]}'"
                    else:
                        value_str = f"'{value}'"
                elif operator.upper() == "LIKE":
                    value_str = f"'{value}'"
                else:
                    value_str = f"'{value}'" if isinstance(value, str) else str(value)
                
                conditions.append(f"{column} {operator} {value_str}")
        
        return " AND ".join(conditions)
    
    def _build_group_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建GROUP BY子句"""
        return ", ".join(semantic_sql.group_by)
    
    def _build_order_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建ORDER BY子句"""
        order_items = []
        for order in semantic_sql.order_by:
            column = order.get("column", "")
            direction = order.get("direction", "ASC").upper()
            if column:
                order_items.append(f"{column} {direction}")
        
        return ", ".join(order_items)

# 创建全局实例
semantic_sql_converter = SemanticSQLConverter()
mysql_sql_generator = MySQLSQLGenerator()
```

```python
import pymysql
from typing import List, Dict, Any, Optional
from app.config import settings
from app.models import SQLExecutionResponse

class DatabaseManager:
    """数据库管理器"""
    
    def __init__(self):
        self.connection = None
    
    def get_connection(self):
        """获取数据库连接"""
        if not self.connection:
            self.connection = pymysql.connect(
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                user=settings.DB_USER,
                password=settings.DB_PASSWORD,
                database=settings.DB_NAME,
                charset='utf8mb4',
                autocommit=True
            )
        return self.connection
    
    def execute_query(self, sql: str) -> SQLExecutionResponse:
        """执行SQL查询"""
        try:
            connection = self.get_connection()
            cursor = connection.cursor(pymysql.cursors.DictCursor)
            
            cursor.execute(sql)
            
            # 获取查询结果
            if sql.strip().upper().startswith('SELECT'):
                data = cursor.fetchall()
                row_count = len(data)
            else:
                data = None
                row_count = cursor.rowcount
            
            cursor.close()
            
            return SQLExecutionResponse(
                success=True,
                data=data,
                row_count=row_count
            )
            
        except Exception as e:
            return SQLExecutionResponse(
                success=False,
                error=str(e),
                row_count=0
            )
    
    def get_table_schema(self, table_name: str) -> List[Dict[str, Any]]:
        """获取表结构信息"""
        try:
            connection = self.get_connection()
            cursor = connection.cursor(pymysql.cursors.DictCursor)
            
            cursor.execute(f"DESCRIBE {table_name}")
            schema = cursor.fetchall()
            
            cursor.close()
            return schema
            
        except Exception as e:
            print(f"Error getting schema for table {table_name}: {e}")
            return []
    
    def get_all_tables(self) -> List[str]:
        """获取所有表名"""
        try:
            connection = self.get_connection()
            cursor = connection.cursor()
            
            cursor.execute("SHOW TABLES")
            tables = [row[0] for row in cursor.fetchall()]
            
            cursor.close()
            return tables
            
        except Exception as e:
            print(f"Error getting tables: {e}")
            return []
    
    def close_connection(self):
        """关闭数据库连接"""
        if self.connection:
            self.connection.close()
            self.connection = None

# 创建全局实例
db_manager = DatabaseManager()
```

现在创建FastAPI接口和路由：


```python
import uuid
from typing import Dict, Optional
from app.models import ChatRequest, ChatResponse, SemanticSQL
from app.semantic_sql_converter import semantic_sql_converter, mysql_sql_generator
from app.database import db_manager

class ChatService:
    """聊天服务"""
    
    def __init__(self):
        self.conversations: Dict[str, list] = {}
    
    def process_chat_message(self, request: ChatRequest) -> ChatResponse:
        """处理聊天消息"""
        try:
            # 获取或创建会话ID
            conversation_id = request.conversation_id or str(uuid.uuid4())
            
            # 初始化会话历史
            if conversation_id not in self.conversations:
                self.conversations[conversation_id] = []
            
            # 添加用户消息到会话历史
            self.conversations[conversation_id].append({
                "role": "user",
                "content": request.message
            })
            
            # 转换自然语言为语义SQL
            semantic_sql = semantic_sql_converter.convert_to_semantic_sql(request.message)
            
            # 生成MySQL SQL语句
            mysql_sql = mysql_sql_generator.generate_mysql_sql(semantic_sql)
            
            # 生成响应消息
            response_message = self._generate_response_message(
                request.message, 
                semantic_sql, 
                mysql_sql
            )
            
            # 添加助手回复到会话历史
            self.conversations[conversation_id].append({
                "role": "assistant",
                "content": response_message,
                "semantic_sql": semantic_sql.dict(),
                "mysql_sql": mysql_sql
            })
            
            return ChatResponse(
                response=response_message,
                sql_query=mysql_sql,
                semantic_sql=semantic_sql,
                conversation_id=conversation_id
            )
            
        except Exception as e:
            error_message = f"处理消息时发生错误: {str(e)}"
            return ChatResponse(
                response=error_message,
                conversation_id=request.conversation_id or str(uuid.uuid4())
            )
    
    def _generate_response_message(
        self, 
        user_message: str, 
        semantic_sql: SemanticSQL, 
        mysql_sql: str
    ) -> str:
        """生成响应消息"""
        
        # 检查是否是查询请求
        if any(keyword in user_message.lower() for keyword in [
            "查询", "显示", "获取", "找出", "统计", "计算", "求和", "平均", "最大", "最小"
        ]):
            
            response = f"我已经将您的查询转换为SQL语句：\n\n"
            response += f"**语义SQL结构：**\n"
            response += f"- 涉及表: {', '.join(semantic_sql.tables) if semantic_sql.tables else '未指定'}\n"
            response += f"- 查询列: {', '.join(semantic_sql.columns) if semantic_sql.columns else '所有列'}\n"
            
            if semantic_sql.conditions:
                response += f"- 筛选条件: {len(semantic_sql.conditions)}个条件\n"
            
            if semantic_sql.aggregations:
                response += f"- 聚合函数: {len(semantic_sql.aggregations)}个\n"
            
            if semantic_sql.joins:
                response += f"- 表连接: {len(semantic_sql.joins)}个\n"
            
            response += f"\n**生成的MySQL SQL：**\n```sql\n{mysql_sql}\n```\n\n"
            response += "您想要执行这个SQL查询吗？"
            
        else:
            response = f"我理解您想要：{user_message}\n\n"
            response += "我已经生成了相应的SQL查询语句。您需要我执行查询并返回结果吗？"
        
        return response
    
    def execute_sql_and_update_response(self, conversation_id: str, sql: str) -> Dict:
        """执行SQL并更新响应"""
        try:
            # 执行SQL查询
            execution_result = db_manager.execute_query(sql)
            
            # 更新会话历史中的最后一条助手消息
            if (conversation_id in self.conversations and 
                self.conversations[conversation_id] and 
                self.conversations[conversation_id][-1]["role"] == "assistant"):
                
                self.conversations[conversation_id][-1]["execution_result"] = execution_result.dict()
            
            return execution_result.dict()
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "row_count": 0
            }
    
    def get_conversation_history(self, conversation_id: str) -> list:
        """获取会话历史"""
        return self.conversations.get(conversation_id, [])
    
    def clear_conversation(self, conversation_id: str):
        """清除会话历史"""
        if conversation_id in self.conversations:
            del self.conversations[conversation_id]

# 创建全局实例
chat_service = ChatService()
```

```python
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from typing import Dict, Any
import uvicorn

from app.models import ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResponse
from app.chat_service import chat_service
from app.database import db_manager
from app.config import settings

# 创建FastAPI应用
app = FastAPI(
    title="ChatBI API",
    description="自然语言转语义SQL的聊天BI系统",
    version="1.0.0"
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境中应该限制为前端域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup_event():
    """应用启动事件"""
    print("ChatBI Server 正在启动...")
    print(f"数据库连接: {settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}")
    print(f"Ollama服务: {settings.OLLAMA_BASE_URL}")

@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭事件"""
    print("ChatBI Server 正在关闭...")
    db_manager.close_connection()

@app.get("/")
async def root():
    """根路径"""
    return {
        "message": "ChatBI API Server",
        "version": "1.0.0",
        "status": "running"
    }

@app.get("/health")
async def health_check():
    """健康检查"""
    try:
        # 测试数据库连接
        tables = db_manager.get_all_tables()
        return {
            "status": "healthy",
            "database": "connected",
            "tables_count": len(tables)
        }
    except Exception as e:
        return JSONResponse(
            status_code=503,
            content={
                "status": "unhealthy",
                "error": str(e)
            }
        )

@app.post("/api/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """聊天接口"""
    try:
        response = chat_service.process_chat_message(request)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/execute-sql", response_model=SQLExecutionResponse)
async def execute_sql(request: SQLExecutionRequest):
    """执行SQL接口"""
    try:
        result = db_manager.execute_query(request.sql_query)
        
        # 如果提供了会话ID，更新会话历史
        if request.conversation_id:
            chat_service.execute_sql_and_update_response(
                request.conversation_id, 
                request.sql_query
            )
        
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/conversation/{conversation_id}")
async def get_conversation_history(conversation_id: str):
    """获取会话历史"""
    try:
        history = chat_service.get_conversation_history(conversation_id)
        return {
            "conversation_id": conversation_id,
            "history": history
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/api/conversation/{conversation_id}")
async def clear_conversation(conversation_id: str):
    """清除会话历史"""
    try:
        chat_service.clear_conversation(conversation_id)
        return {"message": "会话历史已清除"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/database/tables")
async def get_tables():
    """获取数据库表列表"""
    try:
        tables = db_manager.get_all_tables()
        return {"tables": tables}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/database/tables/{table_name}/schema")
async def get_table_schema(table_name: str):
    """获取表结构"""
    try:
        schema = db_manager.get_table_schema(table_name)
        return {
            "table_name": table_name,
            "schema": schema
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/database/schema")
async def get_full_database_schema():
    """获取完整数据库结构"""
    try:
        tables = db_manager.get_all_tables()
        schema = {}
        
        for table in tables:
            schema[table] = db_manager.get_table_schema(table)
        
        return {"database_schema": schema}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.API_HOST,
        port=settings.API_PORT,
        reload=True
    )
```

```python
# ChatBI Server Package
```

现在创建前端项目结构：


```json
{
  "name": "chatbi-ui",
  "version": "1.0.0",
  "description": "ChatBI前端界面",
  "main": "index.js",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "lint": "eslint . --ext js,jsx,ts,tsx --report-unused-disable-directives --max-warnings 0"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "axios": "^1.6.0",
    "antd": "^5.12.0",
    "@ant-design/icons": "^5.2.0",
    "react-markdown": "^9.0.0",
    "react-syntax-highlighter": "^15.5.0",
    "uuid": "^9.0.0",
    "moment": "^2.29.0"
  },
  "devDependencies": {
    "@types/react": "^18.2.37",
    "@types/react-dom": "^18.2.15",
    "@types/uuid": "^9.0.0",
    "@types/react-syntax-highlighter": "^15.5.0",
    "@vitejs/plugin-react": "^4.1.1",
    "eslint": "^8.53.0",
    "eslint-plugin-react-hooks": "^4.6.0",
    "eslint-plugin-react-refresh": "^0.4.4",
    "typescript": "^5.2.2",
    "vite": "^4.5.0"
  }
}
```

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: true
  },
  build: {
    outDir: 'dist'
  }
})
```

```jsonc
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

```jsonc
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts"]
}
```

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>ChatBI - 智能聊天BI系统</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

现在实现前端聊天界面：


```typescriptreact
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

code {
  font-family: source-code-pro, Menlo, Monaco, Consolas, 'Courier New',
    monospace;
}

* {
  box-sizing: border-box;
}

#root {
  height: 100vh;
  width: 100vw;
}

.chat-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background-color: #f5f5f5;
}

.chat-input-area {
  border-top: 1px solid #d9d9d9;
  background-color: white;
  padding: 16px;
}

.message-item {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-start;
}

.message-user {
  justify-content: flex-end;
}

.message-assistant {
  justify-content: flex-start;
}

.message-content {
  max-width: 70%;
  padding: 12px 16px;
  border-radius: 8px;
  word-wrap: break-word;
}

.message-user .message-content {
  background-color: #1890ff;
  color: white;
  margin-left: auto;
}

.message-assistant .message-content {
  background-color: white;
  border: 1px solid #d9d9d9;
  margin-right: auto;
}

.sql-code-block {
  background-color: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 16px;
  margin: 8px 0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  overflow-x: auto;
}

.semantic-sql-info {
  background-color: #f0f9ff;
  border: 1px solid #0ea5e9;
  border-radius: 6px;
  padding: 12px;
  margin: 8px 0;
}

.data-table {
  margin: 8px 0;
}

.data-table .ant-table {
  font-size: 12px;
}

.loading-dots {
  display: inline-block;
}

.loading-dots::after {
  content: '';
  animation: dots 1.5s steps(5, end) infinite;
}

@keyframes dots {
  0%, 20% {
    content: '';
  }
  40% {
    content: '.';
  }
  60% {
    content: '..';
  }
  80%, 100% {
    content: '...';
  }
}
```

```typescript
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
```

```typescript
import axios from 'axios';
import { ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResult, DatabaseSchema } from '../types';

const API_BASE_URL = 'http://localhost:8000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    console.log('API Request:', config.method?.toUpperCase(), config.url);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
    return Promise.reject(error);
  }
);

export const chatAPI = {
  // 发送聊天消息
  sendMessage: async (request: ChatRequest): Promise<ChatResponse> => {
    const response = await api.post('/chat', request);
    return response.data;
  },

  // 执行SQL查询
  executeSQL: async (request: SQLExecutionRequest): Promise<SQLExecutionResult> => {
    const response = await api.post('/execute-sql', request);
    return response.data;
  },

  // 获取会话历史
  getConversationHistory: async (conversationId: string) => {
    const response = await api.get(`/conversation/${conversationId}`);
    return response.data;
  },

  // 清除会话历史
  clearConversation: async (conversationId: string) => {
    const response = await api.delete(`/conversation/${conversationId}`);
    return response.data;
  },
};

export const databaseAPI = {
  // 获取所有表
  getTables: async (): Promise<string[]> => {
    const response = await api.get('/database/tables');
    return response.data.tables;
  },

  // 获取表结构
  getTableSchema: async (tableName: string) => {
    const response = await api.get(`/database/tables/${tableName}/schema`);
    return response.data;
  },

  // 获取完整数据库结构
  getFullDatabaseSchema: async (): Promise<DatabaseSchema> => {
    const response = await api.get('/database/schema');
    return response.data.database_schema;
  },
};

export const systemAPI = {
  // 健康检查
  healthCheck: async () => {
    const response = await api.get('/health');
    return response.data;
  },
};

export default api;
```

```typescriptreact
import React from 'react';
import { Card, Tag, Button, Table, Space, Tooltip, Typography } from 'antd';
import { PlayCircleOutlined, CodeOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { tomorrow } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { ChatMessage as ChatMessageType, SQLExecutionResult } from '../types';

const { Text, Paragraph } = Typography;

interface ChatMessageProps {
  message: ChatMessageType;
  onExecuteSQL?: (sql: string) => void;
  isExecuting?: boolean;
}

const ChatMessage: React.FC<ChatMessageProps> = ({ 
  message, 
  onExecuteSQL, 
  isExecuting = false 
}) => {
  const isUser = message.role === 'user';

  const renderSemanticSQL = (semanticSQL: any) => {
    if (!semanticSQL) return null;

    return (
      <div className="semantic-sql-info">
        <h4 style={{ margin: '0 0 8px 0', color: '#0ea5e9' }}>
          <DatabaseOutlined /> 语义SQL结构
        </h4>
        
        {semanticSQL.tables && semanticSQL.tables.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>涉及表: </Text>
            {semanticSQL.tables.map((table: string) => (
              <Tag key={table} color="blue">{table}</Tag>
            ))}
          </div>
        )}

        {semanticSQL.columns && semanticSQL.columns.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>查询列: </Text>
            {semanticSQL.columns.map((column: string) => (
              <Tag key={column} color="green">{column}</Tag>
            ))}
          </div>
        )}

        {semanticSQL.conditions && semanticSQL.conditions.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>筛选条件: </Text>
            {semanticSQL.conditions.map((condition: any, index: number) => (
              <Tag key={index} color="orange">
                {condition.column} {condition.operator} {condition.value}
              </Tag>
            ))}
          </div>
        )}

        {semanticSQL.aggregations && semanticSQL.aggregations.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>聚合函数: </Text>
            {semanticSQL.aggregations.map((agg: any, index: number) => (
              <Tag key={index} color="purple">
                {agg.function}({agg.column})
              </Tag>
            ))}
          </div>
        )}

        {semanticSQL.joins && semanticSQL.joins.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>表连接: </Text>
            {semanticSQL.joins.map((join: any, index: number) => (
              <Tag key={index} color="cyan">
                {join.type} JOIN {join.table2}
              </Tag>
            ))}
          </div>
        )}
      </div>
    );
  };

  const renderSQLQuery = (sql: string) => {
    if (!sql) return null;

    return (
      <Card 
        size="small" 
        title={
          <Space>
            <CodeOutlined />
            <Text strong>生成的MySQL SQL</Text>
            {onExecuteSQL && (
              <Button
                type="primary"
                size="small"
                icon={<PlayCircleOutlined />}
                loading={isExecuting}
                onClick={() => onExecuteSQL(sql)}
              >
                执行查询
              </Button>
            )}
          </Space>
        }
        style={{ marginTop: 8 }}
      >
        <SyntaxHighlighter
          language="sql"
          style={tomorrow}
          customStyle={{
            margin: 0,
            fontSize: '12px',
            maxHeight: '300px',
            overflow: 'auto'
          }}
        >
          {sql}
        </SyntaxHighlighter>
      </Card>
    );
  };

  const renderExecutionResult = (result: SQLExecutionResult) => {
    if (!result) return null;

    if (!result.success) {
      return (
        <Card size="small" style={{ marginTop: 8 }}>
          <Text type="danger">
            <strong>执行失败:</strong> {result.error}
          </Text>
        </Card>
      );
    }

    if (!result.data || result.data.length === 0) {
      return (
        <Card size="small" style={{ marginTop: 8 }}>
          <Text type="warning">查询成功，但没有返回数据</Text>
        </Card>
      );
    }

    const columns = Object.keys(result.data[0]).map(key => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (text: any) => (
        <Tooltip title={String(text)}>
          <Text style={{ fontSize: '12px' }}>{String(text)}</Text>
        </Tooltip>
      )
    }));

    return (
      <Card 
        size="small" 
        title={`查询结果 (${result.row_count} 行)`}
        style={{ marginTop: 8 }}
      >
        <Table
          columns={columns}
          dataSource={result.data}
          pagination={{
            pageSize: 10,
            size: 'small',
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`
          }}
          scroll={{ x: 'max-content' }}
          size="small"
          className="data-table"
        />
      </Card>
    );
  };

  return (
    <div className={`message-item ${isUser ? 'message-user' : 'message-assistant'}`}>
      <div className="message-content">
        <div style={{ marginBottom: 8 }}>
          <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
            {message.content}
          </Paragraph>
        </div>
        
        {!isUser && (
          <>
            {message.semantic_sql && renderSemanticSQL(message.semantic_sql)}
            {message.sql_query && renderSQLQuery(message.sql_query)}
            {message.execution_result && renderExecutionResult(message.execution_result)}
          </>
        )}
        
        <div style={{ fontSize: '12px', color: '#999', marginTop: 8 }}>
          {message.timestamp.toLocaleTimeString()}
        </div>
      </div>
    </div>
  );
};

export default ChatMessage;
```

```typescriptreact
import React, { useState } from 'react';
import { Input, Button, Space, message } from 'antd';
import { SendOutlined, ClearOutlined } from '@ant-design/icons';

const { TextArea } = Input;

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  onClearChat: () => void;
  disabled?: boolean;
  placeholder?: string;
}

const ChatInput: React.FC<ChatInputProps> = ({
  onSendMessage,
  onClearChat,
  disabled = false,
  placeholder = "请输入您想要查询的问题，例如：查询所有用户的订单总金额"
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSend = async () => {
    if (!inputValue.trim()) {
      message.warning('请输入消息内容');
      return;
    }

    if (disabled) return;

    setIsLoading(true);
    try {
      await onSendMessage(inputValue.trim());
      setInputValue('');
    } catch (error) {
      message.error('发送消息失败');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleClear = () => {
    if (disabled) return;
    onClearChat();
    setInputValue('');
  };

  return (
    <div className="chat-input-area">
      <Space direction="vertical" style={{ width: '100%' }}>
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder={placeholder}
          disabled={disabled}
          autoSize={{ minRows: 2, maxRows: 6 }}
          style={{ fontSize: '14px' }}
        />
        
        <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
          <Button
            icon={<ClearOutlined />}
            onClick={handleClear}
            disabled={disabled}
          >
            清空对话
          </Button>
          
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={isLoading}
            disabled={disabled || !inputValue.trim()}
          >
            发送
          </Button>
        </Space>
      </Space>
    </div>
  );
};

export default ChatInput;
```

```typescriptreact
import React, { useState, useEffect } from 'react';
import { Card, Tree, Spin, Alert, Button, Typography, Space, Tag } from 'antd';
import { DatabaseOutlined, TableOutlined, ReloadOutlined } from '@ant-design/icons';
import { databaseAPI } from '../services/api';
import { DatabaseSchema } from '../types';

const { Title, Text } = Typography;

interface DatabaseSchemaProps {
  onSelectTable?: (tableName: string) => void;
}

const DatabaseSchema: React.FC<DatabaseSchemaProps> = ({ onSelectTable }) => {
  const [schema, setSchema] = useState<DatabaseSchema>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSchema = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const data = await databaseAPI.getFullDatabaseSchema();
      setSchema(data);
    } catch (err: any) {
      setError(err.message || '加载数据库结构失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchema();
  }, []);

  const renderTreeData = () => {
    const treeData = Object.entries(schema).map(([tableName, columns]) => ({
      title: (
        <Space>
          <TableOutlined />
          <Text strong>{tableName}</Text>
          <Tag color="blue">{columns.length} 列</Tag>
        </Space>
      ),
      key: tableName,
      children: columns.map((column, index) => ({
        title: (
          <Space>
            <Text code>{column.Field}</Text>
            <Text type="secondary">{column.Type}</Text>
            {column.Key === 'PRI' && <Tag color="red" size="small">主键</Tag>}
            {column.Null === 'NO' && <Tag color="orange" size="small">非空</Tag>}
            {column.Key === 'UNI' && <Tag color="green" size="small">唯一</Tag>}
          </Space>
        ),
        key: `${tableName}-${column.Field}`,
        isLeaf: true
      }))
    }));

    return treeData;
  };

  const handleSelect = (selectedKeys: React.Key[]) => {
    if (selectedKeys.length > 0) {
      const key = selectedKeys[0] as string;
      const tableName = key.split('-')[0];
      if (onSelectTable) {
        onSelectTable(tableName);
      }
    }
  };

  if (loading) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '20px' }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>加载数据库结构...</div>
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={loadSchema}>
              重试
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <Card
      title={
        <Space>
          <DatabaseOutlined />
          <Title level={5} style={{ margin: 0 }}>数据库结构</Title>
          <Button 
            size="small" 
            icon={<ReloadOutlined />} 
            onClick={loadSchema}
          >
            刷新
          </Button>
        </Space>
      }
      style={{ height: '100%' }}
    >
      <Tree
        treeData={renderTreeData()}
        onSelect={handleSelect}
        showIcon
        defaultExpandAll={false}
        style={{ fontSize: '12px' }}
      />
    </Card>
  );
};

export default DatabaseSchema;
```

```typescriptreact
import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Alert, Button } from 'antd';
import { ReloadOutlined, DatabaseOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseSchema from './components/DatabaseSchema';
import { chatAPI, systemAPI } from './services/api';
import { ChatMessage as ChatMessageType, SQLExecutionResult } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>(uuidv4());
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  const [systemError, setSystemError] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      const status = await systemAPI.healthCheck();
      setSystemStatus('healthy');
      setSystemError(null);
    } catch (error: any) {
      setSystemStatus('error');
      setSystemError(error.message || '系统连接失败');
    }
  };

  useEffect(() => {
    checkSystemStatus();
    
    // 添加欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: '欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。\n\n例如：\n- 查询所有用户的订单总金额\n- 统计每个月的销售数量\n- 找出购买最多的前10个商品',
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages(prev => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const response = await chatAPI.sendMessage({
        message: content,
        conversation_id: conversationId
      });

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date()
      };

      setMessages(prev => [...prev, assistantMessage]);

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId
      });

      // 更新最后一条助手消息的执行结果
      setMessages(prev => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      await chatAPI.clearConversation(conversationId);
      setMessages([]);
      setConversationId(uuidv4());
      
      // 重新添加欢迎消息
      const welcomeMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '对话已清空。我可以帮您将自然语言转换为SQL查询语句，请告诉我您想要查询什么数据。',
        timestamp: new Date()
      };
      setMessages([welcomeMessage]);
      
      message.success('对话已清空');
    } catch (error: any) {
      message.error('清空对话失败: ' + (error.message || '未知错误'));
    }
  };

  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <Alert
            message="检查系统状态..."
            type="info"
            showIcon
            icon={<Spin size="small" />}
          />
        );
      case 'healthy':
        return (
          <Alert
            message="系统运行正常"
            type="success"
            showIcon
            icon={<DatabaseOutlined />}
            action={
              <Button size="small" icon={<ReloadOutlined />} onClick={checkSystemStatus}>
                刷新
              </Button>
            }
          />
        );
      case 'error':
        return (
          <Alert
            message="系统连接异常"
            description={systemError}
            type="error"
            showIcon
            action={
              <Button size="small" icon={<ReloadOutlined />} onClick={checkSystemStatus}>
                重试
              </Button>
            }
          />
        );
      default:
        return null;
    }
  };

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          ChatBI - 智能聊天BI系统
        </div>
        <div style={{ width: '300px' }}>
          {renderSystemStatus()}
        </div>
      </Header>

      <Layout>
        <Sider width={300} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <DatabaseSchema onSelectTable={handleSelectTable} />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>
    </Layout>
  );
};

export default App;
```

现在创建项目配置和依赖文件：


```python
#!/usr/bin/env python3
"""
ChatBI Server 启动脚本
"""

import uvicorn
from app.config import settings

if __name__ == "__main__":
    print("=" * 50)
    print("ChatBI Server 启动中...")
    print(f"API地址: http://{settings.API_HOST}:{settings.API_PORT}")
    print(f"数据库: {settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}")
    print(f"Ollama: {settings.OLLAMA_BASE_URL}")
    print("=" * 50)
    
    uvicorn.run(
        "app.main:app",
        host=settings.API_HOST,
        port=settings.API_PORT,
        reload=True,
        log_level="info"
    )
```

```python
#!/usr/bin/env python3
"""
数据库初始化脚本
创建示例数据表和测试数据
"""

import pymysql
from app.config import settings

def create_sample_database():
    """创建示例数据库和表"""
    
    # 连接数据库（不指定数据库名）
    connection = pymysql.connect(
        host=settings.DB_HOST,
        port=settings.DB_PORT,
        user=settings.DB_USER,
        password=settings.DB_PASSWORD,
        charset='utf8mb4'
    )
    
    try:
        cursor = connection.cursor()
        
        # 创建数据库
        cursor.execute(f"CREATE DATABASE IF NOT EXISTS {settings.DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        cursor.execute(f"USE {settings.DB_NAME}")
        
        # 创建用户表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL,
                email VARCHAR(100) UNIQUE NOT NULL,
                age INT,
                city VARCHAR(50),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # 创建订单表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS orders (
                id INT PRIMARY KEY AUTO_INCREMENT,
                user_id INT NOT NULL,
                product_name VARCHAR(200) NOT NULL,
                amount DECIMAL(10,2) NOT NULL,
                quantity INT NOT NULL,
                order_date DATE NOT NULL,
                status VARCHAR(20) DEFAULT 'pending',
                FOREIGN KEY (user_id) REFERENCES users(id)
            )
        """)
        
        # 创建商品表
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(200) NOT NULL,
                category VARCHAR(100) NOT NULL,
                price DECIMAL(10,2) NOT NULL,
                stock INT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # 插入示例用户数据
        users_data = [
            ('张三', 'zhangsan@example.com', 25, '北京'),
            ('李四', 'lisi@example.com', 30, '上海'),
            ('王五', 'wangwu@example.com', 28, '广州'),
            ('赵六', 'zhaoliu@example.com', 35, '深圳'),
            ('钱七', 'qianqi@example.com', 22, '杭州'),
            ('孙八', 'sunba@example.com', 29, '成都'),
            ('周九', 'zhoujiu@example.com', 31, '武汉'),
            ('吴十', 'wushi@example.com', 27, '西安')
        ]
        
        cursor.execute("DELETE FROM users")  # 清空现有数据
        cursor.executemany(
            "INSERT INTO users (name, email, age, city) VALUES (%s, %s, %s, %s)",
            users_data
        )
        
        # 插入示例商品数据
        products_data = [
            ('iPhone 15', '手机', 7999.00, 100),
            ('MacBook Pro', '电脑', 15999.00, 50),
            ('iPad Air', '平板', 4399.00, 80),
            ('AirPods Pro', '耳机', 1899.00, 200),
            ('Apple Watch', '手表', 2999.00, 150),
            ('Samsung Galaxy', '手机', 5999.00, 120),
            ('Dell XPS', '电脑', 8999.00, 60),
            ('Sony WH-1000XM4', '耳机', 2199.00, 90)
        ]
        
        cursor.execute("DELETE FROM products")  # 清空现有数据
        cursor.executemany(
            "INSERT INTO products (name, category, price, stock) VALUES (%s, %s, %s, %s)",
            products_data
        )
        
        # 插入示例订单数据
        orders_data = [
            (1, 'iPhone 15', 7999.00, 1, '2024-01-15', 'completed'),
            (2, 'MacBook Pro', 15999.00, 1, '2024-01-16', 'completed'),
            (1, 'AirPods Pro', 1899.00, 2, '2024-01-17', 'pending'),
            (3, 'iPad Air', 4399.00, 1, '2024-01-18', 'completed'),
            (4, 'Apple Watch', 2999.00, 1, '2024-01-19', 'completed'),
            (5, 'Samsung Galaxy', 5999.00, 1, '2024-01-20', 'pending'),
            (2, 'Dell XPS', 8999.00, 1, '2024-01-21', 'completed'),
            (6, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-22', 'completed'),
            (7, 'iPhone 15', 7999.00, 1, '2024-01-23', 'completed'),
            (8, 'MacBook Pro', 15999.00, 1, '2024-01-24', 'pending'),
            (3, 'iPad Air', 4399.00, 1, '2024-01-25', 'completed'),
            (4, 'AirPods Pro', 1899.00, 2, '2024-01-26', 'completed'),
            (5, 'Apple Watch', 2999.00, 1, '2024-01-27', 'pending'),
            (6, 'Samsung Galaxy', 5999.00, 1, '2024-01-28', 'completed'),
            (7, 'Dell XPS', 8999.00, 1, '2024-01-29', 'completed'),
            (8, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-30', 'completed')
        ]
        
        cursor.execute("DELETE FROM orders")  # 清空现有数据
        cursor.executemany(
            "INSERT INTO orders (user_id, product_name, amount, quantity, order_date, status) VALUES (%s, %s, %s, %s, %s, %s)",
            orders_data
        )
        
        connection.commit()
        print("✅ 数据库初始化完成！")
        print(f"✅ 数据库: {settings.DB_NAME}")
        print("✅ 创建的表:")
        print("  - users (用户表)")
        print("  - orders (订单表)")
        print("  - products (商品表)")
        print("✅ 插入示例数据:")
        print("  - 8个用户")
        print("  - 8个商品")
        print("  - 16个订单")
        
    except Exception as e:
        print(f"❌ 数据库初始化失败: {e}")
        connection.rollback()
    finally:
        connection.close()

if __name__ == "__main__":
    create_sample_database()
```

```dockercompose
version: '3.8'

services:
  # MySQL数据库
  mysql:
    image: mysql:8.0
    container_name: chatbi-mysql
    environment:
      MYSQL_ROOT_PASSWORD: password
      MYSQL_DATABASE: test_db
      MYSQL_USER: chatbi
      MYSQL_PASSWORD: chatbi123
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./chatbi-server/init.sql:/docker-entrypoint-initdb.d/init.sql
    command: --default-authentication-plugin=mysql_native_password
    restart: unless-stopped

  # Ollama服务
  ollama:
    image: ollama/ollama:latest
    container_name: chatbi-ollama
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    environment:
      - OLLAMA_HOST=0.0.0.0
    restart: unless-stopped

  # 后端API服务
  chatbi-server:
    build:
      context: ./chatbi-server
      dockerfile: Dockerfile
    container_name: chatbi-server
    ports:
      - "8000:8000"
    environment:
      - DB_HOST=mysql
      - DB_PORT=3306
      - DB_USER=root
      - DB_PASSWORD=password
      - DB_NAME=test_db
      - OLLAMA_BASE_URL=http://ollama:11434
      - OLLAMA_MODEL=llama2
    depends_on:
      - mysql
      - ollama
    volumes:
      - ./chatbi-server:/app
    restart: unless-stopped

  # 前端UI服务
  chatbi-ui:
    build:
      context: ./chatbi-ui
      dockerfile: Dockerfile
    container_name: chatbi-ui
    ports:
      - "3000:3000"
    environment:
      - VITE_API_BASE_URL=http://localhost:8000/api
    depends_on:
      - chatbi-server
    restart: unless-stopped

volumes:
  mysql_data:
  ollama_data:
```

```dockerfile
FROM python:3.11-slim

WORKDIR /app

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    gcc \
    default-libmysqlclient-dev \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件
COPY requirements.txt .

# 安装Python依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY . .

# 暴露端口
EXPOSE 8000

# 启动命令
CMD ["python", "run.py"]
```

```dockerfile
FROM node:18-alpine

WORKDIR /app

# 复制依赖文件
COPY package*.json ./

# 安装依赖
RUN npm install

# 复制源代码
COPY . .

# 暴露端口
EXPOSE 3000

# 启动开发服务器
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]
```

```sql
-- 初始化数据库脚本
-- 这个文件会在MySQL容器启动时自动执行

USE test_db;

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    age INT,
    city VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建订单表
CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    quantity INT NOT NULL,
    order_date DATE NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 创建商品表
CREATE TABLE IF NOT EXISTS products (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 插入示例用户数据
INSERT INTO users (name, email, age, city) VALUES
('张三', 'zhangsan@example.com', 25, '北京'),
('李四', 'lisi@example.com', 30, '上海'),
('王五', 'wangwu@example.com', 28, '广州'),
('赵六', 'zhaoliu@example.com', 35, '深圳'),
('钱七', 'qianqi@example.com', 22, '杭州'),
('孙八', 'sunba@example.com', 29, '成都'),
('周九', 'zhoujiu@example.com', 31, '武汉'),
('吴十', 'wushi@example.com', 27, '西安');

-- 插入示例商品数据
INSERT INTO products (name, category, price, stock) VALUES
('iPhone 15', '手机', 7999.00, 100),
('MacBook Pro', '电脑', 15999.00, 50),
('iPad Air', '平板', 4399.00, 80),
('AirPods Pro', '耳机', 1899.00, 200),
('Apple Watch', '手表', 2999.00, 150),
('Samsung Galaxy', '手机', 5999.00, 120),
('Dell XPS', '电脑', 8999.00, 60),
('Sony WH-1000XM4', '耳机', 2199.00, 90);

-- 插入示例订单数据
INSERT INTO orders (user_id, product_name, amount, quantity, order_date, status) VALUES
(1, 'iPhone 15', 7999.00, 1, '2024-01-15', 'completed'),
(2, 'MacBook Pro', 15999.00, 1, '2024-01-16', 'completed'),
(1, 'AirPods Pro', 1899.00, 2, '2024-01-17', 'pending'),
(3, 'iPad Air', 4399.00, 1, '2024-01-18', 'completed'),
(4, 'Apple Watch', 2999.00, 1, '2024-01-19', 'completed'),
(5, 'Samsung Galaxy', 5999.00, 1, '2024-01-20', 'pending'),
(2, 'Dell XPS', 8999.00, 1, '2024-01-21', 'completed'),
(6, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-22', 'completed'),
(7, 'iPhone 15', 7999.00, 1, '2024-01-23', 'completed'),
(8, 'MacBook Pro', 15999.00, 1, '2024-01-24', 'pending'),
(3, 'iPad Air', 4399.00, 1, '2024-01-25', 'completed'),
(4, 'AirPods Pro', 1899.00, 2, '2024-01-26', 'completed'),
(5, 'Apple Watch', 2999.00, 1, '2024-01-27', 'pending'),
(6, 'Samsung Galaxy', 5999.00, 1, '2024-01-28', 'completed'),
(7, 'Dell XPS', 8999.00, 1, '2024-01-29', 'completed'),
(8, 'Sony WH-1000XM4', 2199.00, 1, '2024-01-30', 'completed');
```

最后创建README文档：


```markdown
# ChatBI - 智能聊天BI系统

一个基于自然语言转语义SQL的智能聊天BI系统，使用Python/LangChain/FastAPI/Ollama等现代技术栈构建，支持前后端分离架构。

## 🚀 功能特性

- **自然语言理解**: 将用户的自然语言查询转换为结构化的语义SQL
- **智能SQL生成**: 基于语义SQL自动生成MySQL查询语句
- **实时查询执行**: 支持SQL查询的执行和结果展示
- **数据库结构展示**: 可视化展示数据库表结构，便于用户理解
- **会话管理**: 支持多轮对话，保持上下文
- **现代化UI**: 基于React + Ant Design的响应式界面

## 🏗️ 系统架构

```
chat-bi-ai-poc/
├── chatbi-server/          # 后端API服务
│   ├── app/                # 应用核心代码
│   │   ├── main.py         # FastAPI主应用
│   │   ├── config.py       # 配置管理
│   │   ├── models.py       # 数据模型
│   │   ├── semantic_sql_converter.py  # 语义SQL转换器
│   │   ├── database.py     # 数据库管理
│   │   └── chat_service.py # 聊天服务
│   ├── requirements.txt    # Python依赖
│   ├── Dockerfile         # Docker配置
│   └── run.py            # 启动脚本
├── chatbi-ui/             # 前端UI应用
│   ├── src/               # 源代码
│   │   ├── components/    # React组件
│   │   ├── services/      # API服务
│   │   ├── types/         # TypeScript类型
│   │   └── App.tsx        # 主应用组件
│   ├── package.json       # Node.js依赖
│   └── Dockerfile        # Docker配置
└── docker-compose.yml     # 容器编排配置
```

## 🛠️ 技术栈

### 后端
- **FastAPI**: 现代高性能Web框架
- **LangChain**: 大语言模型应用开发框架
- **Ollama**: 本地LLM服务
- **PyMySQL**: MySQL数据库连接器
- **Pydantic**: 数据验证和序列化
- **Uvicorn**: ASGI服务器

### 前端
- **React 18**: 用户界面库
- **TypeScript**: 类型安全的JavaScript
- **Ant Design**: 企业级UI组件库
- **Axios**: HTTP客户端
- **React Syntax Highlighter**: 代码高亮
- **Vite**: 现代构建工具

### 数据库
- **MySQL 8.0**: 关系型数据库
- **Docker**: 容器化部署

## 📦 快速开始

### 方式一：Docker Compose（推荐）

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd chat-bi-ai-poc
   ```

2. **启动所有服务**
   ```bash
   docker-compose up -d
   ```

3. **下载Ollama模型**
   ```bash
   # 进入Ollama容器
   docker exec -it chatbi-ollama bash
   
   # 下载llama2模型（需要一些时间）
   ollama pull llama2
   ```

4. **访问应用**
   - 前端界面: http://localhost:3000
   - 后端API: http://localhost:8000
   - API文档: http://localhost:8000/docs

### 方式二：本地开发

#### 环境要求
- Python 3.11+
- Node.js 18+
- MySQL 8.0+
- Ollama

#### 后端启动

1. **安装Python依赖**
   ```bash
   cd chatbi-server
   pip install -r requirements.txt
   ```

2. **配置环境变量**
   ```bash
   cp env.example .env
   # 编辑.env文件，配置数据库和Ollama连接信息
   ```

3. **初始化数据库**
   ```bash
   python setup_database.py
   ```

4. **启动Ollama服务**
   ```bash
   ollama serve
   # 在另一个终端下载模型
   ollama pull llama2
   ```

5. **启动后端服务**
   ```bash
   python run.py
   ```

#### 前端启动

1. **安装Node.js依赖**
   ```bash
   cd chatbi-ui
   npm install
   ```

2. **启动开发服务器**
   ```bash
   npm run dev
   ```

## 🎯 使用示例

### 自然语言查询示例

1. **基础查询**
   - "查询所有用户信息"
   - "显示所有订单"

2. **条件查询**
   - "查询北京的用户的订单"
   - "找出金额大于5000的订单"

3. **聚合查询**
   - "统计每个用户的订单总金额"
   - "计算每个月的销售数量"

4. **复杂查询**
   - "查询购买最多的前10个商品"
   - "找出每个城市的平均订单金额"

### API接口

#### 发送聊天消息
```bash
POST /api/chat
Content-Type: application/json

{
  "message": "查询所有用户的订单总金额",
  "conversation_id": "optional-conversation-id"
}
```

#### 执行SQL查询
```bash
POST /api/execute-sql
Content-Type: application/json

{
  "sql_query": "SELECT u.name, SUM(o.amount) as total FROM users u JOIN orders o ON u.id = o.user_id GROUP BY u.id",
  "conversation_id": "optional-conversation-id"
}
```

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `OLLAMA_BASE_URL` | Ollama服务地址 | `http://localhost:11434` |
| `OLLAMA_MODEL` | 使用的模型名称 | `llama2` |
| `DB_HOST` | 数据库主机 | `localhost` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_USER` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `password` |
| `DB_NAME` | 数据库名称 | `test_db` |
| `API_HOST` | API服务主机 | `0.0.0.0` |
| `API_PORT` | API服务端口 | `8000` |

### 数据库结构

系统包含三个示例表：

- **users**: 用户信息表
- **orders**: 订单信息表  
- **products**: 商品信息表

## 🚨 注意事项

1. **模型下载**: 首次使用需要下载Ollama模型，可能需要较长时间
2. **内存要求**: Ollama模型运行需要足够的内存（建议8GB+）
3. **网络连接**: 确保Ollama服务可以正常访问
4. **数据库权限**: 确保数据库用户有创建表和插入数据的权限

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [LangChain](https://github.com/langchain-ai/langchain) - 大语言模型应用开发框架
- [Ollama](https://github.com/ollama/ollama) - 本地LLM服务
- [FastAPI](https://github.com/tiangolo/fastapi) - 现代Web框架
- [Ant Design](https://github.com/ant-design/ant-design) - 企业级UI组件库

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件
- 创建 Pull Request

---

**ChatBI** - 让数据分析变得简单自然 🚀
```

```ignore
# Python
__pycache__/
*.py[cod]
*$py.class
*.so
.Python
build/
develop-eggs/
dist/
downloads/
eggs/
.eggs/
lib/
lib64/
parts/
sdist/
var/
wheels/
pip-wheel-metadata/
share/python-wheels/
*.egg-info/
.installed.cfg
*.egg
MANIFEST

# Virtual environments
.env
.venv
env/
venv/
ENV/
env.bak/
venv.bak/

# Node.js
node_modules/
npm-debug.log*
yarn-debug.log*
yarn-error.log*
.pnpm-debug.log*
.npm
.yarn-integrity

# Build outputs
dist/
build/
out/

# IDE
.vscode/
.idea/
*.swp
*.swo
*~

# OS
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes
ehthumbs.db
Thumbs.db

# Logs
logs/
*.log

# Runtime data
pids/
*.pid
*.seed
*.pid.lock

# Coverage directory used by tools like istanbul
coverage/
*.lcov

# nyc test coverage
.nyc_output

# Dependency directories
jspm_packages/

# Optional npm cache directory
.npm

# Optional eslint cache
.eslintcache

# Microbundle cache
.rpt2_cache/
.rts2_cache_cjs/
.rts2_cache_es/
.rts2_cache_umd/

# Optional REPL history
.node_repl_history

# Output of 'npm pack'
*.tgz

# Yarn Integrity file
.yarn-integrity

# dotenv environment variables file
.env
.env.test
.env.local
.env.development.local
.env.test.local
.env.production.local

# Docker
.dockerignore

# Database
*.db
*.sqlite
*.sqlite3

# Temporary files
tmp/
temp/
```

## �� 项目创建完成！

我已经成功为您创建了一个完整的基于Python/LangChain/FastAPI/Ollama的自然语言转语义SQL系统，采用前后端分离架构。以下是项目的主要特点：

### 📁 项目结构

```
chat-bi-ai-poc/
├── chatbi-server/          # 后端API服务 (FastAPI + LangChain + Ollama)
│   ├── app/                # 核心应用代码
│   │   ├── main.py         # FastAPI主应用和路由
│   │   ├── semantic_sql_converter.py  # 语义SQL转换核心逻辑
│   │   ├── chat_service.py # 聊天服务
│   │   ├── database.py     # 数据库管理
│   │   ├── models.py       # 数据模型
│   │   └── config.py       # 配置管理
│   ├── requirements.txt    # Python依赖
│   ├── Dockerfile         # Docker配置
│   └── setup_database.py  # 数据库初始化脚本
├── chatbi-ui/             # 前端UI应用 (React + TypeScript + Ant Design)
│   ├── src/               # 源代码
│   │   ├── components/    # React组件
│   │   ├── services/      # API服务
│   │   ├── types/         # TypeScript类型定义
│   │   └── App.tsx        # 主应用
│   ├── package.json       # Node.js依赖
│   └── Dockerfile        # Docker配置
├── docker-compose.yml     # 容器编排配置
└── README.md             # 详细文档
```

### �� 核心功能

1. **自然语言理解**: 使用LangChain + Ollama将自然语言转换为结构化语义SQL
2. **智能SQL生成**: 基于语义SQL自动生成MySQL查询语句
3. **实时查询执行**: 支持SQL查询的执行和结果展示
4. **数据库结构展示**: 可视化展示数据库表结构
5. **会话管理**: 支持多轮对话，保持上下文
6. **现代化UI**: 基于React + Ant Design的响应式界面

### 🛠️ 技术栈

**后端**:
- FastAPI (Web框架)
- LangChain (LLM应用框架)
- Ollama (本地LLM服务)
- PyMySQL (MySQL连接器)
- Pydantic (数据验证)

**前端**:
- React 18 + TypeScript
- Ant Design (UI组件库)
- Axios (HTTP客户端)
- React Syntax Highlighter (代码高亮)

### 🎯 使用方式

#### 方式一：Docker Compose（推荐）
```bash
# 启动所有服务
docker-compose up -d

# 下载Ollama模型
docker exec -it chatbi-ollama ollama pull llama2

# 访问应用
# 前端: http://localhost:3000
# 后端API: http://localhost:8000
```

#### 方式二：本地开发
```bash
# 后端
cd chatbi-server
pip install -r requirements.txt
python setup_database.py  # 初始化数据库
ollama serve              # 启动Ollama
ollama pull llama2        # 下载模型
python run.py            # 启动后端

# 前端
cd chatbi-ui
npm install
npm run dev              # 启动前端
```

### �� 使用示例

用户可以通过自然语言进行查询：
- "查询所有用户的订单总金额"
- "统计每个月的销售数量" 
- "找出购买最多的前10个商品"
- "查询北京的用户的平均订单金额"

系统会：
1. 将自然语言转换为语义SQL结构
2. 生成对应的MySQL查询语句
3. 执行查询并展示结果
4. 支持多轮对话和上下文理解

### �� 系统特性

- ✅ 完整的语义SQL转换流程
- ✅ 支持复杂查询和聚合操作
- ✅ 实时SQL执行和结果展示
- ✅ 数据库结构可视化
- ✅ 多轮对话支持
- ✅ 现代化响应式UI
- ✅ Docker容器化部署
- ✅ 完整的API文档
- ✅ 示例数据和测试用例

项目已经完全可用，包含了详细的README文档和部署说明。您可以立即开始使用这个智能聊天BI系统！

```plaintext
chat-bi-ai-poc/
├── chatbi-server/          # 后端API服务 (FastAPI + LangChain + Ollama)
│   ├── app/                # 核心应用代码
│   │   ├── main.py         # FastAPI主应用和路由
│   │   ├── semantic_sql_converter.py  # 语义SQL转换核心逻辑
│   │   ├── chat_service.py # 聊天服务
│   │   ├── database.py     # 数据库管理
│   │   ├── models.py       # 数据模型
│   │   └── config.py       # 配置管理
│   ├── requirements.txt    # Python依赖
│   ├── Dockerfile         # Docker配置
│   └── setup_database.py  # 数据库初始化脚本
├── chatbi-ui/             # 前端UI应用 (React + TypeScript + Ant Design)
│   ├── src/               # 源代码
│   │   ├── components/    # React组件
│   │   ├── services/      # API服务
│   │   ├── types/         # TypeScript类型定义
│   │   └── App.tsx        # 主应用
│   ├── package.json       # Node.js依赖
│   └── Dockerfile        # Docker配置
├── docker-compose.yml     # 容器编排配置
└── README.md             # 详细文档
```

```shellscript
# 启动所有服务
docker-compose up -d

# 下载Ollama模型
docker exec -it chatbi-ollama ollama pull llama2

# 访问应用
# 前端: http://localhost:3000
# 后端API: http://localhost:8000
```

```shellscript
# 后端
cd chatbi-server
pip install -r requirements.txt
python setup_database.py  # 初始化数据库
ollama serve              # 启动Ollama
ollama pull llama2        # 下载模型
python run.py            # 启动后端

# 前端
cd chatbi-ui
npm install
npm run dev              # 启动前端
```

