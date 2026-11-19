FROM node:24-alpine

WORKDIR /app

ENV NODE_ENV=production \
    PORT=3000 \
    HOST=0.0.0.0 \
    DB_PATH=/app/data/calendar.db \
    BACKUP_DIR=/app/data/backups \
    TRUST_PROXY=true \
    COOKIE_SECURE=auto \
    TZ=Asia/Shanghai

# 安装生产依赖（仅纯 JS 依赖，无需 C++ 编译链）
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

# 复制应用代码（前端静态资源 + Node 后端）
COPY index.html manifest.json ./
COPY css ./css
COPY icons ./icons
COPY js ./js
COPY server ./server

# 准备 SQLite 持久化数据目录并切换非 root 用户运行
RUN mkdir -p /app/data/backups && chown -R node:node /app/data

USER node

EXPOSE 3000

VOLUME ["/app/data"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/healthz || exit 1

CMD ["node", "server/index.js"]
