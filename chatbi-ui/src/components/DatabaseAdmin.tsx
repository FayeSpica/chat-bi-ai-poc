import React, { useState, useEffect } from 'react';
import { 
  DatabaseConnection, DatabaseConnectionCreate, DatabaseConnectionUpdate, DatabaseConnectionTest,
  TableInfo, TableSchema, CommentUpdate 
} from '../types';
import { databaseAdminAPI } from '../services/api';
import './DatabaseAdmin.css';

interface DatabaseAdminProps {
  onClose: () => void;
  canDeleteDatabase?: boolean;
}

const DatabaseAdmin: React.FC<DatabaseAdminProps> = (props) => {
  const { onClose, canDeleteDatabase = false } = props;
  const [activeTab, setActiveTab] = useState<'connections' | 'metadata'>('connections');
  const [connections, setConnections] = useState<DatabaseConnection[]>([]);
  const [selectedConnection, setSelectedConnection] = useState<DatabaseConnection | null>(null);
  const [tables, setTables] = useState<TableInfo[]>([]);
  const [selectedTable, setSelectedTable] = useState<TableInfo | null>(null);
  const [tableSchema, setTableSchema] = useState<TableSchema | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 加载数据库连接列表
  const loadConnections = async () => {
    try {
      setLoading(true);
      const data = await databaseAdminAPI.getConnections();
      setConnections(data);
    } catch (err) {
      setError('加载数据库连接失败');
    } finally {
      setLoading(false);
    }
  };

  // 加载数据库表列表
  const loadTables = async (connectionId: string) => {
    try {
      setLoading(true);
      setError(null);
      const data = await databaseAdminAPI.getTables(connectionId);
      setTables(data);
    } catch (err: any) {
      console.error('Load tables error:', err);
      let errorMessage = '加载数据库表失败';
      
      if (err.response?.status === 503) {
        errorMessage = '数据库连接失败，请检查连接配置';
      } else if (err.response?.status === 404) {
        errorMessage = '数据库连接不存在';
      } else if (err.response?.data?.detail) {
        errorMessage = err.response.data.detail;
      }
      
      setError(errorMessage);
      setTables([]);
    } finally {
      setLoading(false);
    }
  };

  // 加载表结构
  const loadTableSchema = async (connectionId: string, tableName: string) => {
    try {
      setLoading(true);
      setError(null);
      const data = await databaseAdminAPI.getTableSchema(connectionId, tableName);
      setTableSchema(data);
    } catch (err: any) {
      console.error('Load table schema error:', err);
      let errorMessage = '加载表结构失败';
      
      if (err.response?.status === 503) {
        errorMessage = '数据库连接失败，请检查连接配置';
      } else if (err.response?.status === 404) {
        errorMessage = '表不存在或数据库连接不存在';
      } else if (err.response?.data?.detail) {
        errorMessage = err.response.data.detail;
      }
      
      setError(errorMessage);
      setTableSchema(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConnections();
  }, []);

  const handleConnectionSelect = (connection: DatabaseConnection) => {
    setSelectedConnection(connection);
    loadTables(connection.id!);
    setSelectedTable(null);
    setTableSchema(null);
  };

  const handleTableSelect = (table: TableInfo) => {
    setSelectedTable(table);
    if (selectedConnection) {
      loadTableSchema(selectedConnection.id!, table.table_name);
    }
  };

  return (
    <div className="database-admin">
      <div className="admin-header">
        <h2>数据库后台管理</h2>
        <button className="close-btn" onClick={onClose}>×</button>
      </div>

      <div className="admin-tabs">
        <button 
          className={`tab-btn ${activeTab === 'connections' ? 'active' : ''}`}
          onClick={() => setActiveTab('connections')}
        >
          连接管理
        </button>
        <button 
          className={`tab-btn ${activeTab === 'metadata' ? 'active' : ''}`}
          onClick={() => setActiveTab('metadata')}
        >
          元数据管理
        </button>
      </div>

      {error && (
        <div className="error-message">
          {error}
          <button onClick={() => setError(null)}>×</button>
        </div>
      )}

      {activeTab === 'connections' && (
        <ConnectionManager 
          connections={connections}
          onConnectionsChange={loadConnections}
          canDeleteDatabase={canDeleteDatabase}
        />
      )}

      {activeTab === 'metadata' && (
        <MetadataManager
          connections={connections}
          selectedConnection={selectedConnection}
          onConnectionSelect={handleConnectionSelect}
          tables={tables}
          selectedTable={selectedTable}
          onTableSelect={handleTableSelect}
          tableSchema={tableSchema}
          loading={loading}
        />
      )}
    </div>
  );
};

// 连接管理组件
interface ConnectionManagerProps {
  connections: DatabaseConnection[];
  onConnectionsChange: () => void;
  canDeleteDatabase?: boolean;
}

const ConnectionManager: React.FC<ConnectionManagerProps> = ({ connections, onConnectionsChange, canDeleteDatabase = false }) => {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingConnection, setEditingConnection] = useState<DatabaseConnection | null>(null);
  const [testingConnection, setTestingConnection] = useState<string | null>(null);

  const handleCreateConnection = async (connectionData: DatabaseConnectionCreate) => {
    try {
      await databaseAdminAPI.createConnection(connectionData);
      onConnectionsChange();
      setShowCreateForm(false);
    } catch (error) {
      console.error('创建连接失败:', error);
    }
  };

  const handleUpdateConnection = async (id: string, updateData: DatabaseConnectionUpdate) => {
    try {
      await databaseAdminAPI.updateConnection(id, updateData);
      onConnectionsChange();
      setEditingConnection(null);
    } catch (error) {
      console.error('更新连接失败:', error);
    }
  };

  const handleDeleteConnection = async (id: string) => {
    const connection = connections.find(c => c.id === id);
    
    if (!connection) return;
    
    // 如果是最后一个连接，不允许删除
    if (connections.length <= 1) {
      alert('不能删除最后一个数据库连接');
      return;
    }
    
    if (window.confirm(`确定要删除数据库连接"${connection.name}"吗？`)) {
      try {
        await databaseAdminAPI.deleteConnection(id);
        onConnectionsChange();
      } catch (error: any) {
        console.error('删除连接失败:', error);
        if (error.response?.status === 400) {
          alert('不能删除最后一个数据库连接或默认连接');
        } else {
          alert('删除连接失败: ' + (error.message || '未知错误'));
        }
      }
    }
  };

  const handleTestConnection = async (connection: DatabaseConnection) => {
    setTestingConnection(connection.id!);
    try {
      const testData: DatabaseConnectionTest = {
        host: connection.host,
        port: connection.port,
        username: connection.username,
        password: connection.password,
        database_name: connection.database_name,
        charset_name: connection.charset_name
      };
      const result = await databaseAdminAPI.testConnection(testData);
      alert(result.success ? `连接成功！\n版本: ${result.version}` : `连接失败: ${result.message}`);
    } catch (error) {
      alert('测试连接时发生错误');
    } finally {
      setTestingConnection(null);
    }
  };

  return (
    <div className="connection-manager">
      <div className="section-header">
        <h3>数据库连接管理</h3>
        <button 
          className="btn btn-primary"
          onClick={() => setShowCreateForm(true)}
        >
          添加连接
        </button>
      </div>

      {showCreateForm && (
        <ConnectionForm
          onSubmit={handleCreateConnection}
          onCancel={() => setShowCreateForm(false)}
        />
      )}

      {editingConnection && (
        <ConnectionForm
          connection={editingConnection}
          onSubmit={(data) => handleUpdateConnection(editingConnection.id!, data)}
          onCancel={() => setEditingConnection(null)}
          isEdit={true}
        />
      )}

      <div className="connections-list">
        {connections.map(connection => (
          <div key={connection.id} className="connection-item">
            <div className="connection-info">
              <h4>
                {connection.name}
                {connection.name === '默认数据库' && (
                  <span className="default-badge">默认</span>
                )}
              </h4>
              <p>{connection.host}:{connection.port}/{connection.database_name}</p>
              {connection.description && <p className="description">{connection.description}</p>}
              <span className={`status ${connection.is_active ? 'active' : 'inactive'}`}>
                {connection.is_active ? '活跃' : '非活跃'}
              </span>
            </div>
            <div className="connection-actions">
              <button 
                className="btn btn-sm"
                onClick={() => handleTestConnection(connection)}
                disabled={testingConnection === connection.id}
              >
                {testingConnection === connection.id ? '测试中...' : '测试'}
              </button>
              <button 
                className="btn btn-sm"
                onClick={() => setEditingConnection(connection)}
              >
                编辑
              </button>
              <button 
                className="btn btn-sm btn-danger"
                onClick={() => handleDeleteConnection(connection.id!)}
                disabled={!canDeleteDatabase}
                title={!canDeleteDatabase ? '需要 ADMIN 权限才能删除数据库连接' : undefined}
              >
                删除
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

// 元数据管理组件
interface MetadataManagerProps {
  connections: DatabaseConnection[];
  selectedConnection: DatabaseConnection | null;
  onConnectionSelect: (connection: DatabaseConnection) => void;
  tables: TableInfo[];
  selectedTable: TableInfo | null;
  onTableSelect: (table: TableInfo) => void;
  tableSchema: TableSchema | null;
  loading: boolean;
}

const MetadataManager: React.FC<MetadataManagerProps> = ({
  connections,
  selectedConnection,
  onConnectionSelect,
  tables,
  selectedTable,
  onTableSelect,
  tableSchema,
  loading
}) => {
  const handleUpdateComment = async (commentUpdate: CommentUpdate) => {
    if (!selectedConnection) return;
    
    try {
      await databaseAdminAPI.updateComment(selectedConnection.id!, commentUpdate);
      // 重新加载表结构
      if (selectedTable) {
        const updatedSchema = await databaseAdminAPI.getTableSchema(selectedConnection.id!, selectedTable.table_name);
        // 这里需要更新tableSchema状态，但组件结构需要调整
        alert('注释更新成功');
      }
    } catch (error) {
      console.error('更新注释失败:', error);
      alert('更新注释失败');
    }
  };

  return (
    <div className="metadata-manager">
      <div className="metadata-layout">
        <div className="connections-panel">
          <h3>选择数据库连接</h3>
          <div className="connections-list">
            {connections.map(connection => (
              <div 
                key={connection.id}
                className={`table-item ${selectedConnection?.id === connection.id ? 'selected' : ''}`}
                onClick={() => onConnectionSelect(connection)}
              >
                <h4>{connection.name}</h4>
                <p>{connection.host}:{connection.port}/{connection.database_name}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="tables-panel">
          <h3>数据库表</h3>
          {loading ? (
            <div className="loading">加载中...</div>
          ) : (
            <div className="tables-list">
              {tables.map(table => (
                <div 
                  key={table.table_name}
                  className={`table-item ${selectedTable?.table_name === table.table_name ? 'selected' : ''}`}
                  onClick={() => onTableSelect(table)}
                >
                  <h4>{table.table_name}</h4>
                  {table.table_comment && <p className="comment">{table.table_comment}</p>}
                  <div className="table-meta">
                    <span>行数: {table.table_rows || 'N/A'}</span>
                    <span>大小: {table.table_size || 'N/A'}MB</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="schema-panel">
          <h3>表结构</h3>
          {loading ? (
            <div className="loading">加载中...</div>
          ) : tableSchema ? (
            <TableSchemaView 
              schema={tableSchema}
              onUpdateComment={handleUpdateComment}
            />
          ) : (
            <div className="no-selection">请选择表查看结构</div>
          )}
        </div>
      </div>
    </div>
  );
};

// 连接表单组件
interface ConnectionFormProps {
  connection?: DatabaseConnection;
  onSubmit: (data: DatabaseConnectionCreate | DatabaseConnectionUpdate) => void;
  onCancel: () => void;
  isEdit?: boolean;
}

const ConnectionForm: React.FC<ConnectionFormProps> = ({ connection, onSubmit, onCancel, isEdit = false }) => {
  const [formData, setFormData] = useState({
    name: connection?.name || '',
    host: connection?.host || '',
    port: connection?.port || 3306,
    username: connection?.username || '',
    password: connection?.password || '',
    database_name: connection?.database_name || '',
    charset_name: connection?.charset_name || 'utf8mb4',
    description: connection?.description || ''
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit(formData);
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: name === 'port' ? parseInt(value) || 3306 : value
    }));
  };

  return (
    <div className="connection-form-overlay">
      <div className="connection-form">
        <h3>{isEdit ? '编辑连接' : '添加连接'}</h3>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>连接名称</label>
            <input
              type="text"
              name="name"
              value={formData.name}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>主机地址</label>
            <input
              type="text"
              name="host"
              value={formData.host}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>端口</label>
            <input
              type="number"
              name="port"
              value={formData.port}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>用户名</label>
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>密码</label>
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>数据库名</label>
            <input
              type="text"
              name="database_name"
              value={formData.database_name}
              onChange={handleChange}
              required
            />
          </div>
          <div className="form-group">
            <label>字符集</label>
            <input
              type="text"
              name="charset_name"
              value={formData.charset_name}
              onChange={handleChange}
            />
          </div>
          <div className="form-group">
            <label>描述</label>
            <input
              type="text"
              name="description"
              value={formData.description}
              onChange={handleChange}
            />
          </div>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary">
              {isEdit ? '更新' : '创建'}
            </button>
            <button type="button" className="btn" onClick={onCancel}>
              取消
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// 表结构视图组件
interface TableSchemaViewProps {
  schema: TableSchema;
  onUpdateComment: (commentUpdate: CommentUpdate) => void;
}

const TableSchemaView: React.FC<TableSchemaViewProps> = ({ schema, onUpdateComment }) => {
  const [editingComment, setEditingComment] = useState<{ type: 'table' | 'column'; name: string } | null>(null);
  const [newComment, setNewComment] = useState('');

  const handleEditComment = (type: 'table' | 'column', name: string, currentComment: string = '') => {
    setEditingComment({ type, name });
    setNewComment(currentComment);
  };

  const handleSaveComment = () => {
    if (!editingComment) return;

    const commentUpdate: CommentUpdate = {
      table_name: schema.table_name,
      comment: newComment
    };

    if (editingComment.type === 'column') {
      commentUpdate.column_name = editingComment.name;
    }

    onUpdateComment(commentUpdate);
    setEditingComment(null);
    setNewComment('');
  };

  return (
    <div className="table-schema-view">
      <div className="table-info">
        <h4>{schema.table_name}</h4>
        <div className="table-comment">
          {editingComment?.type === 'table' ? (
            <div className="comment-editor">
              <input
                type="text"
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                placeholder="输入表注释"
              />
              <button onClick={handleSaveComment}>保存</button>
              <button onClick={() => setEditingComment(null)}>取消</button>
            </div>
          ) : (
            <div className="comment-display" onClick={() => handleEditComment('table', schema.table_name, schema.table_comment || '')}>
              {schema.table_comment || '点击添加表注释'}
            </div>
          )}
        </div>
      </div>

      <div className="columns-list">
        <h5>字段列表</h5>
        {schema.columns.map(column => (
          <div key={column.column_name} className="column-item">
            <div className="column-info">
              <span className="column-name">{column.column_name}</span>
              <span className="column-type">{column.data_type}</span>
              {column.column_key && <span className="column-key">{column.column_key}</span>}
            </div>
            <div className="column-comment">
              {editingComment?.type === 'column' && editingComment.name === column.column_name ? (
                <div className="comment-editor">
                  <input
                    type="text"
                    value={newComment}
                    onChange={(e) => setNewComment(e.target.value)}
                    placeholder="输入字段注释"
                  />
                  <button onClick={handleSaveComment}>保存</button>
                  <button onClick={() => setEditingComment(null)}>取消</button>
                </div>
              ) : (
                <div className="comment-display" onClick={() => handleEditComment('column', column.column_name, column.column_comment || '')}>
                  {column.column_comment || '点击添加字段注释'}
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default DatabaseAdmin;
