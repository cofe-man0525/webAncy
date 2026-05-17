# 论文分析 AI Agent 能力说明

## 当前定位

该 Python 服务已经从单纯的文档分析 Worker，升级为面向论文分析场景的多工具 AI Agent。Java 后端负责任务创建、用户鉴权、历史记忆聚合和报告接口，Python Agent 负责消费任务并自动调用文档解析、OCR、RAG、联网搜索、风险识别、改写生成和数据库写回等工具。

## Agent 流程

```text
Java 创建分析任务
-> RabbitMQ 投递任务
-> Python Agent 消费任务
-> 读取当前用户历史分析记忆
-> Agent Router 选择工具
-> 文档解析 / OCR
-> 句子切分
-> RAG 私有知识库检索
-> 可选联网搜索
-> AI 风格风险识别
-> 学术表达优化建议生成
-> 报告和 Agent Trace 写回 MySQL
-> Java 按当前用户读取报告并返回前端展示
```

## 已增强能力

- `AgentRouter`：根据任务深度、文件类型和配置自动选择 OCR、RAG、联网搜索、改写等工具。
- `UserAnalysisMemoryStore`：按 `user_id` 读取用户历史分析摘要和高频风险原因，形成用户级历史记忆。
- `RagRetrieveTool`：从关键词检索升级为轻量向量检索 + 关键词兜底。
- `WebSearchTool`：预留 Tavily Search API 接入，深度分析时可检索互联网资料。
- `PaperAnalysisAgent`：将历史记忆、工具路由、RAG/搜索结果写入 Agent Trace，便于前端展示分析过程。

## Java 配套能力

- 新增 `/history/memory` 接口，按当前登录用户返回历史分析画像。
- 返回内容包含历史报告数量、近期平均风险分、最近报告摘要和高频风险原因。
- 前端历史页展示 AI 历史记忆，保证不同用户只能看到自己的分析数据。
- 用户设置中支持保存个人大模型 `Base URL`、`Model` 和 `API Key`。
- Java 创建分析任务时会把当前用户的大模型配置写入 RabbitMQ 消息，Python Agent 优先使用该用户配置，未配置时再使用服务端 `.env` 兜底。
- API Key 查询时只返回是否已配置，不向前端明文回显。

## 数据库迁移

如果已有 `user_settings` 表，需要执行 Java 后端中的迁移脚本：

```text
agent论文查重后端java/docs/user-ai-settings-migration.sql
```
