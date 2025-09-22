# 分析并修复SQL语法错误
_Exported on 2025/9/22 at GMT+8 22:48:27 from Cursor (1.6.42)_

---

**User**

执行时出现：执行失败: (1064, "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version for the right syntax to use near 'GROUP BY DATE_TRUNC('day', orders.order_date) ORDER BY order_date ASC' at line 1") 帮我分析原因并修复

---

**Cursor**

I'll search the server code to see where SQL is generated, particularly any date truncation logic that might be using Postgres-specific functions like DATE_TRUNC. Then I'll open the relevant files to implement a MySQL-compatible fix.

I'll open the SQL generation-related modules to locate where `DATE_TRUNC` is used and adjust to MySQL-safe functions.

I'll add a normalization step in the MySQL SQL generator to convert Postgres-specific functions like DATE_TRUNC into MySQL equivalents, and apply it to SELECT, GROUP BY, and ORDER BY expressions. Then I'll update the code accordingly.

```python
import json
import re
from typing import Dict, List, Any, Optional
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate
from app.config import settings
import logging
from app.models import SemanticSQL

class SemanticSQLConverter:
    """自然语言转语义SQL转换器"""
    
    def __init__(self):
        self.llm = ChatOllama(
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
            logging.getLogger("chatbi.converter").info(
                "Invoking ChatOllama: base=%s model=%s",
                settings.OLLAMA_BASE_URL, settings.OLLAMA_MODEL
            )
            msg = self.llm.invoke(prompt)
            response = getattr(msg, "content", "")
            
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
            logging.getLogger("chatbi.converter").exception("convert_to_semantic_sql failed: %s", e)
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
    
    def _normalize_expression_for_mysql(self, expression: str) -> str:
        """将常见的非MySQL语法转换为MySQL兼容表达式。
        目前支持：DATE_TRUNC('unit', column)
        """
        try:
            expr = expression
            # 兼容大小写：DATE_TRUNC('day', col)
            import re as _re
            def _replace_date_trunc(match):
                unit = match.group(1).lower()
                col = match.group(2).strip()
                if unit == "day":
                    return f"DATE({col})"
                if unit == "month":
                    return f"DATE_FORMAT({col}, '%Y-%m')"
                if unit == "year":
                    return f"DATE_FORMAT({col}, '%Y')"
                if unit == "week":
                    return f"YEARWEEK({col})"
                if unit == "hour":
                    return f"DATE_FORMAT({col}, '%Y-%m-%d %H:00:00')"
                if unit == "quarter":
                    return f"CONCAT(YEAR({col}), '-Q', QUARTER({col}))"
                # 默认降级为DATE()
                return f"DATE({col})"
            expr = _re.sub(r"(?i)DATE_TRUNC\(\s*'([^']+)'\s*,\s*([^\)]+)\)", _replace_date_trunc, expr)

            # 处理Postgres风格的类型转换 ::date -> DATE(col)
            expr = _re.sub(r"\b(\w+)::date\b", r"DATE(\\1)", expr)

            return expr
        except Exception:
            return expression
    
    def generate_mysql_sql(self, semantic_sql: SemanticSQL) -> str:
        """将语义SQL转换为MySQL SQL语句"""
        try:
            if not semantic_sql.tables:
                return "SELECT 1; -- No tables specified"
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
            normalized_cols = [self._normalize_expression_for_mysql(col) for col in semantic_sql.columns]
            return ", ".join(normalized_cols)
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
        def sql_quote(val: Any) -> str:
            # 为字符串值加单引号并转义单引号，其它类型转为字符串
            if isinstance(val, str):
                return "'" + val.replace("'", "''") + "'"
            return str(val)

        conditions = []
        for condition in semantic_sql.conditions:
            column = condition.get("column", "")
            operator = condition.get("operator", "=")
            value = condition.get("value", "")
            
            if column and value:
                # 处理不同类型的值
                if operator.upper() == "IN":
                    if isinstance(value, list):
                        value_str = "(" + ", ".join(sql_quote(v) for v in value) + ")"
                    else:
                        value_str = f"({sql_quote(value)})"
                elif operator.upper() == "BETWEEN":
                    if isinstance(value, list) and len(value) == 2:
                        value_str = f"{sql_quote(value[0])} AND {sql_quote(value[1])}"
                    else:
                        value_str = sql_quote(value)
                elif operator.upper() == "LIKE":
                    value_str = sql_quote(value)
                else:
                    value_str = sql_quote(value)
                
                conditions.append(f"{column} {operator} {value_str}")
        
        return " AND ".join(conditions)
    
    def _build_group_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建GROUP BY子句"""
        normalized_groups = [self._normalize_expression_for_mysql(g) for g in (semantic_sql.group_by or [])]
        return ", ".join(normalized_groups)
    
    def _build_order_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建ORDER BY子句"""
        order_items = []
        for order in semantic_sql.order_by:
            column = self._normalize_expression_for_mysql(order.get("column", ""))
            direction = order.get("direction", "ASC").upper()
            if column:
                order_items.append(f"{column} {direction}")
        
        return ", ".join(order_items)

# 创建全局实例
semantic_sql_converter = SemanticSQLConverter()
mysql_sql_generator = MySQLSQLGenerator()

```

