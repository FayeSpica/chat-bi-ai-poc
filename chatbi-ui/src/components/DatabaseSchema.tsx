import React, { useState, useEffect } from 'react';
import { Card, Tree, Spin, Alert, Button, Typography, Space, Tag } from 'antd';
import { DatabaseOutlined, TableOutlined, ReloadOutlined } from '@ant-design/icons';
import { databaseAPI } from '../services/api';
import { DatabaseSchema as DatabaseSchemaType } from '../types';

const { Title, Text } = Typography;

interface DatabaseSchemaProps {
  onSelectTable?: (tableName: string) => void;
}

const DatabaseSchema: React.FC<DatabaseSchemaProps> = ({ onSelectTable }) => {
  const [schema, setSchema] = useState<DatabaseSchemaType>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadSchema = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const data = await databaseAPI.getFullDatabaseSchema();
      setSchema(data);
    } catch (err: any) {
      setError(err.message || '加载数据库结构失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSchema();
  }, []);

  const renderTreeData = () => {
    const treeData = Object.entries(schema).map(([tableName, columns]) => ({
      title: (
        <Space>
          <TableOutlined />
          <Text strong>{tableName}</Text>
          <Tag color="blue">{columns.length} 列</Tag>
        </Space>
      ),
      key: tableName,
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
        key: `${tableName}-${column.Field}`,
        isLeaf: true
      }))
    }));

    return treeData;
  };

  const handleSelect = (selectedKeys: React.Key[]) => {
    if (selectedKeys.length > 0) {
      const key = selectedKeys[0] as string;
      const tableName = key.split('-')[0];
      if (onSelectTable) {
        onSelectTable(tableName);
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
            <Button size="small" icon={<ReloadOutlined />} onClick={loadSchema}>
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
          <Button 
            size="small" 
            icon={<ReloadOutlined />} 
            onClick={loadSchema}
          >
            刷新
          </Button>
        </Space>
      }
      style={{ height: '100%' }}
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
