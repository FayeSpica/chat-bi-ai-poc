# ChatBI - 智能聊天BI系统

一个将自然语言转换为语义SQL与可执行SQL的智能聊天式 BI 系统，基于 FastAPI + React + Ollama + MySQL 构建。

## 🚀 亮点功能

- **自然语言到语义SQL与可执行SQL**：解析中文问题，生成结构化语义SQL与 MySQL 语句
- **自动/手动执行查询**：助手回复后自动执行生成的 SQL，同时保留“执行查询”手动按钮
- **结果可视化**：除表格外，支持折线图、柱状图、条形图、饼状图，支持动态选择 X/Y/分组 字段
- **美观的 SQL 展示**：前端以 Markdown + 代码高亮展示 ```sql 代码块
- **会话与调试信息**：每轮对话可展开“调试信息”，包含：
  - 请求参数（前端→后端）
  - 模型响应（后端→前端）
  - Ollama 调试（提供方/模型/Prompt/原始回复/错误）
  - SQL 执行结果（成功/失败、行数、错误信息）
- **数据库结构与增强元数据**：
  - 左侧树形结构展示表与字段（含主键/唯一/非空等）
  - 新增增强元数据接口，包含表注释、字段注释、字段样例值、表样例行
  - 提供更“可读”的元数据摘要给 LLM，提升 SQL 生成正确性
- **SQL 生成修复（JOIN）**：优化 JOIN 语句生成，避免 `FROM a, b INNER JOIN b ...` 的冗余与错误
- **UTF-8 完整链路**：数据库、连接、容器均默认使用 utf8mb4，确保中文不乱码

## 🏗️ 架构与目录

```
chat-bi-ai-poc/
├── chatbi-server-java/           # 后端 API 服务 (Java Spring Boot)
│   ├── src/main/java/com/chatbi/
│   │   ├── controller/           # REST 控制器
│   │   ├── service/              # 业务服务层
│   │   ├── model/                # 数据模型
│   │   └── repository/           # 数据访问层
│   ├── src/main/resources/
│   │   └── application.yml       # 应用配置
│   ├── pom.xml                   # Maven 依赖
│   └── Dockerfile
├── copilot/
│   └── chatbi-ui/                # 前端 UI (React + TS + AntD)
│       ├── src/
│       │   ├── components/
│       │   │   ├── ChatMessage.tsx   # 消息与调试面板、Markdown/代码高亮、图表
│       │   │   └── DatabaseSchema.tsx# 数据库结构树
│       │   ├── services/api.ts       # API 封装（支持环境变量配置）
│       │   ├── types/                # TS 类型定义
│       │   └── App.tsx               # 页面布局、自动执行 SQL
│       ├── package.json
│       └── Dockerfile
├── docker-compose.yml            # 容器编排（MySQL/Server/UI）
└── README.md
```

## 🛠️ 技术栈

- 后端：Java Spring Boot, LangChain4j (Ollama), MySQL Connector
- 前端：React 18, TypeScript, Ant Design, @ant-design/plots, react-markdown, react-syntax-highlighter, Axios, Vite
- 数据库：MySQL 8.0（utf8mb4）
- 部署：Docker Compose

## 📦 快速开始（Docker Compose 推荐）

1) 克隆项目
```bash
git clone <repository-url>
cd chat-bi-ai-poc
```

2) 启动/重建所有服务（会初始化数据库并套用 COMMENT/utf8mb4）
```bash
docker compose up -d --build
```

3) 准备 Ollama（本地或远程）
- 确保有可用的 Ollama 服务，并在 docker-compose.yml 的 `chatbi-server` 环境变量中配置：
  - `OLLAMA_BASE_URL`
  - `OLLAMA_MODEL`（例如 `qwen2.5:7b`）

4) 访问
- 前端：`http://localhost:3000`
- 后端：`http://localhost:8000`
- API 文档：`http://localhost:8000/docs`

## 💬 使用指南

