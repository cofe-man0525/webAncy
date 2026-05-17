# 论文 AI 风格风险分析 Java 后端

这是一个 Spring Boot 3 单体后端，用于配合前端 `agent查重论文网站`。

## 技术栈

- Java 17
- Spring Boot 3
- Spring Security + JWT
- MyBatis-Plus
- MySQL
- Redis
- RabbitMQ
- 本地文件存储，后续可替换为 MinIO

## 当前能力

- 用户注册、登录、JWT 鉴权
- 上传论文文件并创建分析任务
- 任务进度查询
- 历史记录查询
- 分析报告查询
- 句子级推荐语句重新生成
- 用户分析偏好读取与保存
- 默认本地 Mock Agent 异步生成报告
- 保留 RabbitMQ 投递接口，后续可接 Python Agent Worker

## 启动步骤

1. 创建 MySQL 数据库并执行 `docs/database-schema.md` 中的 SQL。
2. 修改 `src/main/resources/application.yml` 里的数据库账号密码。
3. 启动 Redis，可选启动 RabbitMQ。
4. 默认 `app.rabbit.enabled=false`，无需 Python Worker 也能模拟生成报告。
5. 运行后端：

```bash
mvn spring-boot:run
```

服务地址：

```text
http://localhost:8080/api
```

前端开发地址默认允许：

```text
http://localhost:5173
```

## 接入 Python Agent

后续 Python Agent 准备好后：

1. 启动 MySQL、Redis、RabbitMQ。
2. 启动 Python AI 服务。
3. Java 使用 AI 配置启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ai
```

4. Python 服务消费队列 `papercheck.analysis.queue`。
5. 消费消息后解析文档、调用 RAG 和大模型。
6. 将报告结果写入 MySQL，Java 报告接口即可读取。

消息结构见 `com.xianhua.papercheck.mq.AnalysisTaskMessage`。
