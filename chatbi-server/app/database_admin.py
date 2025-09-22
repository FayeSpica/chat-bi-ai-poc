import json
import pymysql
import uuid
from typing import List, Dict, Any, Optional
from datetime import datetime
import logging
from app.models import (
    DatabaseConnection, DatabaseConnectionCreate, DatabaseConnectionUpdate,
    DatabaseConnectionTest, TableInfo, ColumnInfo, TableSchema, CommentUpdate
)
from app.config import settings

class DatabaseAdminService:
    """数据库后台管理服务"""
    
    def __init__(self):
        self.connections_file = "database_connections.json"
        self.connections = self._load_connections()
        # 如果没有连接，自动创建默认连接
        if not self.connections:
            self._create_default_connection()
    
    def _load_connections(self) -> Dict[str, DatabaseConnection]:
        """加载数据库连接配置"""
        try:
            with open(self.connections_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                connections = {}
                for conn_id, conn_data in data.items():
                    connections[conn_id] = DatabaseConnection(**conn_data)
                return connections
        except FileNotFoundError:
            return {}
        except Exception as e:
            logging.error(f"Error loading connections: {e}")
            return {}
    
    def _create_default_connection(self):
        """创建默认数据库连接"""
        try:
            connection_id = str(uuid.uuid4())
            now = datetime.now().isoformat()
            
            default_connection = DatabaseConnection(
                id=connection_id,
                name="默认数据库",
                host=settings.DB_HOST,
                port=settings.DB_PORT,
                username=settings.DB_USER,
                password=settings.DB_PASSWORD,
                database=settings.DB_NAME,
                charset="utf8mb4",
                description="ChatBI系统默认数据库连接",
                is_active=True,
                created_at=now,
                updated_at=now
            )
            
            self.connections[connection_id] = default_connection
            self._save_connections()
            logging.info(f"Created default database connection: {default_connection.name}")
            
        except Exception as e:
            logging.error(f"Failed to create default connection: {e}")
    
    def _ensure_default_connection(self):
        """确保默认连接存在"""
        # 检查是否已经有默认连接
        has_default = any(
            conn.name == "默认数据库" and 
            conn.host == settings.DB_HOST and 
            conn.database == settings.DB_NAME 
            for conn in self.connections.values()
        )
        
        if not has_default and not self.connections:
            self._create_default_connection()
    
    def _save_connections(self):
        """保存数据库连接配置"""
        try:
            data = {}
            for conn_id, conn in self.connections.items():
                data[conn_id] = conn.dict()
            
            with open(self.connections_file, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
        except Exception as e:
            logging.error(f"Error saving connections: {e}")
            raise
    
    def _get_connection_by_id(self, connection_id: str) -> Optional[DatabaseConnection]:
        """根据ID获取数据库连接配置"""
        return self.connections.get(connection_id)
    
    def _create_pymysql_connection(self, conn: DatabaseConnection) -> pymysql.Connection:
        """创建PyMySQL连接"""
        return pymysql.connect(
            host=conn.host,
            port=conn.port,
            user=conn.username,
            password=conn.password,
            database=conn.database,
            charset=conn.charset,
            use_unicode=True,
            init_command=f'SET NAMES {conn.charset}',
            autocommit=True
        )
    
    # 数据库连接管理
    def create_connection(self, conn_data: DatabaseConnectionCreate) -> DatabaseConnection:
        """创建数据库连接"""
        connection_id = str(uuid.uuid4())
        now = datetime.now().isoformat()
        
        connection = DatabaseConnection(
            id=connection_id,
            name=conn_data.name,
            host=conn_data.host,
            port=conn_data.port,
            username=conn_data.username,
            password=conn_data.password,
            database=conn_data.database,
            charset=conn_data.charset,
            description=conn_data.description,
            is_active=True,
            created_at=now,
            updated_at=now
        )
        
        self.connections[connection_id] = connection
        self._save_connections()
        
        return connection
    
    def get_all_connections(self) -> List[DatabaseConnection]:
        """获取所有数据库连接"""
        # 确保至少有一个默认连接
        self._ensure_default_connection()
        return list(self.connections.values())
    
    def get_connection(self, connection_id: str) -> Optional[DatabaseConnection]:
        """获取指定数据库连接"""
        return self._get_connection_by_id(connection_id)
    
    def get_active_connection(self) -> Optional[DatabaseConnection]:
        """获取当前活动的数据库连接（用于聊天功能）"""
        # 优先返回第一个活跃的连接，如果没有则返回第一个连接
        active_connections = [conn for conn in self.connections.values() if conn.is_active]
        if active_connections:
            return active_connections[0]
        
        # 如果没有活跃连接，返回第一个连接
        if self.connections:
            return list(self.connections.values())[0]
        
        # 如果没有任何连接，尝试创建默认连接
        self._create_default_connection()
        if self.connections:
            return list(self.connections.values())[0]
        
        return None
    
    def update_connection(self, connection_id: str, update_data: DatabaseConnectionUpdate) -> Optional[DatabaseConnection]:
        """更新数据库连接"""
        connection = self._get_connection_by_id(connection_id)
        if not connection:
            return None
        
        # 更新字段
        update_dict = update_data.dict(exclude_unset=True)
        for field, value in update_dict.items():
            setattr(connection, field, value)
        
        connection.updated_at = datetime.now().isoformat()
        
        self.connections[connection_id] = connection
        self._save_connections()
        
        return connection
    
    def delete_connection(self, connection_id: str) -> bool:
        """删除数据库连接"""
        if connection_id not in self.connections:
            return False
        
        # 如果只有一个连接，不允许删除
        if len(self.connections) <= 1:
            return False
        
        connection = self.connections[connection_id]
        
        # 如果是默认连接且只有一个连接，不允许删除
        if (connection.name == "默认数据库" and 
            connection.host == settings.DB_HOST and 
            connection.database == settings.DB_NAME and 
            len(self.connections) == 1):
            return False
        
        del self.connections[connection_id]
        self._save_connections()
        return True
    
    def test_connection(self, test_data: DatabaseConnectionTest) -> Dict[str, Any]:
        """测试数据库连接"""
        try:
            connection = pymysql.connect(
                host=test_data.host,
                port=test_data.port,
                user=test_data.username,
                password=test_data.password,
                database=test_data.database,
                charset=test_data.charset,
                use_unicode=True,
                init_command=f'SET NAMES {test_data.charset}',
                autocommit=True
            )
            
            # 测试查询
            cursor = connection.cursor()
            cursor.execute("SELECT VERSION()")
            version = cursor.fetchone()[0]
            cursor.close()
            connection.close()
            
            return {
                "success": True,
                "message": "连接成功",
                "version": version
            }
        except Exception as e:
            return {
                "success": False,
                "message": f"连接失败: {str(e)}"
            }
    
    # 数据库元数据管理
    def get_tables(self, connection_id: str) -> List[TableInfo]:
        """获取数据库中的所有表"""
        connection_config = self._get_connection_by_id(connection_id)
        if not connection_config:
            raise ValueError(f"Database connection {connection_id} not found")
        
        try:
            conn = self._create_pymysql_connection(connection_config)
            cursor = conn.cursor(pymysql.cursors.DictCursor)
            
            # 获取表信息
            cursor.execute("""
                SELECT 
                    TABLE_NAME as table_name,
                    TABLE_COMMENT as table_comment,
                    TABLE_ROWS as table_rows,
                    ROUND(((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024), 2) as table_size,
                    ENGINE as engine,
                    TABLE_COLLATION as charset
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = %s
                ORDER BY TABLE_NAME
            """, (connection_config.database,))
            
            tables = []
            for row in cursor.fetchall():
                # 将Decimal类型转换为字符串
                if 'table_size' in row and row['table_size'] is not None:
                    row['table_size'] = str(row['table_size'])
                tables.append(TableInfo(**row))
            
            cursor.close()
            conn.close()
            
            return tables
            
        except pymysql.Error as e:
            error_msg = f"数据库连接失败: {str(e)}"
            logging.error(f"Database connection error for {connection_config.name}: {e}")
            raise ConnectionError(error_msg)
        except Exception as e:
            error_msg = f"获取表信息失败: {str(e)}"
            logging.error(f"Error getting tables for {connection_config.name}: {e}")
            raise RuntimeError(error_msg)
    
    def get_table_schema(self, connection_id: str, table_name: str) -> TableSchema:
        """获取表结构"""
        connection_config = self._get_connection_by_id(connection_id)
        if not connection_config:
            raise ValueError(f"Database connection {connection_id} not found")
        
        try:
            conn = self._create_pymysql_connection(connection_config)
            cursor = conn.cursor(pymysql.cursors.DictCursor)
            
            # 获取表注释
            cursor.execute("""
                SELECT TABLE_COMMENT as table_comment
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
            """, (connection_config.database, table_name))
            
            table_result = cursor.fetchone()
            table_comment = table_result['table_comment'] if table_result else None
            
            # 获取字段信息
            cursor.execute("""
                SELECT 
                    COLUMN_NAME as column_name,
                    DATA_TYPE as data_type,
                    IS_NULLABLE as is_nullable,
                    COLUMN_KEY as column_key,
                    COLUMN_DEFAULT as column_default,
                    EXTRA as extra,
                    COLUMN_COMMENT as column_comment,
                    ORDINAL_POSITION as column_order
                FROM information_schema.COLUMNS 
                WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s
                ORDER BY ORDINAL_POSITION
            """, (connection_config.database, table_name))
            
            columns = []
            for row in cursor.fetchall():
                columns.append(ColumnInfo(
                    column_name=row['column_name'],
                    data_type=row['data_type'],
                    is_nullable=row['is_nullable'] == 'YES',
                    column_key=row['column_key'],
                    column_default=row['column_default'],
                    extra=row['extra'],
                    column_comment=row['column_comment'],
                    column_order=row['column_order']
                ))
            
            cursor.close()
            conn.close()
            
            return TableSchema(
                table_name=table_name,
                table_comment=table_comment,
                columns=columns
            )
            
        except pymysql.Error as e:
            error_msg = f"数据库连接失败: {str(e)}"
            logging.error(f"Database connection error for {connection_config.name}: {e}")
            raise ConnectionError(error_msg)
        except Exception as e:
            error_msg = f"获取表结构失败: {str(e)}"
            logging.error(f"Error getting table schema for {table_name}: {e}")
            raise RuntimeError(error_msg)
    
    def update_comment(self, connection_id: str, comment_update: CommentUpdate) -> bool:
        """更新表或字段注释"""
        connection_config = self._get_connection_by_id(connection_id)
        if not connection_config:
            raise ValueError(f"Database connection {connection_id} not found")
        
        try:
            conn = self._create_pymysql_connection(connection_config)
            cursor = conn.cursor()
            
            if comment_update.column_name:
                # 更新字段注释
                # 首先获取字段的完整定义
                cursor.execute("""
                    SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA
                    FROM information_schema.COLUMNS 
                    WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s AND COLUMN_NAME = %s
                """, (connection_config.database, comment_update.table_name, comment_update.column_name))
                
                field_info = cursor.fetchone()
                if not field_info:
                    raise ValueError(f"Column {comment_update.column_name} not found")
                
                column_type, is_nullable, column_default, extra = field_info
                
                # 构建ALTER语句
                null_constraint = "NULL" if is_nullable == "YES" else "NOT NULL"
                default_constraint = f"DEFAULT {column_default}" if column_default else ""
                extra_constraint = extra if extra else ""
                
                sql = f"""
                    ALTER TABLE `{comment_update.table_name}` 
                    MODIFY COLUMN `{comment_update.column_name}` {column_type} {null_constraint} {default_constraint} {extra_constraint} 
                    COMMENT '{comment_update.comment}'
                """
            else:
                # 更新表注释
                sql = f"""
                    ALTER TABLE `{comment_update.table_name}` 
                    COMMENT = '{comment_update.comment}'
                """
            
            cursor.execute(sql)
            cursor.close()
            conn.close()
            
            return True
            
        except Exception as e:
            logging.error(f"Error updating comment: {e}")
            raise
    
    def execute_custom_sql(self, connection_id: str, sql: str) -> Dict[str, Any]:
        """执行自定义SQL"""
        connection_config = self._get_connection_by_id(connection_id)
        if not connection_config:
            raise ValueError(f"Database connection {connection_id} not found")
        
        try:
            conn = self._create_pymysql_connection(connection_config)
            cursor = conn.cursor(pymysql.cursors.DictCursor)
            
            cursor.execute(sql)
            
            if sql.strip().upper().startswith('SELECT'):
                data = cursor.fetchall()
                row_count = len(data)
            else:
                data = None
                row_count = cursor.rowcount
            
            cursor.close()
            conn.close()
            
            return {
                "success": True,
                "data": data,
                "row_count": row_count
            }
            
        except Exception as e:
            logging.error(f"Error executing SQL: {e}")
            return {
                "success": False,
                "error": str(e),
                "row_count": 0
            }

# 创建全局实例
database_admin_service = DatabaseAdminService()
