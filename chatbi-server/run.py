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
