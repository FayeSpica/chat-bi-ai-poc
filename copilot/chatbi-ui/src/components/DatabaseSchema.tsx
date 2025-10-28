import React, { useState, useEffect } from 'react';
import { Card, Tree, Spin, Alert, Button, Typography, Space, Tag, Badge } from 'antd';
import { DatabaseOutlined, TableOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import { databaseAPI, databaseAdminAPI } from '../services/api';
import { DatabaseSchema as DatabaseSchemaType, DatabaseConnection } from '../types';

const { Title, Text } = Typography;

interface DatabaseSchemaProps {
  onSelectTable?: (tableName: string) => void;
  selectedDatabaseId?: string;
}

const DatabaseSchema: React.FC<DatabaseSchemaProps> = ({ onSelectTable, selectedDatabaseId }) => {
  const [schema, setSchema] = useState<DatabaseSchemaType>({});
  const [activeConnection, setActiveConnection] = useState<DatabaseConnection | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadActiveConnection = async () => {
    try {
      let connection;
      if (selectedDatabaseId) {
        connection = await databaseAdminAPI.getConnection(selectedDatabaseId);
      } else {
        connection = await databaseAdminAPI.getActiveConnection();
      }
      setActiveConnection(connection);
    } catch (err) {
      console.warn('Failed to load connection:', err);
      setActiveConnection(null);
    }
  };

  const loadSchema = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const data = await databaseAPI.getFullDatabaseSchema(selectedDatabaseId);
      setSchema(data);
    } catch (err: any) {
      setError(err.message || '加载数据库结构失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRefresh = async () => {
    await loadActiveConnection();
    await loadSchema();
  };

  useEffect(() => {
    handleRefresh();
  }, [selectedDatabaseId]);

  const renderTreeData = () => {
    const treeData = Object.entries(schema).map(([tableName, columns]) => ({
      title: (
        <Space>
          <TableOutlined />
          <Text strong>{tableName}</Text>
          <Tag color="blue">{columns.length} 列</Tag>
        </Space>
      ),
      key: `table-${tableName}`,
      children: columns.map((column, index) => ({
        title: (
          <Space>
            <Text code>{column.Field}</Text>
            <Text type="secondary">{column.Type}</Text>
            {column.Key === 'PRI' && <Tag color="red" size="small">主键</Tag>}
            {column.Null === 'NO' && <Tag color="orange" size="small">非空</Tag>}
            {column.Key === 'UNI' && <Tag color="green" size="small">唯一</Tag>}
          </Space>
        ),
        key: `table-${tableName}-column-${index}-${column.Field}`,
        isLeaf: true
      }))
    }));

    return treeData;
  };

  const handleSelect = (selectedKeys: React.Key[]) => {
    if (selectedKeys.length > 0) {
      const key = selectedKeys[0] as string;
      // Extract table name from the new key format: table-{tableName} or table-{tableName}-column-...
      const keyParts = key.split('-');
      if (keyParts[0] === 'table' && keyParts[1]) {
        const tableName = keyParts[1];
        if (onSelectTable) {
          onSelectTable(tableName);
        }
      }
    }
  };

  if (loading) {
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: '20px' }}>
          <Spin size="large" />
          <div style={{ marginTop: 16 }}>加载数据库结构...</div>
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Alert
          message="加载失败"
          description={error}
          type="error"
          showIcon
          action={
            <Button size="small" icon={<ReloadOutlined />} onClick={handleRefresh}>
              重试
            </Button>
          }
        />
      </Card>
    );
  }

  return (
    <Card
      title={
        <Space>
          <DatabaseOutlined />
          <Title level={5} style={{ margin: 0 }}>数据库结构</Title>
        </Space>
      }
      extra={
        <Space size="small">
          {activeConnection ? (
            <Badge status="processing" text={
              <Text type="secondary" style={{ fontSize: '12px' }}>
                {activeConnection.name}
              </Text>
            } />
          ) : (
            <Tag color="default" style={{ fontSize: '12px' }}>默认连接</Tag>
          )}
          <Button 
            size="small" 
            icon={<ReloadOutlined />} 
            onClick={handleRefresh}
          >
            刷新
          </Button>
        </Space>
      }
      style={{ height: '100%' }}
      bodyStyle={{ padding: '12px' }}
    >
      <Tree
        treeData={renderTreeData()}
        onSelect={handleSelect}
        showIcon
        defaultExpandAll={false}
        style={{ fontSize: '12px' }}
      />
    </Card>
  );
};

export default DatabaseSchema;
