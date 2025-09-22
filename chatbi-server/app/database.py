import pymysql
from typing import List, Dict, Any, Optional
from app.config import settings
from app.models import SQLExecutionResponse, DatabaseConnection

class DatabaseManager:
    """数据库管理器"""
    
    def __init__(self):
        self.connection = None
        self.dynamic_connection = None
        self.dynamic_connection_config = None
    
    def get_connection(self, dynamic_connection: Optional[DatabaseConnection] = None):
        """获取数据库连接"""
        # 如果提供了动态连接配置，使用动态连接
        if dynamic_connection:
            # 如果配置相同且连接存在，直接返回
            if (self.dynamic_connection_config and 
                self.dynamic_connection_config.id == dynamic_connection.id and 
                self.dynamic_connection):
                return self.dynamic_connection
            
            # 创建新的动态连接
            try:
                self.dynamic_connection = pymysql.connect(
                    host=dynamic_connection.host,
                    port=dynamic_connection.port,
                    user=dynamic_connection.username,
                    password=dynamic_connection.password,
                    database=dynamic_connection.database,
                    charset=dynamic_connection.charset,
                    use_unicode=True,
                    init_command=f'SET NAMES {dynamic_connection.charset}',
                    autocommit=True
                )
                self.dynamic_connection_config = dynamic_connection
                return self.dynamic_connection
            except Exception as e:
                print(f"Failed to create dynamic connection: {e}")
                # 如果动态连接失败，回退到默认连接
                return self.get_default_connection()
        
        # 使用默认连接
        return self.get_default_connection()
    
    def get_default_connection(self):
        """获取默认数据库连接"""
        if not self.connection:
            self.connection = pymysql.connect(
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                user=settings.DB_USER,
                password=settings.DB_PASSWORD,
                database=settings.DB_NAME,
                charset='utf8mb4',
                use_unicode=True,
                init_command='SET NAMES utf8mb4',
                autocommit=True
            )
        return self.connection
    
    def execute_query(self, sql: str, dynamic_connection: Optional[DatabaseConnection] = None) -> SQLExecutionResponse:
        """执行SQL查询"""
        try:
            connection = self.get_connection(dynamic_connection)
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
    
    def get_table_schema(self, table_name: str, dynamic_connection: Optional[DatabaseConnection] = None) -> List[Dict[str, Any]]:
        """获取表结构信息"""
        try:
            connection = self.get_connection(dynamic_connection)
            cursor = connection.cursor(pymysql.cursors.DictCursor)
            
            cursor.execute(f"DESCRIBE {table_name}")
            schema = cursor.fetchall()
            
            cursor.close()
            return schema
            
        except Exception as e:
            print(f"Error getting schema for table {table_name}: {e}")
            return []
    
    def get_all_tables(self, dynamic_connection: Optional[DatabaseConnection] = None) -> List[str]:
        """获取所有表名"""
        try:
            connection = self.get_connection(dynamic_connection)
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
        if self.dynamic_connection:
            self.dynamic_connection.close()
            self.dynamic_connection = None
            self.dynamic_connection_config = None

# 创建全局实例
db_manager = DatabaseManager()
