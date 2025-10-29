import React, { useState, useEffect, useRef } from 'react';
import { Layout, message, Spin, Button, Drawer } from 'antd';
import { ReloadOutlined, SettingOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import ChatMessage from './components/ChatMessage';
import ChatInput from './components/ChatInput';
import DatabaseAdmin from './components/DatabaseAdmin';
import DatabaseSchema from './components/DatabaseSchema';
import DatabaseSelector from './components/DatabaseSelector';
import SessionList from './components/SessionList';
import AccessDenied from './components/AccessDenied';
import { chatAPI, systemAPI, databaseAdminAPI, setAuthErrorHandler, sessionAPI } from './services/api';
import { ChatMessage as ChatMessageType, ChatRequest, ChatResponse, PersistedChatMessage } from './types';
import { v4 as uuidv4 } from 'uuid';

const { Header, Content, Sider } = Layout;

const App: React.FC = () => {
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [conversationId, setConversationId] = useState<string>('');
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isExecuting, setIsExecuting] = useState(false);
  const [systemStatus, setSystemStatus] = useState<'checking' | 'healthy' | 'error'>('checking');
  
  const [showDatabaseAdmin, setShowDatabaseAdmin] = useState(false);
  const [selectedDatabaseId, setSelectedDatabaseId] = useState<string | undefined>();
  const [sidebarCollapsed, setSidebarCollapsed] = useState<boolean>(true); // 默认折叠
  const [accessDenied, setAccessDenied] = useState<{ show: boolean; status: 401 | 403 }>({ show: false, status: 403 });
  const [userPermissions, setUserPermissions] = useState<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }>({
    hasDatabaseAccess: false,
    canDeleteDatabase: false,
    role: 'READER'
  });
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [showSchemaDrawer, setShowSchemaDrawer] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);
  const [sessionListRefresh, setSessionListRefresh] = useState(0);

  // 检查系统状态
  const checkSystemStatus = async () => {
    setSystemStatus('checking');
    try {
      await systemAPI.healthCheck();
      setSystemStatus('healthy');
      
    } catch (error: any) {
      setSystemStatus('error');
    }
  };

  // 加载用户权限
  const loadUserPermissions = async () => {
    try {
      const permissions = await systemAPI.getUserPermissions();
      setUserPermissions(permissions);
    } catch (error: any) {
      console.error('加载用户权限失败:', error);
      // 失败时使用默认权限（最严格）
      setUserPermissions({
        hasDatabaseAccess: false,
        canDeleteDatabase: false,
        role: 'READER'
      });
    }
  };

  // 加载数据库连接列表
  const loadDatabaseConnections = async () => {
    try {
      const connections = await databaseAdminAPI.getConnections();
      
      // 设置默认选择的数据库连接（第一个活跃的连接或第一个连接）
      const activeConnection = connections.find(conn => conn.is_active) || connections[0];
      if (activeConnection) {
        if (activeConnection.id) {
          setSelectedDatabaseId(activeConnection.id);
          // 加载该连接的表名
          try {
            const tables = await databaseAdminAPI.getTables(activeConnection.id);
            setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
          } catch {}
        } else {
          setSelectedDatabaseId(undefined);
          setTableNames([]);
        }
      }
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    }
  };

  // 处理数据库选择变化
  const handleDatabaseChange = (connectionId: string) => {
    setSelectedDatabaseId(connectionId);
    // 切换连接时更新表名
    (async () => {
      try {
        const tables = await databaseAdminAPI.getTables(connectionId);
        setTableNames(Array.isArray(tables) ? tables.map(t => t.table_name) : []);
      } catch {
        setTableNames([]);
      }
    })();
  };

  // 设置权限错误处理
  useEffect(() => {
    setAuthErrorHandler((status: 401 | 403) => {
      setAccessDenied({ show: true, status });
    });
  }, []);

  useEffect(() => {
    checkSystemStatus();
    loadUserPermissions();
    loadDatabaseConnections();
    
    // 会话初始化：优先恢复上次会话；否则若有会话则选第一个；若没有则自动创建
    (async () => {
      try {
        const lastSession = localStorage.getItem('chatbi:lastSessionId');
        if (lastSession) {
          const idNum = parseInt(lastSession, 10);
          if (!Number.isNaN(idNum)) {
            await handleSelectSession(idNum);
            return;
          }
        }

        const sessions = await sessionAPI.listSessions();
        if (sessions && sessions.length > 0) {
          await handleSelectSession(sessions[0].id);
          return;
        }

        const created = await sessionAPI.createSession('新的会话');
        await handleSelectSession(created.id);
      } catch (e) {
        // 失败时回退到欢迎消息
        const welcomeMessage: ChatMessageType = {
          id: uuidv4(),
          role: 'assistant',
          content: `欢迎使用ChatBI智能聊天系统！\n\n我可以帮您将自然语言转换为SQL查询语句。请告诉我您想要查询什么数据。`,
          timestamp: new Date()
        };
        setMessages([welcomeMessage]);
      }
    })();
  }, []);
  const loadSessionMessages = async (sessionId: number) => {
    const list: PersistedChatMessage[] = await sessionAPI.getSessionMessages(sessionId);
    const mapped: ChatMessageType[] = list.map(m => {
      let parsedSemantic: any = m.semanticSql;
      if (typeof parsedSemantic === 'string') {
        try { parsedSemantic = JSON.parse(parsedSemantic); } catch {}
      }
      let parsedExec: any = m.executionResult;
      if (typeof parsedExec === 'string') {
        try { parsedExec = JSON.parse(parsedExec); } catch {}
      }
      let parsedDebug: any = m.debugInfo;
      if (typeof parsedDebug === 'string') {
        try { parsedDebug = JSON.parse(parsedDebug); } catch {}
      }
      // 兼容历史存储：如果 debugInfo 直接是 ollama 字段集合，则包一层 { ollama: ... }
      let normalizedDebug: any = parsedDebug;
      if (parsedDebug && typeof parsedDebug === 'object' && !('ollama' in parsedDebug)) {
        const possibleOllamaKeys = ['provider','model','base_url','prompt','raw_response','error'];
        const hasOllamaShape = possibleOllamaKeys.some(k => k in parsedDebug);
        if (hasOllamaShape) {
          normalizedDebug = { ollama: parsedDebug };
        }
      }
      const debug_info = normalizedDebug || (parsedExec ? { sql_execution: parsedExec } : undefined);
      return {
        id: String(m.id),
        role: m.role as 'user' | 'assistant',
        content: m.content,
        timestamp: new Date(m.createdAt),
        semantic_sql: parsedSemantic,
        sql_query: m.sqlQuery || undefined,
        execution_result: parsedExec,
        debug_info,
      } as ChatMessageType;
    });
    // 基于当前表名生成联动示例
    const examples: string[] = (() => {
      if (!tableNames || tableNames.length === 0) {
        return [
          '- [查询最近10条订单](#query)',
          '- [统计每个城市的用户数量](#query)',
          '- [查看任意表的前10行数据](#query)',
        ];
      }
      const picks = tableNames.slice(0, 3);
      return picks.map(name => `- [查看 ${name} 表的前10行数据](#query)`);
    })();
    const welcomeMessage: ChatMessageType = {
      id: uuidv4(),
      role: 'assistant',
      content: [
        '欢迎使用ChatBI智能聊天系统！',
        '',
        '我可以把自然语言转换为可执行的 SQL，并展示结果。',
        '',
        '你可以试试这些示例：',
        ...examples,
      ].join('\n'),
      timestamp: new Date(),
    };
    setMessages([welcomeMessage, ...mapped]);
  };

  const handleSelectSession = async (sessionId: number) => {
    setCurrentSessionId(sessionId);
    setConversationId(String(sessionId));
    localStorage.setItem('chatbi:lastSessionId', String(sessionId));
    await loadSessionMessages(sessionId);
  };


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
        conversation_id: currentSessionId ? String(currentSessionId) : undefined,
        database_connection_id: selectedDatabaseId
      };
      const response: ChatResponse = await chatAPI.sendMessage(requestPayload);

      // 同步后端返回的会话ID
      if (response.conversation_id) {
        const sid = parseInt(response.conversation_id, 10);
        if (!Number.isNaN(sid)) {
          setCurrentSessionId(sid);
          setConversationId(response.conversation_id);
          localStorage.setItem('chatbi:lastSessionId', String(sid));
        }
      }

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
      const created = await sessionAPI.createSession('新的会话');
      await handleSelectSession(created.id);
      message.success('已新建会话');
    } catch (error: any) {
      message.error('新建会话失败: ' + (error.message || '未知错误'));
    }
  };

  

  

  const handleDatabaseAdminClose = () => {
    setShowDatabaseAdmin(false);
    // 重新加载数据库连接列表
    loadDatabaseConnections();
    
  };
  const handleSelectTable = (tableName: string) => {
    const suggestionMessage = `我想查看 ${tableName} 表的数据，请帮我生成查询语句 默认查询10条`;
    handleSendMessage(suggestionMessage);
    setShowSchemaDrawer(false);
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

  // 如果显示权限错误页面，则直接返回
  if (accessDenied.show) {
    return (
      <AccessDenied 
        status={accessDenied.status}
        onRetry={() => {
          setAccessDenied({ show: false, status: 403 });
          // 重新检查系统状态
          checkSystemStatus();
        }}
      />
    );
  }

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
          智能问数
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <DatabaseSelector
            selectedDatabaseId={selectedDatabaseId}
            onDatabaseChange={handleDatabaseChange}
            disabled={isLoading || systemStatus !== 'healthy'}
          />
          <Button 
            onClick={() => setShowSchemaDrawer(true)}
            disabled={!selectedDatabaseId || isLoading || systemStatus !== 'healthy'}
          >
            数据库结构
          </Button>
          {userPermissions.hasDatabaseAccess && (
            <Button 
              type="primary" 
              icon={<SettingOutlined />}
              onClick={() => setShowDatabaseAdmin(true)}
            >
              数据库管理
            </Button>
          )}
          <div style={{ width: '150px' }}>
            {renderSystemStatus()}
          </div>
        </div>
      </Header>

      <Layout>
        <Sider 
          width={400}
          collapsedWidth={0}
          collapsible
          collapsed={sidebarCollapsed}
          onCollapse={setSidebarCollapsed}
          trigger={null}
          style={{ background: '#fff', borderRight: '1px solid #f0f0f0', overflow: sidebarCollapsed ? 'visible' : 'hidden', position: 'relative' }}
        >
          <Button
            type="text"
            icon={sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => {
              const willExpand = sidebarCollapsed;
              setSidebarCollapsed(!sidebarCollapsed);
              if (willExpand) {
                setSessionListRefresh(prev => prev + 1);
              }
            }}
            style={{
              position: 'absolute',
              right: sidebarCollapsed ? -36 : 8,
              top: '50%',
              transform: 'translateY(-50%)',
              zIndex: 1002,
              transition: 'right 0.2s, transform 0.2s',
              background: '#fff',
              border: '1px solid #f0f0f0',
              borderRadius: sidebarCollapsed ? '0 4px 4px 0' : '4px',
              boxShadow: '2px 2px 8px rgba(0,0,0,0.1)',
              width: '36px',
              height: '36px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: 0
            }}
          />
          <SessionList
            key={sessionListRefresh}
            selectedSessionId={currentSessionId}
            onSelect={handleSelectSession}
            canDelete={userPermissions.role === 'ADMIN'}
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
        <DatabaseAdmin 
          onClose={handleDatabaseAdminClose}
          canDeleteDatabase={userPermissions.canDeleteDatabase}
        />
      )}
      <Drawer
        placement="right"
        width={420}
        open={showSchemaDrawer}
        onClose={() => setShowSchemaDrawer(false)}
        destroyOnClose
        title="数据库结构"
      >
        <DatabaseSchema 
          onSelectTable={handleSelectTable}
          selectedDatabaseId={selectedDatabaseId}
        />
      </Drawer>
    </Layout>
  );
};

export default App;
