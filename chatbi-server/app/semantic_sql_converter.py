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
            temperature=0.1,
            request_timeout=120  # 设置Ollama请求超时为120秒
        )
        # 保存最近一次与Ollama交互的调试信息
        self.last_debug: Optional[Dict[str, Any]] = None
        
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
            # 保存调试信息
            self.last_debug = {
                "provider": "ollama",
                "base_url": settings.OLLAMA_BASE_URL,
                "model": settings.OLLAMA_MODEL,
                "prompt": prompt,
                "raw_response": response
            }
            
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
            # 记录失败调试信息
            self.last_debug = {
                "provider": "ollama",
                "base_url": settings.OLLAMA_BASE_URL,
                "model": settings.OLLAMA_MODEL,
                "error": str(e)
            }
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
            def infer_table_meaning(name: str) -> str:
                n = name.lower()
                if "user" in n:
                    return "用户相关数据"
                if "order" in n:
                    return "订单/交易相关数据"
                if "product" in n or "item" in n:
                    return "商品/物品相关数据"
                if "log" in n or "event" in n:
                    return "日志/事件记录"
                return "业务相关数据表"

            def infer_column_meaning(name: str) -> str:
                n = name.lower()
                if n == "id" or n.endswith("_id"):
                    return "主键/外键标识"
                if "name" in n:
                    return "名称/标题"
                if "email" in n:
                    return "电子邮箱"
                if "city" in n or "address" in n:
                    return "城市/地址"
                if "amount" in n or "total" in n or "price" in n or "cost" in n:
                    return "金额/数值"
                if "qty" in n or "quantity" in n or "count" in n:
                    return "数量"
                if "date" in n or "time" in n or n.endswith("_at"):
                    return "日期/时间"
                if "status" in n or "state" in n:
                    return "状态"
                if "category" in n or "type" in n:
                    return "类别/类型"
                return "字段含义未注明"

            lines = []
            tables = metadata.get("tables", {})
            for table_name, t in tables.items():
                comment = (t.get("comment") or "").strip()
                if not comment:
                    comment = infer_table_meaning(table_name)
                if len(comment) > 60:
                    comment = comment[:57] + "..."
                lines.append(f"- 表 {table_name}: {comment}")

                cols = t.get("columns", [])
                col_lines = []
                for c in cols[:12]:  # 限制每表输出的字段数量
                    cname = c.get("name")
                    ctype = c.get("type")
                    ccomment = (c.get("comment") or "").strip()
                    if not ccomment:
                        ccomment = infer_column_meaning(cname or "")
                    if len(ccomment) > 40:
                        ccomment = ccomment[:37] + "..."
                    # 取样例值（如有）
                    samples = c.get("samples") or []
                    sample_part = ""
                    if samples:
                        preview = ", ".join(str(v) for v in samples[:2])
                        sample_part = f"，样例: {preview}"
                    col_lines.append(f"{cname}({ctype}): {ccomment}{sample_part}")
                if col_lines:
                    lines.append("  字段: " + "; ".join(col_lines))

                # 表级样例行展示一条
                samples = t.get("samples", [])
                if samples:
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
        # 如果有JOIN，只保留第一个表在FROM中，其他表在JOIN中处理
        if semantic_sql.joins:
            return semantic_sql.tables[0] if semantic_sql.tables else ""
        else:
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
                # 如果table1是FROM子句中的第一个表，直接JOIN table2
                if table1 == semantic_sql.tables[0]:
                    join_clauses.append(f"{join_type} JOIN {table2} ON {condition}")
                else:
                    # 否则需要先JOIN table1，再JOIN table2
                    join_clauses.append(f"{join_type} JOIN {table1} ON {condition}")
        
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
