import React from 'react';
import { Card, Tag, Button, Table, Space, Tooltip, Typography } from 'antd';
import { PlayCircleOutlined, CodeOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { tomorrow } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { ChatMessage as ChatMessageType, SQLExecutionResult } from '../types';

const { Text, Paragraph } = Typography;

interface ChatMessageProps {
  message: ChatMessageType;
  onExecuteSQL?: (sql: string) => void;
  isExecuting?: boolean;
}

const ChatMessage: React.FC<ChatMessageProps> = ({ 
  message, 
  onExecuteSQL, 
  isExecuting = false 
}) => {
  const isUser = message.role === 'user';

  const renderSemanticSQL = (semanticSQL: any) => {
    if (!semanticSQL) return null;

    return (
      <div className="semantic-sql-info">
        <h4 style={{ margin: '0 0 8px 0', color: '#0ea5e9' }}>
          <DatabaseOutlined /> 语义SQL结构
        </h4>
        
        {semanticSQL.tables && semanticSQL.tables.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>涉及表: </Text>
            {semanticSQL.tables.map((table: string) => (
              <Tag key={table} color="blue">{table}</Tag>
            ))}
          </div>
        )}

        {semanticSQL.columns && semanticSQL.columns.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>查询列: </Text>
            {semanticSQL.columns.map((column: string) => (
              <Tag key={column} color="green">{column}</Tag>
            ))}
          </div>
        )}

        {semanticSQL.conditions && semanticSQL.conditions.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>筛选条件: </Text>
            {semanticSQL.conditions.map((condition: any, index: number) => (
              <Tag key={index} color="orange">
                {condition.column} {condition.operator} {condition.value}
              </Tag>
            ))}
          </div>
        )}

        {semanticSQL.aggregations && semanticSQL.aggregations.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>聚合函数: </Text>
            {semanticSQL.aggregations.map((agg: any, index: number) => (
              <Tag key={index} color="purple">
                {agg.function}({agg.column})
              </Tag>
            ))}
          </div>
        )}

        {semanticSQL.joins && semanticSQL.joins.length > 0 && (
          <div style={{ marginBottom: 8 }}>
            <Text strong>表连接: </Text>
            {semanticSQL.joins.map((join: any, index: number) => (
              <Tag key={index} color="cyan">
                {join.type} JOIN {join.table2}
              </Tag>
            ))}
          </div>
        )}
      </div>
    );
  };

  const renderSQLQuery = (sql: string) => {
    if (!sql) return null;

    return (
      <Card 
        size="small" 
        title={
          <Space>
            <CodeOutlined />
            <Text strong>生成的MySQL SQL</Text>
            {onExecuteSQL && (
              <Button
                type="primary"
                size="small"
                icon={<PlayCircleOutlined />}
                loading={isExecuting}
                onClick={() => onExecuteSQL(sql)}
              >
                执行查询
              </Button>
            )}
          </Space>
        }
        style={{ marginTop: 8 }}
      >
        <SyntaxHighlighter
          language="sql"
          style={tomorrow}
          customStyle={{
            margin: 0,
            fontSize: '12px',
            maxHeight: '300px',
            overflow: 'auto'
          }}
        >
          {sql}
        </SyntaxHighlighter>
      </Card>
    );
  };

  const renderExecutionResult = (result: SQLExecutionResult) => {
    if (!result) return null;

    if (!result.success) {
      return (
        <Card size="small" style={{ marginTop: 8 }}>
          <Text type="danger">
            <strong>执行失败:</strong> {result.error}
          </Text>
        </Card>
      );
    }

    if (!result.data || result.data.length === 0) {
      return (
        <Card size="small" style={{ marginTop: 8 }}>
          <Text type="warning">查询成功，但没有返回数据</Text>
        </Card>
      );
    }

    const columns = Object.keys(result.data[0]).map(key => ({
      title: key,
      dataIndex: key,
      key,
      ellipsis: true,
      render: (text: any) => (
        <Tooltip title={String(text)}>
          <Text style={{ fontSize: '12px' }}>{String(text)}</Text>
        </Tooltip>
      )
    }));

    return (
      <Card 
        size="small" 
        title={`查询结果 (${result.row_count} 行)`}
        style={{ marginTop: 8 }}
      >
        <Table
          columns={columns}
          dataSource={result.data}
          pagination={{
            pageSize: 10,
            size: 'small',
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条记录`
          }}
          scroll={{ x: 'max-content' }}
          size="small"
          className="data-table"
        />
      </Card>
    );
  };

  return (
    <div className={`message-item ${isUser ? 'message-user' : 'message-assistant'}`}>
      <div className="message-content">
        <div style={{ marginBottom: 8 }}>
          <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
            {message.content}
          </Paragraph>
        </div>
        
        {!isUser && (
          <>
            {message.semantic_sql && renderSemanticSQL(message.semantic_sql)}
            {message.sql_query && renderSQLQuery(message.sql_query)}
            {message.execution_result && renderExecutionResult(message.execution_result)}
          </>
        )}
        
        <div style={{ fontSize: '12px', color: '#999', marginTop: 8 }}>
          {message.timestamp.toLocaleTimeString()}
        </div>
      </div>
    </div>
  );
};

export default ChatMessage;
