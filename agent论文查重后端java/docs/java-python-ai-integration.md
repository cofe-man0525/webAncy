# Java 与 Python AI 服务对接说明

这个项目现在支持三种分析任务触发方式，方便本地开发、演示和正式异步部署。

## 1. Mock 模式

默认模式，不依赖 Python 服务和 RabbitMQ。

```bash
mvn spring-boot:run
```

适合只演示前后端流程，Java 会生成一份本地模拟报告。

## 2. HTTP 直连 Python 模式

Java 保存论文、创建分析任务后，异步调用 Python FastAPI：

```text
POST http://localhost:8090/tasks/{taskId}/run
```

启动 Java：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=http-ai
```

配置项：

```yaml
app:
  rabbit:
    enabled: false
  ai-service:
    enabled: true
    base-url: http://localhost:8090
```

适合简历演示和本地联调：前端上传论文，Java 负责用户、文件、任务、历史记忆和模型配置，Python 负责 Agent 工具链分析。

## 3. RabbitMQ 异步模式

Java 将分析任务投递到 RabbitMQ，Python Worker 消费任务。

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ai
```

配置项：

```yaml
app:
  rabbit:
    enabled: true
```

适合正式部署：上传接口快速返回，Python Worker 异步处理文档解析、RAG 检索、联网搜索和改写建议。

## 4. 用户模型配置链路

前端登录后可在个人设置中填写：

- 大模型 Base URL
- 模型名称
- API Key

Java 保存到 `user_settings` 表。提交分析任务时：

- RabbitMQ 模式：Java 将 `llmBaseUrl`、`llmApiKey`、`llmModel` 放入任务消息。
- HTTP 模式：Python 根据 `taskId` 查询任务、论文和用户模型配置。

Python Agent 会优先使用用户自己的模型配置，没有配置时再使用服务端 `.env` 中的默认模型配置。

## 5. 简历表述

采用 Spring Boot 封装论文任务、用户设置和历史记忆接口，通过 RabbitMQ 异步消息与 HTTP 直连两种方式接入 Python FastAPI Agent 服务；Java 端负责登录鉴权、任务持久化、模型配置和分析结果查询，Python 端负责文档解析、RAG 检索、联网搜索、AI 风格识别和改写建议生成，实现前后端与 AI 分析链路的完整闭环。
