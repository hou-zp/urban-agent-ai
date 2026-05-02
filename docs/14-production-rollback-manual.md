# 生产部署回滚手册

## 1. 适用范围

本手册用于城管 AI 智能体平台在 `staging / prod` 环境的发布失败、配置误发、数据库迁移异常、健康检查失败等场景。

适用对象：

- 运维值班人员
- 发布负责人
- 后端研发值班人员

本手册覆盖四类回滚动作：

1. **版本回退**
2. **数据库备份与恢复**
3. **配置回滚**
4. **健康检查与验收**

## 2. 回滚触发条件

满足以下任一条件，应立即暂停继续发布，并评估是否进入回滚：

- `/actuator/health` 返回 `DOWN`
- 主流程接口持续返回 `5xx`
- 模型调用整体不可用，且影响对话主链
- 智能问数出现持续性失败、SQL 权限拒绝异常升高
- 法审、知识索引、附件下载等核心功能不可用
- Flyway 迁移失败或迁移后应用无法启动
- 发布后 10 分钟内无法通过业务验收检查

## 3. 回滚职责

| 角色 | 职责 |
| --- | --- |
| 发布负责人 | 决定暂停发布、执行版本回退、同步状态 |
| 运维值班人 | 执行服务切换、配置恢复、健康检查 |
| 后端值班人 | 判断是否涉及数据库恢复、分析错误日志 |
| 产品/业务接口人 | 验证关键业务链路是否恢复 |

## 4. 发布前强制检查

每次生产发布前，必须先完成以下动作：

### 4.1 版本留档

- 记录本次发布包名称、发布时间、负责人
- 记录当前稳定版本路径
- 保留最近 3 个可回滚版本

建议目录结构：

```text
/opt/urban-agent/
  current -> /opt/urban-agent/releases/20260502190000
  releases/
    20260430183000/
    20260501102000/
    20260502190000/
```

### 4.2 数据库备份

生产发布前必须执行一次逻辑备份，至少保留：

- 主库结构
- 核心业务表数据
- 审计与法审数据

建议最小备份范围：

- `agent_session`
- `agent_message`
- `agent_run`
- `plan`
- `plan_step`
- `tool_call`
- `query_record`
- `knowledge_document`
- `knowledge_chunk`
- `knowledge_chunk_embedding`
- `risk_event`
- `legal_review`
- `audit_log`
- `model_call_record`

建议命名：

```text
urban-agent-prod-YYYYMMDD-HHMM-predeploy.sql.gz
```

### 4.3 配置快照

发布前保存以下文件或环境变量快照：

- `application-prod.yml`
- 启动脚本中的环境变量文件
- Nginx 渲染结果
- systemd 服务文件

至少保留：

```text
config-backup/
  20260502-1900/
    production.env
    urban-agent.conf
    urban-agent-backend.service
```

## 5. 标准回滚流程

### 5.1 先判断回滚类型

| 场景 | 回滚方式 |
| --- | --- |
| 仅应用代码异常 | 版本回退 |
| 环境变量或网关配置错误 | 配置回滚 |
| Flyway 失败但未成功提供服务 | 版本回退 + 迁移排查 |
| 已执行破坏性迁移且数据受影响 | 数据库恢复 + 版本回退 |

### 5.2 冻结发布

先执行：

1. 停止继续发布和再次重试
2. 在值班群同步“进入回滚”
3. 记录触发时间、现象、当前版本号

## 6. 版本回退

### 6.1 适用场景

- 新版本代码缺陷
- 模型接入异常
- 审计、问数、知识库主链功能异常
- 发布包损坏

### 6.2 操作步骤

1. 确认上一个稳定版本目录，例如：

```text
/opt/urban-agent/releases/20260501102000
```

2. 停止当前后端服务  
   使用现有脚本：

```bash
STAGING_DIR=/opt/urban-agent deploy/scripts/stop-backend.sh
```

3. 将 `current` 链接切回上一个稳定版本：

```bash
ln -sfn /opt/urban-agent/releases/20260501102000 /opt/urban-agent/current
```

4. 重新启动后端：

```bash
STAGING_DIR=/opt/urban-agent deploy/scripts/start-backend.sh
```

5. 执行健康检查：

```bash
STAGING_DIR=/opt/urban-agent deploy/scripts/health-check.sh
```