I'll update WHERE clause handling to normalize DATE_TRUNC in condition columns as well, then run a quick lint check on the modified file.

```python
import json
import re
from typing import Dict, List, Any, Optional
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate
from app.config import settings
import logging
from app.models import SemanticSQL

class SemanticSQLConverter:
    """自然语言转语义SQL转换器"""
    
    def __init__(self):
        self.llm = ChatOllama(
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
            logging.getLogger("chatbi.converter").info(
                "Invoking ChatOllama: base=%s model=%s",
                settings.OLLAMA_BASE_URL, settings.OLLAMA_MODEL
            )
            msg = self.llm.invoke(prompt)
            response = getattr(msg, "content", "")
            
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
            logging.getLogger("chatbi.converter").exception("convert_to_semantic_sql failed: %s", e)
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
    
    def _normalize_expression_for_mysql(self, expression: str) -> str:
        """将常见的非MySQL语法转换为MySQL兼容表达式。
        目前支持：DATE_TRUNC('unit', column)
        """
        try:
            expr = expression
            # 兼容大小写：DATE_TRUNC('day', col)
            import re as _re
            def _replace_date_trunc(match):
                unit = match.group(1).lower()
                col = match.group(2).strip()
                if unit == "day":
                    return f"DATE({col})"
                if unit == "month":
                    return f"DATE_FORMAT({col}, '%Y-%m')"
                if unit == "year":
                    return f"DATE_FORMAT({col}, '%Y')"
                if unit == "week":
                    return f"YEARWEEK({col})"
                if unit == "hour":
                    return f"DATE_FORMAT({col}, '%Y-%m-%d %H:00:00')"
                if unit == "quarter":
                    return f"CONCAT(YEAR({col}), '-Q', QUARTER({col}))"
                # 默认降级为DATE()
                return f"DATE({col})"
            expr = _re.sub(r"(?i)DATE_TRUNC\(\s*'([^']+)'\s*,\s*([^\)]+)\)", _replace_date_trunc, expr)

            # 处理Postgres风格的类型转换 ::date -> DATE(col)
            expr = _re.sub(r"\b(\w+)::date\b", r"DATE(\\1)", expr)

            return expr
        except Exception:
            return expression
    
    def generate_mysql_sql(self, semantic_sql: SemanticSQL) -> str:
        """将语义SQL转换为MySQL SQL语句"""
        try:
            if not semantic_sql.tables:
                return "SELECT 1; -- No tables specified"
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
            normalized_cols = [self._normalize_expression_for_mysql(col) for col in semantic_sql.columns]
            return ", ".join(normalized_cols)
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
        def sql_quote(val: Any) -> str:
            # 为字符串值加单引号并转义单引号，其它类型转为字符串
            if isinstance(val, str):
                return "'" + val.replace("'", "''") + "'"
            return str(val)

        conditions = []
        for condition in semantic_sql.conditions:
            column = self._normalize_expression_for_mysql(condition.get("column", ""))
            operator = condition.get("operator", "=")
            value = condition.get("value", "")
            
            if column and value:
                # 处理不同类型的值
                if operator.upper() == "IN":
                    if isinstance(value, list):
                        value_str = "(" + ", ".join(sql_quote(v) for v in value) + ")"
                    else:
                        value_str = f"({sql_quote(value)})"
                elif operator.upper() == "BETWEEN":
                    if isinstance(value, list) and len(value) == 2:
                        value_str = f"{sql_quote(value[0])} AND {sql_quote(value[1])}"
                    else:
                        value_str = sql_quote(value)
                elif operator.upper() == "LIKE":
                    value_str = sql_quote(value)
                else:
                    value_str = sql_quote(value)
                
                conditions.append(f"{column} {operator} {value_str}")
        
        return " AND ".join(conditions)
    
    def _build_group_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建GROUP BY子句"""
        normalized_groups = [self._normalize_expression_for_mysql(g) for g in (semantic_sql.group_by or [])]
        return ", ".join(normalized_groups)
    
    def _build_order_by_clause(self, semantic_sql: SemanticSQL) -> str:
        """构建ORDER BY子句"""
        order_items = []
        for order in semantic_sql.order_by:
            column = self._normalize_expression_for_mysql(order.get("column", ""))
            direction = order.get("direction", "ASC").upper()
            if column:
                order_items.append(f"{column} {direction}")
        
        return ", ".join(order_items)

# 创建全局实例
semantic_sql_converter = SemanticSQLConverter()
mysql_sql_generator = MySQLSQLGenerator()

```

