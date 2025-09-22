from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from typing import Dict, Any
import uvicorn
import logging
import asyncio

from app.models import ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResponse
from app.chat_service import chat_service
from app.database import db_manager
from app.config import settings
from app.metadata_builder import schema_metadata_builder

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
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s"
        
    )
    logger = logging.getLogger("chatbi")
    print("ChatBI Server 正在启动...")
    print(f"数据库连接: {settings.DB_HOST}:{settings.DB_PORT}/{settings.DB_NAME}")
    print(f"Ollama服务: {settings.OLLAMA_BASE_URL}")
    print(f"Ollama模型: {settings.OLLAMA_MODEL}")
    logger.info("Server started with DB %s:%s/%s, Ollama %s, Model %s",
                settings.DB_HOST, settings.DB_PORT, settings.DB_NAME,
                settings.OLLAMA_BASE_URL, settings.OLLAMA_MODEL)

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

# 兼容旧路径：/api/health
@app.get("/api/health")
async def legacy_health_check():
    return await health_check()

@app.post("/api/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """聊天接口"""
    try:
        logger = logging.getLogger("chatbi.api.chat")
        logger.info("Incoming chat: conversation_id=%s, message=%s",
                    request.conversation_id, request.message)
        logger.info("Using Ollama: base=%s, model=%s",
                    settings.OLLAMA_BASE_URL, settings.OLLAMA_MODEL)
        
        # 使用 asyncio.wait_for 设置超时时间为2分钟
        response = await asyncio.wait_for(
            asyncio.to_thread(chat_service.process_chat_message, request),
            timeout=120.0
        )
        
        try:
            logger.info("Generated SQL: %s", response.sql_query)
            if response.semantic_sql:
                logger.info("Semantic SQL: tables=%s, cols=%s, joins=%s, conds=%s, group_by=%s, order_by=%s, limit=%s",
                            
                            response.semantic_sql.tables,
                            response.semantic_sql.columns,
                            response.semantic_sql.joins,
                            response.semantic_sql.conditions,
                            response.semantic_sql.group_by,
                            response.semantic_sql.order_by,
                            response.semantic_sql.limit)
        except Exception:
            logger.warning("Failed to log semantic/sql details")
        return response
    except asyncio.TimeoutError:
        logger.error("Chat request timed out after 120 seconds")
        raise HTTPException(status_code=504, detail="请求超时，大模型响应时间过长")
    except Exception as e:
        logging.getLogger("chatbi.api.chat").exception("/api/chat failed: %s", e)
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

@app.get("/api/metadata")
async def get_enriched_metadata():
    """获取增强的数据库元数据（包含表名、字段、字段样例值、表样例行）。"""
    try:
        metadata = schema_metadata_builder.build_database_metadata()
        return {"metadata": metadata}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.API_HOST,
        port=settings.API_PORT,
        reload=True
    )
