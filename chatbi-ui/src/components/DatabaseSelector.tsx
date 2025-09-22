import React, { useState, useEffect } from 'react';
import { Select, message } from 'antd';
import { DatabaseOutlined, ReloadOutlined } from '@ant-design/icons';
import { databaseAdminAPI } from '../services/api';
import { DatabaseConnection } from '../types';

const { Option } = Select;

interface DatabaseSelectorProps {
  selectedDatabaseId?: string;
  onDatabaseChange: (connectionId: string) => void;
  disabled?: boolean;
}

const DatabaseSelector: React.FC<DatabaseSelectorProps> = ({
  selectedDatabaseId,
  onDatabaseChange,
  disabled = false
}) => {
  const [connections, setConnections] = useState<DatabaseConnection[]>([]);
  const [loading, setLoading] = useState(false);

  // 加载数据库连接列表
  const loadConnections = async () => {
    try {
      setLoading(true);
      const data = await databaseAdminAPI.getConnections();
      setConnections(data);
    } catch (error: any) {
      console.error('加载数据库连接失败:', error);
      message.error('加载数据库连接失败: ' + (error.message || '未知错误'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadConnections();
  }, []);

  const handleDatabaseChange = (connectionId: string) => {
    onDatabaseChange(connectionId);
  };

  const handleRefresh = () => {
    loadConnections();
  };

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
      <DatabaseOutlined style={{ color: '#1890ff' }} />
      <span style={{ fontSize: '14px', fontWeight: '500' }}>数据库:</span>
      <Select
        value={selectedDatabaseId}
        onChange={handleDatabaseChange}
        placeholder="选择数据库"
        style={{ minWidth: 200 }}
        loading={loading}
        disabled={disabled}
        suffixIcon={<ReloadOutlined onClick={handleRefresh} style={{ cursor: 'pointer' }} />}
      >
        {connections.map(connection => (
          <Option key={connection.id} value={connection.id!}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <span>{connection.name}</span>
              <span style={{ fontSize: '12px', color: '#666', marginLeft: '8px' }}>
                {connection.host}:{connection.port}/{connection.database}
              </span>
            </div>
          </Option>
        ))}
      </Select>
    </div>
  );
};

export default DatabaseSelector;
