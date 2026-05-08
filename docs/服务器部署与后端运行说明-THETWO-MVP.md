# 服务器部署与后端运行说明：THETWO MVP

## 文档信息

- 文档版本：`v0.2`
- 文档状态：`In Progress`
- 更新时间：`2026-05-08`
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

### 4.3 持久化与自启

当前已执行：

```bash
pm2 save
pm2 startup
```

## 5. 当前验证方式

### 5.1 进程状态

```bash
pm2 status
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

- 开发验证码
- mock 聊天回复
- SQLite 本地库
- `pm2` 常驻

当前未实现：

- 真实邮件验证码发送
- 真实模型服务
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
7. 真实模型服务接入
