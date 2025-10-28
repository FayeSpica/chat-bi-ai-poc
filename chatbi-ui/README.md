# ChatBI UI

ChatBI 前端界面，支持通过运行时环境变量配置后端服务地址。

## 特性

- 🚀 **运行时配置**: 支持在 Docker 容器启动时通过环境变量配置后端服务地址
- 🔧 **无需重新构建**: 修改后端地址无需重新构建前端镜像
- 📦 **Docker 优化**: 使用 nginx 的 sub_filter 模块实现运行时配置注入
- 🛠️ **开发友好**: 支持本地开发和 Docker 环境的不同配置

## 环境变量配置

### 本地开发

在项目根目录创建 `.env` 文件：

```bash
# 本地开发环境
VITE_CHATBI_SERVER_ENDPOINT=http://localhost:8000/api
```

### Docker 环境

#### 方式一：通过环境变量文件（推荐）

1. 在项目根目录创建 `.env` 文件：
```bash
# Docker 环境
CHATBI_SERVER_ENDPOINT=http://chatbi-server-java:8000/api
```

2. 使用 docker-compose 启动：
```bash
docker-compose up --build
```

#### 方式二：通过环境变量

```bash
export CHATBI_SERVER_ENDPOINT=http://your-server:8000/api
docker-compose up --build
```

#### 方式三：通过 Docker 运行参数

```bash
docker run -e CHATBI_SERVER_ENDPOINT=http://your-server:8000/api -p 3000:80 chatbi-ui
```

## 工作原理

1. **构建时**: 前端代码使用占位符 `__CHATBI_SERVER_ENDPOINT__` 作为 API 地址
2. **运行时**: nginx 的 `sub_filter` 模块将占位符替换为实际的环境变量值
3. **启动时**: Docker 容器启动脚本处理环境变量并更新 nginx 配置

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

- `CHATBI_SERVER_ENDPOINT`: 后端 API 服务地址
- 默认值: `http://localhost:8000/api`
- 在 Docker 环境中，建议使用容器名作为主机名（如 `chatbi-server-java:8000`）

## 部署路径

前端应用部署在 `/copilot` 路径下，访问地址为 `http://your-domain/copilot/`

## 技术实现

- **构建工具**: Vite
- **Web 服务器**: nginx
- **配置注入**: nginx sub_filter 模块
- **环境变量处理**: envsubst 工具
