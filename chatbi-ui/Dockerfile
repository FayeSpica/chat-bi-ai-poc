# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ENV HTTP_PROXY=${HTTP_PROXY} \
    http_proxy=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    https_proxy=${HTTPS_PROXY} \
    NO_PROXY=${NO_PROXY} \
    no_proxy=${NO_PROXY}

# 复制依赖文件
COPY package*.json ./

# 安装依赖
RUN npm install

# 复制源代码
COPY . .

# 构建静态资源
RUN npm run build

# 生产阶段
FROM nginx:alpine

# 安装 envsubst 工具
RUN apk add --no-cache gettext

# 复制构建的静态资源到nginx目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置文件
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制启动脚本
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# 设置默认环境变量
ENV CHATBI_SERVER_ENDPOINT=http://localhost:8000/api

# 暴露端口
EXPOSE 80

# 使用自定义启动脚本
ENTRYPOINT ["/docker-entrypoint.sh"]
