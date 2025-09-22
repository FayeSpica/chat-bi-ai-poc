import uuid
from typing import Dict, Optional
from app.models import ChatRequest, ChatResponse, SemanticSQL
from app.semantic_sql_converter import semantic_sql_converter, mysql_sql_generator
from app.database import db_manager

class ChatService:
    """聊天服务"""
    
    def __init__(self):
        self.conversations: Dict[str, list] = {}
    
    def process_chat_message(self, request: ChatRequest) -> ChatResponse:
        """处理聊天消息"""
        try:
            # 获取或创建会话ID
            conversation_id = request.conversation_id or str(uuid.uuid4())
            
            # 初始化会话历史
            if conversation_id not in self.conversations:
                self.conversations[conversation_id] = []
            
            # 添加用户消息到会话历史
            self.conversations[conversation_id].append({
                "role": "user",
                "content": request.message
            })
            
            # 转换自然语言为语义SQL
            semantic_sql = semantic_sql_converter.convert_to_semantic_sql(request.message)
            
            # 生成MySQL SQL语句
            mysql_sql = mysql_sql_generator.generate_mysql_sql(semantic_sql)
            
            # 生成响应消息
            response_message = self._generate_response_message(
                request.message, 
                semantic_sql, 
                mysql_sql
            )
            
            # 添加助手回复到会话历史
            self.conversations[conversation_id].append({
                "role": "assistant",
                "content": response_message,
                "semantic_sql": semantic_sql.dict(),
                "mysql_sql": mysql_sql
            })
            
            return ChatResponse(
                response=response_message,
                sql_query=mysql_sql,
                semantic_sql=semantic_sql,
                conversation_id=conversation_id
            )
            
        except Exception as e:
            error_message = f"处理消息时发生错误: {str(e)}"
            return ChatResponse(
                response=error_message,
                conversation_id=request.conversation_id or str(uuid.uuid4())
            )
    
    def _generate_response_message(
        self, 
        user_message: str, 
        semantic_sql: SemanticSQL, 
        mysql_sql: str
    ) -> str:
        """生成响应消息"""
        
        # 检查是否是查询请求
        if any(keyword in user_message.lower() for keyword in [
            "查询", "显示", "获取", "找出", "统计", "计算", "求和", "平均", "最大", "最小"
        ]):
            
            response = f"我已经将您的查询转换为SQL语句：\n\n"
            response += f"**语义SQL结构：**\n"
            response += f"- 涉及表: {', '.join(semantic_sql.tables) if semantic_sql.tables else '未指定'}\n"
            response += f"- 查询列: {', '.join(semantic_sql.columns) if semantic_sql.columns else '所有列'}\n"
            
            if semantic_sql.conditions:
                response += f"- 筛选条件: {len(semantic_sql.conditions)}个条件\n"
            
            if semantic_sql.aggregations:
                response += f"- 聚合函数: {len(semantic_sql.aggregations)}个\n"
            
            if semantic_sql.joins:
                response += f"- 表连接: {len(semantic_sql.joins)}个\n"
            
            response += f"\n**生成的MySQL SQL：**\n```sql\n{mysql_sql}\n```\n\n"
            response += "您想要执行这个SQL查询吗？"
            
        else:
            response = f"我理解您想要：{user_message}\n\n"
            response += "我已经生成了相应的SQL查询语句。您需要我执行查询并返回结果吗？"
        
        return response
    
    def execute_sql_and_update_response(self, conversation_id: str, sql: str) -> Dict:
        """执行SQL并更新响应"""
        try:
            # 执行SQL查询
            execution_result = db_manager.execute_query(sql)
            
            # 更新会话历史中的最后一条助手消息
            if (conversation_id in self.conversations and 
                self.conversations[conversation_id] and 
                self.conversations[conversation_id][-1]["role"] == "assistant"):
                
                self.conversations[conversation_id][-1]["execution_result"] = execution_result.dict()
            
            return execution_result.dict()
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "row_count": 0
            }
    
    def get_conversation_history(self, conversation_id: str) -> list:
        """获取会话历史"""
        return self.conversations.get(conversation_id, [])
    
    def clear_conversation(self, conversation_id: str):
        """清除会话历史"""
        if conversation_id in self.conversations:
            del self.conversations[conversation_id]

# 创建全局实例
chat_service = ChatService()
