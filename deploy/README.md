# Staging 部署说明

本目录提供 MVP 试点环境的最小部署产物：

- `env/staging.env.example`：后端运行环境变量样例
- `nginx/urban-agent.conf.template`：Nginx 静态站点和 API 反向代理模板
- `systemd/urban-agent-backend.service.template`：后端 systemd 服务模板
- `scripts/package-release.sh`：构建前后端并生成发布包
- `scripts/deploy-staging.sh`：部署到指定 staging 目录
- `scripts/start-backend.sh`：启动后端
- `scripts/stop-backend.sh`：停止后端
- `scripts/health-check.sh`：检查后端健康状态

## 1. 初始化 PostgreSQL

复制并编辑环境变量文件，至少修改 `DB_PASSWORD`：

```bash
cp deploy/env/staging.env.example .staging/config/staging.env
vim .staging/config/staging.env
```

初始化脚本会读取 `DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`，不会在 SQL 中固定数据库密码。

```bash
deploy/scripts/init-postgres.sh
```

默认会优先使用本机 `psql`，如果未安装但本机存在名为 `postgres-15` 的容器，则会自动回退到 `docker exec`。

## 2. 生成发布包

```bash
deploy/scripts/package-release.sh
```

默认输出到 `deploy/releases/<timestamp>/`。

## 3. 部署到 staging 目录

```bash
deploy/scripts/deploy-staging.sh
```

默认部署目录为 `.staging/`。可以通过环境变量覆盖：

```bash
STAGING_DIR=/opt/urban-agent-staging deploy/scripts/deploy-staging.sh
```

当 `SPRING_PROFILES_ACTIVE=postgres` 时，部署脚本会先执行数据库初始化，再启动后端并等待健康检查通过。
如果默认端口被其他应用占用，可以先修改 `config/staging.env` 中的 `SERVER_PORT`，部署脚本会按该端口启动，并在发现外部进程占用目标端口时直接失败。

## 4. 启动后端

```bash
STAGING_DIR=/opt/urban-agent-staging deploy/scripts/start-backend.sh
```

## 5. 检查健康状态

```bash
STAGING_DIR=/opt/urban-agent-staging deploy/scripts/health-check.sh
```

## 6. Nginx

渲染后的 Nginx 配置输出到：

```text
<staging-dir>/nginx/urban-agent.conf
```

将其链接或复制到 Nginx 配置目录后重载：

```bash
sudo ln -sf /opt/urban-agent-staging/nginx/urban-agent.conf /etc/nginx/conf.d/urban-agent.conf
sudo nginx -t
sudo systemctl reload nginx
```

## 7. Demo 数据

如需 staging 启动时自动灌入测试指标和知识文档，可在 `config/staging.env` 中设置：

```bash
URBAN_AGENT_BOOTSTRAP_DEMO_DATA_ENABLED=true
URBAN_AGENT_BOOTSTRAP_DEMO_DATA_RESET_ON_STARTUP=true
```
