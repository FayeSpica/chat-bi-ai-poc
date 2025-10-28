# ChatBI UI

ChatBI 前端界面，支持通过环境变量配置后端服务地址。

## 环境变量配置

### 本地开发

在项目根目录创建 `.env` 文件：

```bash
# 本地开发环境
VITE_CHATBI_SERVER_ENDPOINT=http://localhost:8000/api
```

### Docker 环境

#### 方式一：通过环境变量文件

1. 在项目根目录创建 `.env` 文件：
```bash
# Docker 环境
CHATBI_SERVER_ENDPOINT=http://chatbi-server-java:8000/api
```

2. 使用 docker-compose 启动：
```bash
docker-compose up --build
```

#### 方式二：通过构建参数

```bash
docker build --build-arg VITE_CHATBI_SERVER_ENDPOINT=http://your-server:8000/api -t chatbi-ui .
```

#### 方式三：通过环境变量

```bash
export CHATBI_SERVER_ENDPOINT=http://your-server:8000/api
docker-compose up --build
```

## 开发命令

```bash
# 安装依赖
npm install

# 本地开发
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

## 配置说明

- `VITE_CHATBI_SERVER_ENDPOINT`: 后端 API 服务地址
- 默认值: `http://localhost:8000/api`
- 在 Docker 环境中，建议使用容器名作为主机名（如 `chatbi-server-java:8000`）

## 部署路径

前端应用部署在 `/copilot` 路径下，访问地址为 `http://your-domain/copilot/`
