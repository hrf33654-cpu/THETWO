# 服务器部署与后端运行说明：THETWO MVP

## 文档信息

- 文档版本：`v0.4`
- 文档状态：`In Progress`
- 更新时间：`2026-05-13`
- 关联后端目录：`backend/`

## 1. 文档目的

本文档只描述一件事：

- 当前最小后端如何在服务器上运行

它不代替 PRD，也不代替技术设计文档。

## 2. 当前服务器环境

当前已确认：

- 操作系统：`Ubuntu 22.04`
- Node：`24.x`
- PostgreSQL：`14`
- 进程托管：`pm2`
- 后端目录：`/home/ubuntu/backend`
- 公网联调地址：`http://111.231.14.253`
- 后端内部监听地址：`http://127.0.0.1:8787`

## 3. 当前后端目录结构

关键文件：

- `package.json`
- `tsconfig.json`
- `src/`
- `dist/`
- `.env`
- `.env.example`

关键目录：

- `data/`
  - `thetwo-dev.sqlite`

## 4. 当前后端运行方式

### 4.1 构建

```bash
cd /home/ubuntu/backend
npm install
npm run build
```

### 4.2 启动

当前启动命令：

```bash
pm2 start dist/index.js --name thetwo-backend
```

当前实际托管说明：

- `pm2` 当前运行在 `root` 用户下
- 建议使用以下命令排查：

```bash
sudo env PM2_HOME=/root/.pm2 pm2 status
sudo env PM2_HOME=/root/.pm2 pm2 logs thetwo-backend --lines 100
```

### 4.3 持久化与自启

当前已执行：

```bash
pm2 save
pm2 startup
```

## 5. 当前验证方式

### 5.1 进程状态

```bash
sudo env PM2_HOME=/root/.pm2 pm2 status
```

预期状态：

- `thetwo-backend` 显示为 `online`

### 5.2 健康检查

```bash
curl http://127.0.0.1:8787/health
```

预期结果：

- 返回 JSON
- `success=true`
- `status=ok`

### 5.3 Nginx 反向代理验证

```bash
curl http://127.0.0.1/health
curl http://111.231.14.253/health
```

预期结果：

- 两条命令都返回与后端一致的健康检查 JSON
- `http://111.231.14.253/` 返回 `THETWO gateway`

### 5.4 真实 LLM 联调验证

当前已完成：

- 服务器 `.env` 已配置：
  - `LLM_BASE_URL`
  - `LLM_API_KEY`
  - `LLM_MODEL`
  - `LLM_TIMEOUT_MS`
- 已通过全新测试账号验证：
  - `POST /auth/request-code`
  - `POST /auth/verify-code`
  - `PUT /me/companion-profile`
  - `POST /chat/send`
- `/chat/send` 已返回真实模型回复，不再返回旧占位文案

当前建议新增验证：

```bash
cd backend
npm run probe:llm
```

预期结果：

- 成功时输出 `HTTP 200`
- 失败时输出 `Probe diagnosis`
- 重点看分类是否为：
  - `auth_failed`
  - `rate_limited_or_quota`
  - `model_not_found`
  - `upstream_unavailable`
  - `unknown_failure`

这一步用于先确认 `LLM_BASE_URL / LLM_API_KEY / LLM_MODEL` 是否真的匹配当前供应商套餐，而不是只根据聊天页报错猜测。

## 6. 当前后端接口范围

已实现接口：

- `GET /health`
- `POST /auth/request-code`
- `POST /auth/verify-code`
- `GET /me`
- `PUT /me/companion-profile`
- `GET /me/companion-profile`
- `POST /chat/send`
- `GET /chat/history`
- `DELETE /chat/history`
- `PUT /me/recent-capture`
- `GET /me/recent-capture`
- `DELETE /me/recent-capture`
- `DELETE /me`

## 7. 当前实现边界

当前已实现：

- SMTP / 真实邮件验证码发送代码基础设施（`EMAIL_MODE=smtp` 时启用）
- 真实 LLM 调用链路
- 真实 LLM 线上环境变量配置与公网联调
- SQLite 本地库
- `pm2` 常驻

当前未实现：

- 真实邮件验证码发送
- 域名
- HTTPS

## 8. 当前数据库说明

当前服务器已安装并创建：

- PostgreSQL 14
- 用户：`thetwo`
- 数据库：`thetwo`

但当前后端代码实际运行仍使用：

- `SQLite`
- 文件位置：`/home/ubuntu/backend/data/thetwo-dev.sqlite`

因此当前状态是：

- `PostgreSQL` 已准备好，但尚未切换为当前后端主存储
- `SQLite` 仍是当前运行中的数据存储

## 9. 当前限制

- 当前主要通过 IP 联调
- 客户端下一阶段需要先允许 Debug HTTP
- 当前 `.env` 仍是开发环境配置
- 生产级日志、备份、监控还未完成
- 当前公网入口依赖 `Nginx -> 127.0.0.1:8787` 反向代理，不建议客户端直接使用内部端口

## 10. 后续运维待办

后续建议按这个顺序推进：

1. 域名解析
2. HTTPS 证书
3. 后端日志整理
4. 从 SQLite 迁移到 PostgreSQL
5. 数据库备份策略
6. 真实邮件服务接入
7. 将 `pm2` 托管用户从 `root` 统一迁移到 `ubuntu`
8. 完成公网真机持续回归与故障日志整理
