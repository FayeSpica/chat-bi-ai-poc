#!/bin/sh
set -e

# 设置默认值
CHATBI_SERVER_ENDPOINT=${CHATBI_SERVER_ENDPOINT:-http://localhost:8000/api}

echo "Starting ChatBI UI with CHATBI_SERVER_ENDPOINT=${CHATBI_SERVER_ENDPOINT}"

# 替换 nginx 配置中的环境变量
envsubst '$CHATBI_SERVER_ENDPOINT' < /etc/nginx/conf.d/default.conf > /tmp/nginx.conf
mv /tmp/nginx.conf /etc/nginx/conf.d/default.conf

# 启动 nginx
exec nginx -g "daemon off;"
