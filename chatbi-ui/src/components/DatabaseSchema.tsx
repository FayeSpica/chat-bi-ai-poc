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
      console.log('Database schema loaded:', data);
      setSchema(data || {});
    } catch (err: any) {
      console.error('Failed to load database schema:', err);
      setError(err.message || '加载数据库结构失败');
      setSchema({});
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
    if (!schema || Object.keys(schema).length === 0) {
      return [];
    }
    
    const treeData = Object.entries(schema).map(([tableName, columns]) => {
      // 处理两种不同的数据格式：
      // 1. 使用 connectionId 时：columns 是 ColumnInfo[]，字段名是 column_name, data_type 等
      // 2. 不使用 connectionId 时：columns 是 DESCRIBE 结果，字段名是 Field, Type 等
      const columnArray = Array.isArray(columns) ? columns : [];
      
      console.log(`Table ${tableName} columns:`, columnArray);
      
      return {
        title: (
          <Space>
            <TableOutlined />
            <Text strong>{tableName}</Text>
            <Tag color="blue">{columnArray.length} 列</Tag>
          </Space>
        ),
        key: `table-${tableName}`,
        children: columnArray
          .map((column: any, index: number) => {
            // 适配两种数据格式
            // DESCRIBE 格式：Field, Type, Null, Key, Default, Extra
            // ColumnInfo 格式：column_name, data_type, is_nullable, column_key, column_default, extra
            const fieldName = column.Field || column.column_name || '';
            const fieldType = column.Type || column.data_type || '';
            const isPrimaryKey = column.Key === 'PRI' || column.column_key === 'PRI';
            const isUnique = column.Key === 'UNI' || column.column_key === 'UNI';
            const isNotNull = column.Null === 'NO' || column.is_nullable === 'NO' || column.is_nullable === false;
            
            // 如果字段名为空，跳过该项
            if (!fieldName) {
              return null;
            }
            
            return {
              title: (
                <Space>
                  <Text code>{fieldName}</Text>
                  <Text type="secondary">{fieldType}</Text>
                  {isPrimaryKey && <Tag color="red" size="small">主键</Tag>}
                  {isNotNull && <Tag color="orange" size="small">非空</Tag>}
                  {isUnique && <Tag color="green" size="small">唯一</Tag>}
                </Space>
              ),
              key: `table-${tableName}-column-${index}-${fieldName}`,
              isLeaf: true
            };
          })
          .filter((child: any) => child !== null) // 过滤掉null项
      };
    });

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
