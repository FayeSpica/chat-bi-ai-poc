from typing import Dict, Any, List, Optional
from app.database import db_manager
from app.config import settings
from app.models import DatabaseConnection


class SchemaMetadataBuilder:
    """构建数据库表/字段的元数据，包含含义、描述、样例值等。"""

    def __init__(self, sample_rows_per_table: int = 5):
        self.sample_rows_per_table = sample_rows_per_table

    def build_database_metadata(self, database_connection: Optional[DatabaseConnection] = None) -> Dict[str, Any]:
        """返回完整数据库元数据结构。
        结构:
        {
          "db": {"host":..., "name":...},
          "tables": {
             "table_name": {
                "comment": str | None,
                "columns": [{"name":..., "type":..., "null":..., "key":..., "default":..., "extra":..., "comment":..., "samples": [v1, v2, ...]}],
                "samples": [ {col: val, ...}, ... ]
             },
             ...
          }
        }
        """
        tables = db_manager.get_all_tables(database_connection)
        
        # 使用传入的数据库连接信息，如果没有则使用默认配置
        if database_connection:
            db_info = {
                "host": database_connection.host,
                "name": database_connection.database,
            }
        else:
            db_info = {
                "host": settings.DB_HOST,
                "name": settings.DB_NAME,
            }
        
        metadata: Dict[str, Any] = {
            "db": db_info,
            "tables": {}
        }

        for table in tables:
            columns = self._get_columns_with_comments(table, database_connection)
            sample_rows = self._get_sample_rows(table, self.sample_rows_per_table, database_connection)

            # 基于样例行，为每个字段提取样例值（去重，保留最多5个）
            column_samples_map: Dict[str, List[Any]] = {c["name"]: [] for c in columns}
            for row in sample_rows:
                for col in column_samples_map.keys():
                    if col in row:
                        val = row[col]
                        samples = column_samples_map[col]
                        if val not in samples:
                            samples.append(val)
                            if len(samples) > 5:
                                samples.pop(0)

            # 将样例值合入列结构
            for c in columns:
                c["samples"] = column_samples_map.get(c["name"], [])

            table_meta: Dict[str, Any] = {
                "comment": self._get_table_comment(table, database_connection),
                "columns": columns,
                "samples": sample_rows
            }
            metadata["tables"][table] = table_meta

        return metadata

    def _get_table_comment(self, table_name: str, database_connection: Optional[DatabaseConnection] = None) -> str:
        try:
            sql = (
                "SELECT table_comment FROM information_schema.tables "
                "WHERE table_schema=%s AND table_name=%s"
            )
            conn = db_manager.get_connection(database_connection)
            cur = conn.cursor()
            
            # 使用正确的数据库名称
            db_name = database_connection.database if database_connection else settings.DB_NAME
            cur.execute(sql, (db_name, table_name))
            row = cur.fetchone()
            cur.close()
            if row and row[0]:
                return str(row[0])
        except Exception:
            pass
        return ""

    def _get_columns_with_comments(self, table_name: str, database_connection: Optional[DatabaseConnection] = None) -> List[Dict[str, Any]]:
        try:
            sql = (
                "SELECT column_name, column_type, is_nullable, column_key, column_default, extra, column_comment "
                "FROM information_schema.columns WHERE table_schema=%s AND table_name=%s ORDER BY ordinal_position"
            )
            conn = db_manager.get_connection(database_connection)
            cur = conn.cursor()
            
            # 使用正确的数据库名称
            db_name = database_connection.database if database_connection else settings.DB_NAME
            cur.execute(sql, (db_name, table_name))
            rows = cur.fetchall()
            cur.close()
            columns: List[Dict[str, Any]] = []
            for r in rows:
                # 返回为元组顺序如上
                columns.append({
                    "name": r[0],
                    "type": r[1],
                    "nullable": (str(r[2]).upper() == "YES"),
                    "key": r[3],
                    "default": r[4],
                    "extra": r[5],
                    "comment": r[6] or ""
                })
            return columns
        except Exception:
            return []

    def _get_sample_rows(self, table_name: str, limit: int, database_connection: Optional[DatabaseConnection] = None) -> List[Dict[str, Any]]:
        try:
            sql = f"SELECT * FROM {table_name} LIMIT {limit}"
            result = db_manager.execute_query(sql, database_connection)
            if result.success and result.data:
                return result.data
        except Exception:
            pass
        return []


# 全局实例
schema_metadata_builder = SchemaMetadataBuilder()