6. 如前端静态资源也已发布，同步将 Nginx 指向旧版本静态目录并 reload。

### 6.3 验收标准

- 健康检查恢复 `UP`
- 关键业务链路恢复
- 日志中无持续启动失败

## 7. 数据库备份与恢复

### 7.1 适用场景

- 迁移脚本执行成功但结果错误
- 核心表结构或数据被错误改写
- 发布后出现不可接受的数据损坏

### 7.2 恢复原则

- **优先代码回退，谨慎数据库回退**
- 若只是应用版本不兼容，先不要恢复数据库
- 只有确认数据已经被错误写入、且无法通过前滚修复时，才执行数据库恢复

### 7.3 备份建议

发布前建议执行：

```bash
pg_dump -h <host> -U <user> -d urban_agent -Fc -f urban-agent-prod-predeploy.dump
```

如需快速 SQL 文本备份：

```bash
pg_dump -h <host> -U <user> -d urban_agent | gzip > urban-agent-prod-predeploy.sql.gz
```

### 7.4 恢复步骤

1. 停止应用写入流量
2. 停止后端实例
3. 确认恢复点
4. 恢复数据库
5. 切回匹配该库结构的稳定应用版本
6. 执行健康检查和业务抽样验收

### 7.5 数据恢复风险提示

恢复数据库前必须确认：

- 是否会覆盖发布窗口内新增的法审、审计、会话数据
- 是否需要先导出增量数据留档
- 当前问题是否可通过补丁修复替代整库恢复

## 8. 配置回滚

### 8.1 适用场景

- `DB_URL`、`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OBJECT_STORAGE_*` 配置错误
- OAuth2 / header-auth 配置误切
- Nginx 反向代理或前端静态目录指向错误
- systemd 参数错误

### 8.2 操作步骤

1. 找到上一个已确认可用的配置快照
2. 恢复以下内容：
   - 运行环境变量文件
   - Nginx 配置
   - systemd 服务定义
3. 重新加载 systemd 或 Nginx：

```bash
sudo systemctl daemon-reload
sudo systemctl restart urban-agent-backend
sudo nginx -t
sudo systemctl reload nginx
```

4. 执行健康检查和关键链路验收

### 8.3 必查配置项

- `SPRING_PROFILES_ACTIVE`
- `DB_URL / DB_USERNAME / DB_PASSWORD`
- `OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL`
- `URBAN_AGENT_AI_GATEWAY_TYPE`
- `URBAN_AGENT_SECURITY_*`
- `REDIS_*`
- `RABBITMQ_*`
- `OBJECT_STORAGE_*`

## 9. 健康检查清单

回滚后至少检查以下接口：

### 9.1 基础健康

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/info`

判定：

- 返回 `200`
- `status = UP`

### 9.2 核心业务验收

至少人工验证以下链路：

1. 新建会话并发送普通咨询
2. 智能问数预览
3. 智能问数执行
4. 知识文档列表加载
5. 高风险问题进入法审
6. 审计页可读取最新记录
7. 如启用附件：附件上传/下载链路正常

### 9.3 日志验收

确认日志中没有持续出现以下异常：

- 启动失败
- Flyway 迁移失败
- 数据库连接失败
- 模型不可用持续刷屏
- Redis / MQ / 对象存储不可用导致主链失败

## 10. 回滚后记录

每次回滚完成后，必须补充记录：

- 触发时间
- 触发版本
- 回滚到的版本
- 是否涉及数据库恢复
- 是否涉及配置恢复
- 根因初判
- 后续修复负责人

建议记录模板：

```text
时间：
触发人：
故障现象：
触发版本：
回滚版本：
数据库是否恢复：
配置是否恢复：
健康检查结果：
业务验收结果：
后续修复负责人：
```

## 11. 禁止事项

- 未做数据库备份直接执行生产迁移
- 未确认影响范围直接恢复数据库
- 健康检查未通过就恢复流量
- 仅凭“服务启动了”判断回滚成功
- 发布失败后继续覆盖式重试，导致稳定版本也丢失

## 12. 建议演练频率

- 每月至少做一次 staging 回滚演练
- 每次重大版本上线前做一次预演
- 每次新增中间件、对象存储、认证链路后补一次专项演练
