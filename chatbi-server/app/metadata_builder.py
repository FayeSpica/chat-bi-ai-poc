from typing import Dict, Any, List
from app.database import db_manager
from app.config import settings


class SchemaMetadataBuilder:
    """构建数据库表/字段的元数据，包含含义、描述、样例值等。"""

    def __init__(self, sample_rows_per_table: int = 5):
        self.sample_rows_per_table = sample_rows_per_table

    def build_database_metadata(self) -> Dict[str, Any]:
        """返回完整数据库元数据结构。
        结构:
        {
          "db": {"host":..., "name":...},
          "tables": {
             "table_name": {
                "comment": str | None,
                "columns": [{"name":..., "type":..., "null":..., "key":..., "default":..., "extra":..., "comment":...}],
                "samples": [ {col: val, ...}, ... ]
             },
             ...
          }
        }
        """
        tables = db_manager.get_all_tables()
        metadata: Dict[str, Any] = {
            "db": {
                "host": settings.DB_HOST,
                "name": settings.DB_NAME,
            },
            "tables": {}
        }

        for table in tables:
            table_meta: Dict[str, Any] = {
                "comment": self._get_table_comment(table),
                "columns": self._get_columns_with_comments(table),
                "samples": self._get_sample_rows(table, self.sample_rows_per_table)
            }
            metadata["tables"][table] = table_meta

        return metadata

    def _get_table_comment(self, table_name: str) -> str:
        try:
            sql = (
                "SELECT table_comment FROM information_schema.tables "
                "WHERE table_schema=%s AND table_name=%s"
            )
            conn = db_manager.get_connection()
            cur = conn.cursor()
            cur.execute(sql, (settings.DB_NAME, table_name))
            row = cur.fetchone()
            cur.close()
            if row and row[0]:
                return str(row[0])
        except Exception:
            pass
        return ""

    def _get_columns_with_comments(self, table_name: str) -> List[Dict[str, Any]]:
        try:
            sql = (
                "SELECT column_name, column_type, is_nullable, column_key, column_default, extra, column_comment "
                "FROM information_schema.columns WHERE table_schema=%s AND table_name=%s ORDER BY ordinal_position"
            )
            conn = db_manager.get_connection()
            cur = conn.cursor()
            cur.execute(sql, (settings.DB_NAME, table_name))
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

    def _get_sample_rows(self, table_name: str, limit: int) -> List[Dict[str, Any]]:
        try:
            sql = f"SELECT * FROM {table_name} LIMIT {limit}"
            result = db_manager.execute_query(sql)
            if result.success and result.data:
                return result.data
        except Exception:
            pass
        return []


# 全局实例
schema_metadata_builder = SchemaMetadataBuilder()


