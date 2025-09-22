import React, { useEffect, useMemo, useState } from 'react';
import { Card, Tag, Button, Table, Space, Tooltip, Typography, Segmented, Select, Divider, Collapse } from 'antd';
import { PlayCircleOutlined, CodeOutlined, DatabaseOutlined } from '@ant-design/icons';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { tomorrow } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { ChatMessage as ChatMessageType, SQLExecutionResult } from '../types';
import { Line, Column, Bar, Pie } from '@ant-design/plots';
import ReactMarkdown from 'react-markdown';

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

    // 动态可视化视图
    const allKeys = Object.keys(result.data[0] || {});
    const numericKeys = allKeys.filter(k => typeof result.data[0][k] === 'number' || !isNaN(Number(result.data[0][k])));
    const categoryKeys = allKeys.filter(k => !numericKeys.includes(k));

    const [viewMode, setViewMode] = useState<'table' | 'line' | 'column' | 'bar' | 'pie'>('table');
    const [xField, setXField] = useState<string>(categoryKeys[0] || allKeys[0]);
    const [yField, setYField] = useState<string>(numericKeys[0] || allKeys[1] || allKeys[0]);
    const [seriesField, setSeriesField] = useState<string | undefined>(categoryKeys[1]);

    // 当数据或列变化时，自动修正字段选择
    useEffect(() => {
      if (!xField || !allKeys.includes(xField)) {
        setXField(categoryKeys[0] || allKeys[0]);
      }
      if (!yField || !allKeys.includes(yField)) {
        setYField(numericKeys[0] || allKeys.find(k => k !== xField) || allKeys[0]);
      }
      if (seriesField && !allKeys.includes(seriesField)) {
        setSeriesField(categoryKeys.find(k => k !== xField));
      }
    }, [JSON.stringify(allKeys)]);

    const tableColumns = useMemo(() => (
      Object.keys(result.data[0]).map(key => ({
        title: key,
        dataIndex: key,
        key,
        ellipsis: true,
        render: (text: any) => (
          <Tooltip title={String(text)}>
            <Text style={{ fontSize: '12px' }}>{String(text)}</Text>
          </Tooltip>
        )
      }))
    ), [JSON.stringify(result.data[0])]);

    const chartData = useMemo(() => (
      result.data.map(row => ({
        x: row[xField],
        y: Number(row[yField]),
        series: seriesField ? String(row[seriesField]) : undefined
      }))
    ), [JSON.stringify(result.data), xField, yField, seriesField]);

    const commonChartHeight = 320;

    const renderChartControls = () => (
      <Space wrap size="small" style={{ width: '100%', justifyContent: 'space-between' }}>
        <Segmented
          options={[
            { label: '表格', value: 'table' },
            { label: '折线图', value: 'line' },
            { label: '柱状图', value: 'column' },
            { label: '条形图', value: 'bar' },
            { label: '饼状图', value: 'pie' },
          ]}
          value={viewMode}
          onChange={(v) => setViewMode(v as any)}
        />
        {viewMode !== 'table' && (
          <Space size="small" wrap>
            <Select
              size="small"
              style={{ minWidth: 140 }}
              value={xField}
              onChange={setXField}
              options={allKeys.map(k => ({ label: `X: ${k}`, value: k }))}
            />
            {viewMode !== 'pie' && (
              <Select
                size="small"
                style={{ minWidth: 140 }}
                value={yField}
                onChange={setYField}
                options={allKeys.map(k => ({ label: `Y: ${k}`, value: k }))}
              />
            )}
            {['line', 'column', 'bar', 'pie'].includes(viewMode) && (
              <Select
                allowClear
                placeholder="分组(可选)"
                size="small"
                style={{ minWidth: 160 }}
                value={seriesField}
                onChange={setSeriesField}
                options={allKeys.map(k => ({ label: `分组: ${k}`, value: k }))}
              />
            )}
          </Space>
        )}
      </Space>
    );

    const renderChart = () => {
      if (viewMode === 'table') {
        return (
          <Table
            columns={tableColumns}
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
        );
      }

      if (viewMode === 'line') {
        return (
          <Line
            height={commonChartHeight}
            data={chartData}
            xField="x"
            yField="y"
            seriesField={seriesField ? 'series' : undefined}
            point={{ size: 3, shape: 'circle' }}
            tooltip={{
              fields: ['x', 'y', ...(seriesField ? ['series'] : [])]
            }}
          />
        );
      }

      if (viewMode === 'column') {
        return (
          <Column
            height={commonChartHeight}
            data={chartData}
            xField="x"
            yField="y"
            seriesField={seriesField ? 'series' : undefined}
            tooltip={{
              fields: ['x', 'y', ...(seriesField ? ['series'] : [])]
            }}
          />
        );
      }

      if (viewMode === 'bar') {
        return (
          <Bar
            height={commonChartHeight}
            data={chartData}
            xField="y"
            yField="x"
            seriesField={seriesField ? 'series' : undefined}
            tooltip={{
              fields: ['x', 'y', ...(seriesField ? ['series'] : [])]
            }}
          />
        );
      }

      if (viewMode === 'pie') {
        const pieData = chartData.map(d => ({ type: String(d.x), value: d.y, series: d.series }));
        return (
          <Pie
            height={commonChartHeight}
            data={pieData}
            angleField="value"
            colorField={seriesField ? 'series' : 'type'}
            innerRadius={0}
            label={{
              text: 'type',
              style: { fontSize: 12 }
            }}
            tooltip={{
              fields: ['type', 'value', ...(seriesField ? ['series'] : [])]
            }}
          />
        );
      }

      return null;
    };

    return (
      <Card 
        size="small" 
        title={`查询结果 (${result.row_count} 行)`}
        style={{ marginTop: 8 }}
      >
        {renderChartControls()}
        <Divider style={{ margin: '8px 0' }} />
        {renderChart()}
      </Card>
    );
  };

  const renderDebugInfo = () => {
    if (isUser || !message.debug_info) return null;

    const jsonBlock = (obj: any) => (
      <SyntaxHighlighter
        language="json"
        style={tomorrow}
        customStyle={{ margin: 0, fontSize: '12px', maxHeight: '300px', overflow: 'auto' }}
      >
        {JSON.stringify(obj, null, 2)}
      </SyntaxHighlighter>
    );

    return (
      <Card size="small" style={{ marginTop: 8 }}>
        <Collapse size="small">
          <Collapse.Panel header="调试信息" key="debug">
            <Space direction="vertical" style={{ width: '100%' }} size="small">
              {message.debug_info.request && (
                <Card size="small" title="请求参数">
                  {jsonBlock(message.debug_info.request)}
                </Card>
              )}
              {message.debug_info.response && (
                <Card size="small" title="模型响应">
                  {jsonBlock(message.debug_info.response)}
                </Card>
              )}
              {message.debug_info.ollama && (
                <Card size="small" title="Ollama调试">
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    <Space size="small" wrap>
                      <Tag color="geekblue">提供方: {message.debug_info.ollama.provider || 'ollama'}</Tag>
                      {message.debug_info.ollama.model && (
                        <Tag color="blue">模型: {message.debug_info.ollama.model}</Tag>
                      )}
                    </Space>
                    {message.debug_info.ollama.base_url && (
                      <Text type="secondary" style={{ fontSize: 12 }}>服务地址: {message.debug_info.ollama.base_url}</Text>
                    )}
                    {message.debug_info.ollama.prompt && (
                      <Card size="small" type="inner" title="提示词 (Prompt)">
                        <SyntaxHighlighter
                          language="markdown"
                          style={tomorrow}
                          customStyle={{ margin: 0, fontSize: '12px', maxHeight: '260px', overflow: 'auto' }}
                        >
                          {message.debug_info.ollama.prompt}
                        </SyntaxHighlighter>
                      </Card>
                    )}
                    {message.debug_info.ollama.raw_response && (
                      <Card size="small" type="inner" title="模型原始回复">
                        <SyntaxHighlighter
                          language="markdown"
                          style={tomorrow}
                          customStyle={{ margin: 0, fontSize: '12px', maxHeight: '260px', overflow: 'auto' }}
                        >
                          {String(message.debug_info.ollama.raw_response)}
                        </SyntaxHighlighter>
                      </Card>
                    )}
                    {message.debug_info.ollama.error && (
                      <Text type="danger">错误: {message.debug_info.ollama.error}</Text>
                    )}
                  </Space>
                </Card>
              )}
              {'sql_execution' in (message.debug_info || {}) && (
                <Card size="small" title="SQL执行结果">
                  {jsonBlock(message.debug_info.sql_execution)}
                </Card>
              )}
            </Space>
          </Collapse.Panel>
        </Collapse>
      </Card>
    );
  };

  return (
    <div className={`message-item ${isUser ? 'message-user' : 'message-assistant'}`}>
      <div className="message-content">
        <div style={{ marginBottom: 8 }}>
          {isUser ? (
            <Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
              {message.content}
            </Paragraph>
          ) : (
            <ReactMarkdown
              components={{
                code({ inline, className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || '');
                  if (!inline) {
                    return (
                      <SyntaxHighlighter
                        style={tomorrow}
                        language={match ? match[1] : undefined}
                        PreTag="div"
                        customStyle={{ margin: 0, fontSize: '12px' }}
                        {...props}
                      >
                        {String(children).replace(/\n$/, '')}
                      </SyntaxHighlighter>
                    );
                  }
                  return <code className={className} {...props}>{children}</code>;
                }
              }}
            >
              {message.content}
            </ReactMarkdown>
          )}
        </div>
        
        {!isUser && (
          <>
            {message.semantic_sql && renderSemanticSQL(message.semantic_sql)}
            {message.sql_query && renderSQLQuery(message.sql_query)}
            {message.execution_result && renderExecutionResult(message.execution_result)}
            {renderDebugInfo()}
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
