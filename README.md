# ChatBI - 智能聊天BI系统

一个基于自然语言转语义SQL的智能聊天BI系统，使用Python/LangChain/FastAPI/Ollama等现代技术栈构建，支持前后端分离架构。

## 🚀 功能特性

- **自然语言理解**: 将用户的自然语言查询转换为结构化的语义SQL
- **智能SQL生成**: 基于语义SQL自动生成MySQL查询语句
- **实时查询执行**: 支持SQL查询的执行和结果展示
- **数据库结构展示**: 可视化展示数据库表结构，便于用户理解
- **会话管理**: 支持多轮对话，保持上下文
- **现代化UI**: 基于React + Ant Design的响应式界面

## 🏗️ 系统架构

```
chat-bi-ai-poc/
├── chatbi-server/          # 后端API服务
│   ├── app/                # 应用核心代码
│   │   ├── main.py         # FastAPI主应用
│   │   ├── config.py       # 配置管理
│   │   ├── models.py       # 数据模型
│   │   ├── semantic_sql_converter.py  # 语义SQL转换器
│   │   ├── database.py     # 数据库管理
│   │   └── chat_service.py # 聊天服务
│   ├── requirements.txt    # Python依赖
│   ├── Dockerfile         # Docker配置
│   └── run.py            # 启动脚本
├── chatbi-ui/             # 前端UI应用
│   ├── src/               # 源代码
│   │   ├── components/    # React组件
│   │   ├── services/      # API服务
│   │   ├── types/         # TypeScript类型
│   │   └── App.tsx        # 主应用组件
│   ├── package.json       # Node.js依赖
│   └── Dockerfile        # Docker配置
└── docker-compose.yml     # 容器编排配置
```

## 🛠️ 技术栈

### 后端
- **FastAPI**: 现代高性能Web框架
- **LangChain**: 大语言模型应用开发框架
- **Ollama**: 本地LLM服务
- **PyMySQL**: MySQL数据库连接器
- **Pydantic**: 数据验证和序列化
- **Uvicorn**: ASGI服务器

### 前端
- **React 18**: 用户界面库
- **TypeScript**: 类型安全的JavaScript
- **Ant Design**: 企业级UI组件库
- **Axios**: HTTP客户端
- **React Syntax Highlighter**: 代码高亮
- **Vite**: 现代构建工具

### 数据库
- **MySQL 8.0**: 关系型数据库
- **Docker**: 容器化部署

## 📦 快速开始

### 方式一：Docker Compose（推荐）

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd chat-bi-ai-poc
   ```

2. **启动所有服务**
   ```bash
   docker-compose up -d
   ```

3. **下载Ollama模型**
   ```bash
   # 进入Ollama容器
   docker exec -it chatbi-ollama bash
   
   # 下载llama2模型（需要一些时间）
   ollama pull llama2
   ```

4. **访问应用**
   - 前端界面: http://localhost:3000
   - 后端API: http://localhost:8000
   - API文档: http://localhost:8000/docs

### 方式二：本地开发

#### 环境要求
- Python 3.11+
- Node.js 18+
- MySQL 8.0+
- Ollama

#### 后端启动

1. **安装Python依赖**
   ```bash
   cd chatbi-server
   pip install -r requirements.txt
   ```

2. **配置环境变量**
   ```bash
   cp env.example .env
   # 编辑.env文件，配置数据库和Ollama连接信息
   ```

3. **初始化数据库**
   ```bash
   python setup_database.py
   ```

4. **启动Ollama服务**
   ```bash
   ollama serve
   # 在另一个终端下载模型
   ollama pull llama2
   ```

5. **启动后端服务**
   ```bash
   python run.py
   ```

#### 前端启动

1. **安装Node.js依赖**
   ```bash
   cd chatbi-ui
   npm install
   ```

2. **启动开发服务器**
   ```bash
   npm run dev
   ```

## 🎯 使用示例

### 自然语言查询示例

1. **基础查询**
   - "查询所有用户信息"
   - "显示所有订单"

2. **条件查询**
   - "查询北京的用户的订单"
   - "找出金额大于5000的订单"

3. **聚合查询**
   - "统计每个用户的订单总金额"
   - "计算每个月的销售数量"

4. **复杂查询**
   - "查询购买最多的前10个商品"
   - "找出每个城市的平均订单金额"

### API接口

#### 发送聊天消息
```bash
POST /api/chat
Content-Type: application/json

{
  "message": "查询所有用户的订单总金额",
  "conversation_id": "optional-conversation-id"
}
```

#### 执行SQL查询
```bash
POST /api/execute-sql
Content-Type: application/json

{
  "sql_query": "SELECT u.name, SUM(o.amount) as total FROM users u JOIN orders o ON u.id = o.user_id GROUP BY u.id",
  "conversation_id": "optional-conversation-id"
}
```

## 🔧 配置说明

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `OLLAMA_BASE_URL` | Ollama服务地址 | `http://localhost:11434` |
| `OLLAMA_MODEL` | 使用的模型名称 | `llama2` |
| `DB_HOST` | 数据库主机 | `localhost` |
| `DB_PORT` | 数据库端口 | `3306` |
| `DB_USER` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | `password` |
| `DB_NAME` | 数据库名称 | `test_db` |
| `API_HOST` | API服务主机 | `0.0.0.0` |
| `API_PORT` | API服务端口 | `8000` |

### 数据库结构

系统包含三个示例表：

- **users**: 用户信息表
- **orders**: 订单信息表  
- **products**: 商品信息表

## 🚨 注意事项

1. **模型下载**: 首次使用需要下载Ollama模型，可能需要较长时间
2. **内存要求**: Ollama模型运行需要足够的内存（建议8GB+）
3. **网络连接**: 确保Ollama服务可以正常访问
4. **数据库权限**: 确保数据库用户有创建表和插入数据的权限

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [LangChain](https://github.com/langchain-ai/langchain) - 大语言模型应用开发框架
- [Ollama](https://github.com/ollama/ollama) - 本地LLM服务
- [FastAPI](https://github.com/tiangolo/fastapi) - 现代Web框架
- [Ant Design](https://github.com/ant-design/ant-design) - 企业级UI组件库

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交 Issue
- 发送邮件
- 创建 Pull Request

---

**ChatBI** - 让数据分析变得简单自然 🚀