- 在输入框中输入自然语言问题，发送后：
  - 助手将以 Markdown 格式回复，内含 ```sql 代码块与语义信息
  - 系统会自动执行 SQL，并在消息下方展示表格或图表
  - 你也可以点击“执行查询”按钮手动执行
- 结果视图切换：在结果卡片上方的 Segmented 控件切换 表格/折线/柱状/条形/饼状，并通过下拉选择 X/Y/分组 字段
- 调试信息：展开“调试信息”查看本轮请求/响应、Ollama 调试（Prompt/模型原始回复）、SQL 执行结果

## 🔌 API 一览

- 健康检查
  - `GET /health`
- 聊天
  - `POST /api/chat` → ChatResponse（含 semantic_sql, sql_query, debug_ollama 等）
- 执行 SQL
  - `POST /api/execute-sql` → SQLExecutionResponse（success/data/error/row_count）
- 会话
  - `GET /api/conversation/{conversation_id}` → 历史
  - `DELETE /api/conversation/{conversation_id}` → 清空
- 数据库结构（简化）
  - `GET /api/database/schema`
- 增强元数据（推荐给 LLM 的详细元数据）
  - `GET /api/metadata` →
    ```json
    {
      "metadata": {
        "db": {"host": "...", "name": "..."},
        "tables": {
          "orders": {
            "comment": "订单明细表",
            "columns": [
              {"name": "id", "type": "int", "comment": "订单ID，主键", "samples": [1, 2, ...]},
              {"name": "user_id", "type": "int", "comment": "下单用户ID...", "samples": [1, 2]}
            ],
            "samples": [{"id":1, "user_id":1, "product_name":"iPhone 15", ...}]
          }
        }
      }
    }
    ```

## ⚙️ 配置

环境变量（部分）：

| 变量 | 说明 | 默认 |
|---|---|---|
| OLLAMA_BASE_URL | Ollama 服务地址 | http://localhost:11434 |
| OLLAMA_MODEL | 模型名称 | qwen2.5:7b（示例） |
| DB_HOST/PORT/USER/PASSWORD/NAME | 数据库连接 | mysql:3306/root/password/test_db |
| API_HOST/API_PORT | 后端服务监听 | 0.0.0.0/8000 |
| VITE_API_BASE_URL | 前端API基础地址 | http://localhost:8000/api |

### 前端环境变量配置

前端支持通过环境变量 `VITE_API_BASE_URL` 配置后端API地址，默认值为 `http://localhost:8000/api`。

在 `docker-compose.yml` 中可以通过以下方式配置：
```yaml
environment:
  - VITE_API_BASE_URL=http://your-backend-host:8000/api
```

或者在本地开发时创建 `.env` 文件：
```bash
VITE_API_BASE_URL=http://localhost:8000/api
```

## 🧠 SQL 生成与修复说明

- 语义SQL → MySQL：
  - SELECT：聚合/列映射
  - FROM：当存在 JOIN 时，仅包含首个基础表
  - JOIN：按 `joins[]` 构建规范的 `INNER/LEFT ... JOIN ... ON ...`
  - WHERE/GROUP BY/ORDER BY/LIMIT：按语义SQL拼接
- 修复：避免 `FROM users, orders INNER JOIN orders ...` 的重复表问题，示例：
  - 输入：
    ```json
    {
      "tables": ["users", "orders"],
      "columns": ["users.id", "SUM(orders.amount) as total_amount"],
      "joins": [{"type": "INNER", "table1": "users", "table2": "orders", "condition": "users.id = orders.user_id"}],
      "group_by": ["users.id"]
    }
    ```
  - 生成：
    ```sql
    SELECT users.id, SUM(orders.amount) AS total_amount
    FROM users
    INNER JOIN orders ON users.id = orders.user_id
    GROUP BY users.id
    ```

## 🔤 中文编码与注释

- MySQL 容器参数已设置：`utf8mb4`/`utf8mb4_unicode_ci` 并跳过客户端握手字符集
- 连接层：`charset='utf8mb4', use_unicode=True, init_command='SET NAMES utf8mb4'`
- 初始化脚本：`init.sql` 使用 utf8mb4，并为所有表/字段添加了中文 COMMENT
- 如需重建并套用：
```bash
docker compose down -v && docker compose up -d --build
```

## 🐞 常见问题排查

- 仍出现中文乱码：
  - 执行 `SHOW VARIABLES LIKE 'character_set_%';` 与 `collation_%` 检查是否为 utf8mb4
  - 使用上面的重建命令，或对既有表执行 `ALTER TABLE ... CONVERT TO CHARACTER SET utf8mb4 ...`
- 前端依赖缺失/类型报错：
  - 进入 `chatbi-ui` 目录执行 `npm install`
- Ollama 无法访问：
  - 检查 `OLLAMA_BASE_URL` 可达、模型是否已 `ollama pull`

## 🤝 贡献

欢迎提交 Issue / PR 改进 SQL 生成、可视化和元数据质量。

## 📄 许可证

MIT
