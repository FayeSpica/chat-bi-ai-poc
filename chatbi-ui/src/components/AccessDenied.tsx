import React from 'react';
import { Result, Button, Typography, Space } from 'antd';
import { LockOutlined, HomeOutlined } from '@ant-design/icons';

const { Title, Paragraph, Text } = Typography;

interface AccessDeniedProps {
  status?: 401 | 403;
  onRetry?: () => void;
}

const AccessDenied: React.FC<AccessDeniedProps> = ({ status = 403, onRetry }) => {
  const isUnauthorized = status === 401;

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: '20px'
    }}>
      <Result
        icon={<LockOutlined style={{ fontSize: '72px', color: '#ff4d4f' }} />}
        status="error"
        title={
          <Title level={2} style={{ color: '#fff', marginTop: '16px' }}>
            {isUnauthorized ? '未授权访问' : '访问被拒绝'}
          </Title>
        }
        subTitle={
          <Space direction="vertical" size="large" style={{ marginTop: '24px', textAlign: 'center' }}>
            <Paragraph style={{ color: '#fff', fontSize: '16px', margin: 0 }}>
              {isUnauthorized 
                ? '您的登录凭证已过期或无效，请重新登录' 
                : '您没有权限访问此资源，请联系管理员'}
            </Paragraph>
            <div style={{ 
              background: 'rgba(255, 255, 255, 0.1)', 
              padding: '20px', 
              borderRadius: '8px',
              backdropFilter: 'blur(10px)'
            }}>
              <Text style={{ color: '#fff', fontSize: '14px' }}>
                <strong>错误代码：</strong> {status}
              </Text>
              <br />
              <Text style={{ color: '#fff', fontSize: '14px', marginTop: '8px', display: 'inline-block' }}>
                {isUnauthorized 
                  ? '• 请检查您的登录状态' 
                  : '• 您的账户可能不在访问白名单中'}
              </Text>
            </div>
          </Space>
        }
        extra={[
          <Button 
            type="primary" 
            size="large"
            icon={<HomeOutlined />}
            onClick={() => {
              window.location.reload();
            }}
            style={{ marginRight: '16px' }}
          >
            刷新页面
          </Button>,
          onRetry && (
            <Button 
              size="large"
              onClick={onRetry}
            >
              重试
            </Button>
          )
        ].filter(Boolean)}
      />
    </div>
  );
};

export default AccessDenied;

