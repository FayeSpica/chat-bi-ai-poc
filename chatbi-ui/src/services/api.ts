import axios from 'axios';
import { 
  ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResult, DatabaseSchema,
  DatabaseConnection, DatabaseConnectionCreate, DatabaseConnectionUpdate, DatabaseConnectionTest,
  TableInfo, TableSchema, CommentUpdate,
  PersistedChatSession, PersistedChatMessage
} from '../types';
import { setLoginToken, getLoginToken } from '../utils/cookie';

const API_BASE_URL = 'http://localhost:8000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000, // 增加到2分钟，适应大模型请求时间
  headers: {
    'Content-Type': 'application/json',
  },
});

const sampleToken = {
  userId: 'user2',
  userName: 'user2',
  roleNames: ['ADMIN', 'READER', 'OPERATOR']
};
setLoginToken(sampleToken);
console.log('样例token已设置:', sampleToken);

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 从cookie中获取login_token（已经是base64编码的UserToken JSON）
    // 直接发送base64编码的字符串
    const loginToken = getLoginToken();
    if (loginToken) {
      config.headers['Login-Token'] = loginToken;
    } else {
      // 即使为空也添加header
      config.headers['Login-Token'] = '';
    }
    
    console.log('API Request:', config.method?.toUpperCase(), config.url, 'Login-Token:', loginToken ? '(base64 encoded)' : '(empty)');
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 权限错误处理函数（将在 App.tsx 中设置）
let handleAuthError: ((status: 401 | 403) => void) | null = null;

export const setAuthErrorHandler = (handler: (status: 401 | 403) => void) => {
  handleAuthError = handler;
};

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
    
    // 处理 401/403 权限错误
    const status = error.response?.status;
    if (status === 401 || status === 403) {
      if (handleAuthError) {
        handleAuthError(status as 401 | 403);
      }
    }
    
    return Promise.reject(error);
  }
);

export const chatAPI = {
  // 发送聊天消息
  sendMessage: async (request: ChatRequest): Promise<ChatResponse> => {
    const response = await api.post('/chat', request);
    return response.data;
  },

  // 执行SQL查询
  executeSQL: async (request: SQLExecutionRequest): Promise<SQLExecutionResult> => {
    const response = await api.post('/execute-sql', request);
    return response.data;
  },

  // 获取会话历史
  getConversationHistory: async (conversationId: string) => {
    const response = await api.get(`/conversation/${conversationId}`);
    return response.data;
  },

  // 清除会话历史
  clearConversation: async (conversationId: string) => {
    const response = await api.delete(`/conversation/${conversationId}`);
    return response.data;
  },
};

// 会话与历史 API
export const sessionAPI = {
  listSessions: async (): Promise<PersistedChatSession[]> => {
    const res = await api.get('/sessions');
    return res.data;
  },
  createSession: async (title?: string): Promise<PersistedChatSession> => {
    const res = await api.post('/sessions', title ? { title } : {});
    return res.data;
  },
  renameSession: async (id: number, title: string): Promise<PersistedChatSession> => {
    const res = await api.patch(`/sessions/${id}`, { title });
    return res.data;
  },
  deleteSession: async (id: number): Promise<void> => {
    await api.delete(`/sessions/${id}`);
  },
  getSessionMessages: async (id: number): Promise<PersistedChatMessage[]> => {
    const res = await api.get(`/sessions/${id}/messages`);
    return res.data;
  },
};

export const databaseAPI = {
  // 获取所有表
  getTables: async (): Promise<string[]> => {
    const response = await api.get('/database/tables');
    return response.data.tables;
  },

  // 获取表结构
  getTableSchema: async (tableName: string) => {
    const response = await api.get(`/database/tables/${tableName}/schema`);
    return response.data;
  },

  // 获取完整数据库结构
  getFullDatabaseSchema: async (connectionId?: string): Promise<DatabaseSchema> => {
    const params = connectionId ? { connectionId } : {};
    const response = await api.get('/database/schema', { params });
    return response.data.database_schema;
  },
};

export const systemAPI = {
  // 健康检查
  healthCheck: async () => {
    const response = await api.get('/health', { timeout: 10000 });
    return response.data;
  },
  
  // 获取用户权限
  getUserPermissions: async (): Promise<{ hasDatabaseAccess: boolean; canDeleteDatabase: boolean; role: string }> => {
    const response = await api.get('/user/permissions');
    return response.data;
  },
};

// 数据库后台管理API
export const databaseAdminAPI = {
  // 数据库连接管理
  getConnections: async (): Promise<DatabaseConnection[]> => {
    const response = await api.get('/admin/databases');
    return response.data;
  },

  getActiveConnection: async (): Promise<DatabaseConnection | null> => {
    try {
      const response = await api.get('/admin/databases/active');
      return response.data;
    } catch (error: any) {
      if (error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  },

  createConnection: async (connection: DatabaseConnectionCreate): Promise<DatabaseConnection> => {
    const response = await api.post('/admin/databases', connection);
    return response.data;
  },

  getConnection: async (connectionId: string): Promise<DatabaseConnection> => {
    const response = await api.get(`/admin/databases/${connectionId}`);
    return response.data;
  },

  updateConnection: async (connectionId: string, update: DatabaseConnectionUpdate): Promise<DatabaseConnection> => {
    const response = await api.put(`/admin/databases/${connectionId}`, update);
    return response.data;
  },

  deleteConnection: async (connectionId: string): Promise<void> => {
    await api.delete(`/admin/databases/${connectionId}`);
  },

  testConnection: async (testData: DatabaseConnectionTest): Promise<{ success: boolean; message: string; version?: string }> => {
    const response = await api.post('/admin/databases/test', testData);
    return response.data;
  },

  // 数据库元数据管理
  getTables: async (connectionId: string): Promise<TableInfo[]> => {
    const response = await api.get(`/admin/databases/${connectionId}/tables`);
    return response.data;
  },

  getTableSchema: async (connectionId: string, tableName: string): Promise<TableSchema> => {
    const response = await api.get(`/admin/databases/${connectionId}/tables/${tableName}/schema`);
    return response.data;
  },

  updateComment: async (connectionId: string, commentUpdate: CommentUpdate): Promise<void> => {
    await api.put(`/admin/databases/${connectionId}/comments`, commentUpdate);
  },

  executeSQL: async (connectionId: string, sql: string): Promise<{ success: boolean; data?: any; error?: string; row_count?: number }> => {
    const response = await api.post(`/admin/databases/${connectionId}/execute-sql`, { sql });
    return response.data;
  },
};

export default api;
