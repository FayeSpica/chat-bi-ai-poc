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
