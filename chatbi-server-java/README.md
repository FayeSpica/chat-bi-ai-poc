# ChatBI Server Java

基于Java 21、Spring Boot和LangChain4j的ChatBI服务器，提供自然语言转SQL的聊天BI功能。

## 技术栈

- **Java 21**: 使用最新的Java LTS版本
- **Spring Boot 3.2.0**: 现代化的Java框架
- **LangChain4j 0.29.1**: 用于与Ollama集成的AI框架
- **MySQL**: 数据库支持
- **Maven**: 项目构建工具

## 功能特性

- 自然语言转语义SQL
- 多数据库连接管理
- 数据库元数据构建
- RESTful API接口
- 会话历史管理
- 数据库管理后台

## 快速开始

### 环境要求

- Java 21+
- Maven 3.6+
- MySQL 8.0+
- Ollama (用于AI模型)

### 配置

1. 复制 `src/main/resources/application.yml` 并根据需要修改配置
2. 确保Ollama服务正在运行
3. 配置数据库连接信息

### 运行

```bash
# 使用Maven运行
./mvnw spring-boot:run

# 或者构建后运行
./mvnw clean package
java -jar target/chatbi-server-java-1.0.0.jar
```

### Docker运行

```bash
# 构建镜像
docker build -t chatbi-server-java .

# 运行容器
docker run -p 8000:8000 chatbi-server-java
```

## API接口

### 聊天接口
- `POST /api/chat` - 发送聊天消息
- `POST /api/execute-sql` - 执行SQL查询
- `GET /api/conversation/{id}` - 获取会话历史
- `DELETE /api/conversation/{id}` - 清除会话历史

### 数据库接口
- `GET /api/database/tables` - 获取所有表
- `GET /api/database/tables/{name}/schema` - 获取表结构
- `GET /api/database/schema` - 获取完整数据库结构
- `GET /api/metadata` - 获取增强元数据

### 数据库管理接口
- `GET /api/admin/databases` - 获取所有数据库连接
- `POST /api/admin/databases` - 创建数据库连接
- `PUT /api/admin/databases/{id}` - 更新数据库连接
- `DELETE /api/admin/databases/{id}` - 删除数据库连接
- `POST /api/admin/databases/test` - 测试数据库连接

## 配置说明

### application.yml

```yaml
server:
  port: 8000

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test_db
    username: root
    password: password

chatbi:
  database:
    host: localhost
    port: 3306
    name: test_db
    username: root
    password: password
  ollama:
    base-url: http://192.168.31.230:11434
    model: qwen2.5:7b
    timeout: 120

langchain4j:
  ollama:
    base-url: http://192.168.31.230:11434
    model-name: qwen2.5:7b
    timeout: 120s
    temperature: 0.1
```

## 开发说明

### 项目结构

```
src/main/java/com/chatbi/
├── ChatbiServerJavaApplication.java    # 主应用类
├── config/                            # 配置类
│   ├── ChatbiProperties.java         # 自定义配置属性
│   └── WebConfig.java                # Web配置
├── controller/                        # REST控制器
│   ├── ChatController.java           # 聊天相关接口
│   └── DatabaseAdminController.java  # 数据库管理接口
├── model/                            # 数据模型
│   ├── ChatRequest.java             # 聊天请求
│   ├── ChatResponse.java            # 聊天响应
│   ├── SemanticSQL.java             # 语义SQL
│   └── ...                          # 其他模型
├── repository/                       # 数据访问层
│   └── DatabaseConnectionRepository.java
└── service/                          # 业务服务层
    ├── ChatService.java             # 聊天服务
    ├── DatabaseManager.java         # 数据库管理
    ├── SemanticSQLConverter.java    # SQL转换器
    └── ...                          # 其他服务
```

### 主要组件

1. **ChatService**: 处理聊天消息和会话管理
2. **SemanticSQLConverter**: 使用LangChain4j将自然语言转换为语义SQL
3. **MySQLSQLGenerator**: 将语义SQL转换为MySQL SQL语句
4. **DatabaseManager**: 数据库操作管理
5. **SchemaMetadataBuilder**: 构建数据库元数据

## 与Python版本的对比

| 特性 | Python版本 | Java版本 |
|------|------------|----------|
| 框架 | FastAPI | Spring Boot |
| AI集成 | LangChain | LangChain4j |
| 数据库 | PyMySQL | Spring JDBC |
| 配置 | Pydantic | Spring Configuration |
| 类型安全 | 动态类型 | 静态类型 |
| 性能 | 中等 | 高 |
| 生态 | 丰富 | 企业级 |

## 许可证

本项目采用Apache 2.0许可证。