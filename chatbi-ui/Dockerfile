# 构建阶段
FROM node:18-alpine AS builder

WORKDIR /app

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY
ARG VITE_CHATBI_SERVER_ENDPOINT
ENV HTTP_PROXY=${HTTP_PROXY} \
    http_proxy=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    https_proxy=${HTTPS_PROXY} \
    NO_PROXY=${NO_PROXY} \
    no_proxy=${NO_PROXY} \
    VITE_CHATBI_SERVER_ENDPOINT=${VITE_CHATBI_SERVER_ENDPOINT}

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

# 复制构建的静态资源到nginx目录
COPY --from=builder /app/dist /usr/share/nginx/html

# 复制nginx配置文件（可选，使用默认配置）
# COPY nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
