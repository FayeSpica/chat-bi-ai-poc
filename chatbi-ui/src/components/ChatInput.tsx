import React, { useState } from 'react';
import { Input, Button, Space, message } from 'antd';
import { SendOutlined, PlusOutlined } from '@ant-design/icons';

const { TextArea } = Input;

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  onClearChat: () => void;
  disabled?: boolean;
  placeholder?: string;
}

const ChatInput: React.FC<ChatInputProps> = ({
  onSendMessage,
  onClearChat,
  disabled = false,
  placeholder = "请输入您想要查询的问题，例如：查询所有用户的订单总金额"
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSend = async () => {
    if (!inputValue.trim()) {
      message.warning('请输入消息内容');
      return;
    }

    if (disabled) return;

    setIsLoading(true);
    try {
      await onSendMessage(inputValue.trim());
      setInputValue('');
    } catch (error) {
      message.error('发送消息失败');
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleClear = () => {
    if (disabled) return;
    onClearChat();
    setInputValue('');
  };

  return (
    <div className="chat-input-area">
      <Space direction="vertical" style={{ width: '100%' }}>
        <TextArea
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyPress={handleKeyPress}
          placeholder={placeholder}
          disabled={disabled}
          autoSize={{ minRows: 2, maxRows: 6 }}
          style={{ fontSize: '14px' }}
        />
        
        <Space style={{ justifyContent: 'flex-end', width: '100%' }}>
          <Button
            icon={<PlusOutlined />}
            onClick={handleClear}
            disabled={disabled}
          >
            新建会话
          </Button>
          
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={handleSend}
            loading={isLoading}
            disabled={disabled || !inputValue.trim()}
          >
            发送
          </Button>
        </Space>
      </Space>
    </div>
  );
};

export default ChatInput;
