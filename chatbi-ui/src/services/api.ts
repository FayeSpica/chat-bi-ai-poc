import axios from 'axios';
import { ChatRequest, ChatResponse, SQLExecutionRequest, SQLExecutionResult, DatabaseSchema } from '../types';

const API_BASE_URL = 'http://localhost:8000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120000, // 增加到2分钟，适应大模型请求时间
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    console.log('API Request:', config.method?.toUpperCase(), config.url);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
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
  getFullDatabaseSchema: async (): Promise<DatabaseSchema> => {
    const response = await api.get('/database/schema');
    return response.data.database_schema;
  },
};

export const systemAPI = {
  // 健康检查
  healthCheck: async () => {
    // 健康检查在后端是根路径 /health，不在 /api 之下
    const response = await axios.get('http://localhost:8000/health', { timeout: 10000 });
    return response.data;
  },
};

export default api;
