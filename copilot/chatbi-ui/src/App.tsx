import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Alert, Button } from 'antd';
import { ReloadOutlined, DatabaseOutlined, SettingOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSelector from './components/DatabaseSelector';
import { chatAPI, systemAPI, databaseAdminAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, DatabaseConnection } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>(uuidv4());
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  const [systemError, setSystemError] = useState<string | null>(null);
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [refreshDatabaseSchema, setRefreshDatabaseSchema] = useState(0);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [availableConnections, setAvailableConnections] = useState<DatabaseConnection[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      setSystemError(null);
    } catch (error: any) {
      setSystemStatus('error');
      setSystemError(error.message || '系统连接失败');
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      setAvailableConnections(connections);
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        setSelectedDatabaseId(activeConnection.id);
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 触发数据库结构刷新
    setRefreshDatabaseSchema(prev => prev + 1);
  };

  useEffect(() => {
    checkSystemStatus();
    loadDatabaseConnections();
    
    // 添加欢迎消息
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: `欢迎使用ChatBI智能聊天系统！

我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。

例如：
- [查询所有用户的订单总金额](#query)
- [统计每个月的销售数量](#query)
- [找出购买最多的前10个商品](#query)`,
      timestamp: new Date()
    };
    setMessages([welcomeMessage]);
  }, []);

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (content: string) => {
    if (systemStatus !== 'healthy') {
      message.error('系统连接异常，请检查后端服务');
      return;
    }

    // 添加用户消息
    const userMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'user',
      content,
      timestamp: new Date()
    };

    setMessages((prev: ChatMessageType[]) => [...prev, userMessage]);
    setIsLoading(true);

    try {
      const requestPayload: ChatRequest = {
        message: content,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 添加助手回复
      const assistantMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: response.response,
        semantic_sql: response.semantic_sql,
        sql_query: response.sql_query,
        timestamp: new Date(),
        debug_info: {
          request: requestPayload,
          response,
          ollama: response.debug_ollama,
          sql_execution: null
        }
      };

      setMessages((prev: ChatMessageType[]) => [...prev, assistantMessage]);

      // 自动执行生成的 SQL 查询（同时保留手动执行按钮）
      if (response.sql_query) {
        handleExecuteSQL(response.sql_query);
      }

    } catch (error: any) {
      message.error('发送消息失败: ' + (error.message || '未知错误'));
      
      const errorMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '抱歉，处理您的请求时发生了错误。请稍后重试。',
        timestamp: new Date()
      };
      setMessages((prev: ChatMessageType[]) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleExecuteSQL = async (sql: string) => {
    setIsExecuting(true);
    try {
      const result = await chatAPI.executeSQL({
        sql_query: sql,
        conversation_id: conversationId,
        database_connection_id: selectedDatabaseId
      });

      // 更新最后一条助手消息的执行结果
      setMessages((prev: ChatMessageType[]) => {
        const newMessages = [...prev];
        const lastMessage = newMessages[newMessages.length - 1];
        if (lastMessage.role === 'assistant') {
          lastMessage.execution_result = result;
          lastMessage.debug_info = {
            ...(lastMessage.debug_info || {}),
            sql_execution: result
          };
        }
        return newMessages;
      });

      if (result.success) {
        message.success(`查询成功，返回 ${result.row_count || 0} 条记录`);
      } else {
        message.error('查询失败: ' + result.error);
      }

    } catch (error: any) {
      message.error('执行SQL失败: ' + (error.message || '未知错误'));
    } finally {
      setIsExecuting(false);
    }
  };

  const handleClearChat = async () => {
    try {
      await chatAPI.clearConversation(conversationId);
      setMessages([] as ChatMessageType[]);
      setConversationId(uuidv4());
      
      // 重新添加欢迎消息
      const welcomeMessage: ChatMessageType = {
        id: uuidv4(),
        role: 'assistant',
        content: '对话已清空。我可以帮您将自然语言转换为SQL查询语句，请告诉我您想要查询什么数据。',
        timestamp: new Date()
      };
      setMessages([welcomeMessage]);
      
      message.success('对话已清空');
    } catch (error: any) {
      message.error('清空对话失败: ' + (error.message || '未知错误'));
    }
  };

  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句`;
    handleSendMessage(suggestionMessage);
  };

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    // 触发数据库结构刷新
    setRefreshDatabaseSchema(prev => prev + 1);
  };

  const renderSystemStatus = () => {
    switch (systemStatus) {
      case 'checking':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Spin size="small" />
            <span style={{ fontSize: '12px', color: '#666' }}>检查中...</span>
          </div>
        );
      case 'healthy':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#52c41a' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#52c41a' }}>系统正常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      case 'error':
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <div style={{ 
              width: '8px', 
              height: '8px', 
              borderRadius: '50%', 
              backgroundColor: '#ff4d4f' 
            }}></div>
            <span style={{ fontSize: '12px', color: '#ff4d4f' }}>连接异常</span>
            <Button 
              size="small" 
              type="text" 
              icon={<ReloadOutlined />} 
              onClick={checkSystemStatus}
              style={{ padding: '0 4px', minWidth: 'auto' }}
            />
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <Layout className="chat-container">
      <Header style={{ 
        background: '#fff', 
        borderBottom: '1px solid #f0f0f0',
        padding: '0 16px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ fontSize: '18px', fontWeight: 'bold', color: '#1890ff' }}>
          ChatBI - 智能聊天BI系统
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            type="primary" 
            icon={<SettingOutlined />}
            onClick={() => setShowDatabaseAdmin(true)}
          >
            数据库管理
          </Button>
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider width={400} style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <DatabaseSchema 
            key={refreshDatabaseSchema}
            onSelectTable={handleSelectTable}
            selectedDatabaseId={selectedDatabaseId}
          />
        </Sider>

        <Layout>
          <Content style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            <div className="chat-messages" style={{ flex: 1 }}>
              {messages.map((msg) => (
                <ChatMessage
                  key={msg.id}
                  message={msg}
                  onExecuteSQL={handleExecuteSQL}
                  onSendMessage={handleSendMessage}
                  isExecuting={isExecuting}
                />
              ))}
              
              {isLoading && (
                <div className="message-item message-assistant">
                  <div className="message-content">
                    <div className="loading-dots">正在思考中</div>
                  </div>
                </div>
              )}
              
              <div ref={messagesEndRef} />
            </div>

            <ChatInput
              onSendMessage={handleSendMessage}
              onClearChat={handleClearChat}
              disabled={isLoading || systemStatus !== 'healthy'}
            />
          </Content>
        </Layout>
      </Layout>

      {/* 数据库管理弹窗 */}
      {showDatabaseAdmin && (
        <DatabaseAdmin onClose={handleDatabaseAdminClose} />
      )}
    </Layout>
  );
};

export default App;