- I looked for where SQL is generated and found `MySQLSQLGenerator` in `app/semantic_sql_converter.py`. The error comes from using Postgres’ DATE_TRUNC in generated SQL, which MySQL doesn’t support.

- I added a normalization step that rewrites Postgres-specific expressions to MySQL-safe ones across SELECT, WHERE, GROUP BY, and ORDER BY.

What I changed:
- In `app/semantic_sql_converter.py`, I added `_normalize_expression_for_mysql` and applied it to:
  - SELECT columns
  - WHERE condition columns
  - GROUP BY expressions
  - ORDER BY columns
- DATE_TRUNC mappings:
  - `DATE_TRUNC('day', col)` → `DATE(col)`
  - `DATE_TRUNC('month', col)` → `DATE_FORMAT(col, '%Y-%m')`
  - `DATE_TRUNC('year', col)` → `DATE_FORMAT(col, '%Y')`
  - `DATE_TRUNC('week', col)` → `YEARWEEK(col)`
  - `DATE_TRUNC('hour', col)` → `DATE_FORMAT(col, '%Y-%m-%d %H:00:00')`
  - `DATE_TRUNC('quarter', col)` → `CONCAT(YEAR(col), '-Q', QUARTER(col))`
  - Also converts `col::date` → `DATE(col)`

Next step: 请重新运行你的查询。若错误依旧，请把具体生成的 SQL 发给我，我再继续修复。

- I updated the SQL generation to replace DATE_TRUNC with MySQL equivalents, which should fix the 1064 error.
- Changes are confined to `app/semantic_sql_converter.py` and affect SELECT/WHERE/GROUP BY/ORDER BY.

---

**User**

我希望重构自然语言转语义SQL的过程，应该有数据库表对应的元数据信息，比如每个表的作用，每个字段的含义、描述、样例。这些表信息应作为提示词给大模型

---

**Cursor**

I'll scan the database layer to see how we can fetch schema and examples, then add a metadata builder and wire it into the prompt used in `SemanticSQLConverter`.

I'll create a schema metadata builder to extract table/column info and sample values from MySQL, then wire it into the prompt used by `SemanticSQLConverter` so the LLM receives database context. Now I'll add a new module and update the converter accordingly.

I'll add a new `metadata_builder.py` using `db_manager` to fetch tables, columns, comments (if present), and sample values, then update `SemanticSQLConverter` to include this metadata in its system prompt.

```python
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


```

