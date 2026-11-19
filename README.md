# 工作日程表（云端多账号版）

一套面向个人与小团队的**云端工作日程管理工具**（Node.js 24 + Express + SQLite + 纯 HTML/CSS/JS 前端），支持电脑与手机跨设备实时同步、多账号数据隔离、管理员后台、私有 `.ics` 日历订阅以及 Docker 一键部署（原生适配 Cloudflare）。

## 核心功能

- **账号认证与多租户隔离**：账号 ID + 密码登录（`scrypt` 加盐哈希 + `HttpOnly` 长期会话 Cookie + 登录限流），数据存储于服务端 SQLite 并按 `user_id` 严格隔离；不开放公网自由注册，由管理员统一开号。
- **管理员后台**：
  - **账号管理**：新建账号（支持自动生成强密码并一键复制）、启用/禁用账号、一键重置密码（立即下线旧会话）、删除账号。
  - **节假日维护**：内置 2024–2027 年国务院放假调休安排；支持按年份从开源数据源（`NateScarlet/holiday-cn`）一键拉取入库并自动缓存，同时支持管理员手工增删改单日「休/班」；每年 11–12 月自动检查同步次年数据。
  - **数据库备份**：每日自动执行在线热备份（WAL 安全快照，默认保留 14 份），支持在后台立即创建备份或一键下载 `.db` 快照。
- **月 / 周 / 年三视图与搜索筛选**：周一为每周首日，支持月视图跨天整体围合渲染、重叠泳道避让、农历与二十四节气、关键字即时搜索直达日程、图例栏点击多选类别筛选，以及单页月视图打印。
- **误删保护**：删除单条日程、删除类别（提示关联日程数）、清空全部日程均带二次确认保护。
- **《评估项目台账》导入**：浏览器端离线解析 `.xlsx` / `.csv` 与 Excel 粘贴内容，导入前预览确认与阶段映射，批量写入云端时按台账键去重更新、绝不覆盖手工日程。
- **个人日历订阅与数据迁移**：每个账号拥有专属高熵 `.ics` 订阅链接（供 iPhone / 安卓 / Outlook 自动同步，支持随时重置），支持 JSON 全量备份恢复，以及旧版单机 `localStorage` 数据一键云端迁移。

## 本地运行（Node.js >= 22.13 / 24）

```bash
npm install
ADMIN_USERNAME=admin ADMIN_PASSWORD=your_strong_password npm start
```

启动后访问 `http://127.0.0.1:3000`。若未指定 `ADMIN_PASSWORD`，首次启动会自动生成随机强密码并打印到控制台。

## Docker + Cloudflare 公网部署

### 1. 准备配置并启动容器

```bash
cp .env.example .env
# 编辑 .env，建议设置 ADMIN_PASSWORD（或留空后查看 docker logs 获取初始密码）
docker compose up -d --build
```

查看首次初始化的管理员密码与运行日志：

```bash
docker compose logs -f ryan-calendar
```

SQLite 主库及每日自动备份持久化保存在宿主机 `./data/` 目录中。

### 2. 接入 Cloudflare（推荐两种方式二选一）

#### 方式 A：Cloudflare Tunnel（`cloudflared`，最省心、无需开放服务器入站端口）
1. 在 Cloudflare Zero Trust 控制台创建 Tunnel，将公网域名（如 `cal.example.com`）指向 `http://127.0.0.1:3000`（若 `cloudflared` 同在 Docker 网络中，则指向 `http://ryan-calendar:3000`）。
2. 保持 `.env` 中 `TRUST_PROXY=true` 和 `COOKIE_SECURE=auto`，服务会自动识别 Cloudflare 转发的真实客户端 IP（`CF-Connecting-IP`）和 HTTPS 协议。

#### 方式 B：Cloudflare DNS 代理 + Nginx / Caddy 反代
在反代中将流量转发至 `http://127.0.0.1:3000` 并透传 `X-Forwarded-Proto` 与 `X-Forwarded-For`；Cloudflare SSL 模式建议使用 **Full (strict)**。

> 本服务已对所有 `/api/*` 与 `/ics/*` 响应默认附加 `Cache-Control: no-store`，防止 Cloudflare 误缓存私密日程数据，同时严格采用静态资源白名单托管，不会暴露 `server/`、`data/` 或 `.env` 文件。
