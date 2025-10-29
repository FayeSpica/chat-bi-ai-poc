import React, { useEffect, useState } from 'react';
import { List, Button, Input, Modal, Space, Typography } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { PersistedChatSession } from '../types';
import { sessionAPI } from '../services/api';

interface SessionListProps {
  selectedSessionId?: number | null;
  onSelect: (sessionId: number) => void;
  canDelete?: boolean;
}

const SessionList: React.FC<SessionListProps> = ({ selectedSessionId, onSelect, canDelete }) => {
  const [sessions, setSessions] = useState<PersistedChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [renaming, setRenaming] = useState<PersistedChatSession | null>(null);
  const [newTitle, setNewTitle] = useState('');

  const loadSessions = async () => {
    setLoading(true);
    try {
      const list = await sessionAPI.listSessions();
      setSessions(list);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSessions();
  }, []);

  const handleCreate = async () => {
    const created = await sessionAPI.createSession('新的会话');
    setSessions(prev => [created, ...prev]);
    onSelect(created.id);
  };

  const handleRename = async () => {
    if (!renaming) return;
    const updated = await sessionAPI.renameSession(renaming.id, newTitle || renaming.title || '会话');
    setSessions(prev => prev.map(s => (s.id === updated.id ? updated : s)));
    setRenaming(null);
    setNewTitle('');
  };

  const handleDelete = async (id: number) => {
    await sessionAPI.deleteSession(id);
    setSessions(prev => prev.filter(s => s.id !== id));
  };

  return (
    <div style={{ padding: 16 }}>
      <Space style={{ marginBottom: 12 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建会话</Button>
      </Space>
      <List
        loading={loading}
        dataSource={sessions}
        renderItem={(item) => (
          <List.Item
            style={{ cursor: 'pointer', background: selectedSessionId === item.id ? '#e6f7ff' : undefined }}
            onClick={() => onSelect(item.id)}
            actions={[
              <Button key="rename" type="text" size="small" icon={<EditOutlined />} onClick={(e) => { e.stopPropagation(); setRenaming(item); setNewTitle(item.title || ''); }} />,
              canDelete ? (
                <Button key="delete" type="text" size="small" danger icon={<DeleteOutlined />} onClick={(e) => { e.stopPropagation(); handleDelete(item.id); }} />
              ) : null,
            ].filter(Boolean) as any}
          >
            <List.Item.Meta
              title={<Typography.Text ellipsis>{item.title || '未命名会话'}</Typography.Text>}
              description={new Date(item.updatedAt).toLocaleString()}
            />
          </List.Item>
        )}
      />

      <Modal
        title="重命名会话"
        open={!!renaming}
        onOk={handleRename}
        onCancel={() => { setRenaming(null); setNewTitle(''); }}
      >
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          placeholder="请输入会话标题"
          maxLength={50}
        />
      </Modal>
    </div>
  );
};

export default SessionList;