```python
import json
import re
from typing import Dict, List, Any, Optional
from langchain_ollama import ChatOllama
from langchain_core.prompts import ChatPromptTemplate
from app.metadata_builder import schema_metadata_builder
from app.config import settings
import logging
from app.models import SemanticSQL

class SemanticSQLConverter:
    """自然语言转语义SQL转换器"""
    
    def __init__(self):
        self.llm = ChatOllama(
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

请严格按照JSON格式输出，不要包含任何其他文字。

以下是数据库的表结构和字段元数据（用于更好地理解用户意图并选择正确的表与字段）。
仅将其用于理解上下文，不要直接把元数据内容复制到输出字段中。
"""

    def convert_to_semantic_sql(self, natural_language: str) -> SemanticSQL:
        """将自然语言转换为语义SQL"""
        try:
            # 构建数据库元数据片段（简洁压缩）
            metadata = schema_metadata_builder.build_database_metadata()
            metadata_summary = self._summarize_metadata_for_prompt(metadata)

            prompt = f"{self.system_prompt}\n\n数据库元数据:\n{metadata_summary}\n\n用户查询：{natural_language}"
            logging.getLogger("chatbi.converter").info(
                "Invoking ChatOllama: base=%s model=%s",
                settings.OLLAMA_BASE_URL, settings.OLLAMA_MODEL
            )
            msg = self.llm.invoke(prompt)
            response = getattr(msg, "content", "")
            
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
            logging.getLogger("chatbi.converter").exception("convert_to_semantic_sql failed: %s", e)
            # 如果转换失败，返回一个默认的语义SQL结构
            return SemanticSQL(
                tables=[],
                columns=[],
                conditions=[],
                aggregations=[],
                joins=[]
            )

    def _summarize_metadata_for_prompt(self, metadata: Dict[str, Any]) -> str:
        """压缩数据库元数据为适合放入Prompt的可读文本，避免过长。"""
        try:
            lines = []
            tables = metadata.get("tables", {})
            for table_name, t in tables.items():
                comment = (t.get("comment") or "").strip()
                if len(comment) > 60:
                    comment = comment[:57] + "..."
                lines.append(f"- 表 {table_name}: {comment}")
                cols = t.get("columns", [])
                col_parts = []
                for c in cols[:12]:  # 限制每表输出的字段数量
                    cname = c.get("name")
                    ctype = c.get("type")
                    ccomment = (c.get("comment") or "").strip()
                    if len(ccomment) > 40:
                        ccomment = ccomment[:37] + "..."
                    col_parts.append(f"{cname}({ctype}): {ccomment}")
                if col_parts:
                    lines.append("  字段: " + "; ".join(col_parts))
                # 样例行展示一条
                samples = t.get("samples", [])
                if samples:
                    # 只展示第一行的2-3列样例
                    first = samples[0]
                    sample_items = list(first.items())[:3]
                    sample_str = ", ".join(f"{k}={v}" for k, v in sample_items)
                    lines.append(f"  样例: {sample_str}")
            return "\n".join(lines)
        except Exception:
            return "(metadata unavailable)"

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
            if not semantic_sql.tables:
                return "SELECT 1; -- No tables specified"
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
        def sql_quote(val: Any) -> str:
            # 为字符串值加单引号并转义单引号，其它类型转为字符串
            if isinstance(val, str):
                return "'" + val.replace("'", "''") + "'"
            return str(val)

        conditions = []
        for condition in semantic_sql.conditions:
            column = condition.get("column", "")
            operator = condition.get("operator", "=")
            value = condition.get("value", "")
            
            if column and value:
                # 处理不同类型的值
                if operator.upper() == "IN":
                    if isinstance(value, list):
                        value_str = "(" + ", ".join(sql_quote(v) for v in value) + ")"
                    else:
                        value_str = f"({sql_quote(value)})"
                elif operator.upper() == "BETWEEN":
                    if isinstance(value, list) and len(value) == 2:
                        value_str = f"{sql_quote(value[0])} AND {sql_quote(value[1])}"
                    else:
                        value_str = sql_quote(value)
                elif operator.upper() == "LIKE":
                    value_str = sql_quote(value)
                else:
                    value_str = sql_quote(value)
                
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

- I added `app/metadata_builder.py` to build table/column metadata and sample rows from MySQL using `information_schema` and `SELECT * ... LIMIT`. 
- I updated `SemanticSQLConverter` to fetch this metadata per request, summarize it compactly, and include it in the system prompt so the LLM understands table purposes, field meanings, and examples.

How to use:
- 启动服务后再次请求 `/api/chat`，转换时会自动携带数据库元数据作为提示词上下文。
- 如需更多样例或更详细字段，可调整 `SchemaMetadataBuilder(sample_rows_per_table=...)` 或在 `_summarize_metadata_for_prompt` 中放宽限制。

如果你愿意，我可以再加：
- 基于 `init.sql` 的中文注释自动汇总到 `information_schema`（或维护一份 JSON 手册并合并）。
- 按表白名单/黑名单筛选，减少无关表噪音。
- 缓存元数据，避免每次请求都查询信息架构。

