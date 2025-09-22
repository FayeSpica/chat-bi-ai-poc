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
