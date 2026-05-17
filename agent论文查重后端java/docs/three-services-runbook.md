# 三端联动启动说明

三个目录：

| 模块 | 路径 | 作用 |
|---|---|---|
| 前端 | `D:/桌面文件/agent查重论文网站` | 登录、上传、进度、报告、历史、设置 |
| Java 后端 | `D:/桌面文件/agent论文查重后端java` | 用户、文件、任务、JWT、历史、报告接口 |
| Python AI 服务 | `D:/桌面文件/PythonAI服务的论文查重` | RabbitMQ 消费、Agent 工具调用、RAG、写回 MySQL |

## 1. 基础设施

需要启动：

- MySQL 8
- Redis
- RabbitMQ

MySQL 先执行：

```text
D:/桌面文件/agent论文查重后端java/docs/database-schema.md
```

## 2. 启动 Python AI 服务

```bash
cd D:/桌面文件/PythonAI服务的论文查重
python -m venv .venv
.venv/Scripts/activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

如果你的 Python 命令不是 `python`，请按本机环境替换。

## 3. 启动 Java 后端

联动 Python 时使用 `ai` profile：

```bash
cd D:/桌面文件/agent论文查重后端java
mvn spring-boot:run -Dspring-boot.run.profiles=ai
```

如果只是想不依赖 Python 先跑 Java 模拟报告：

```bash
mvn spring-boot:run
```

## 4. 启动前端

```bash
cd D:/桌面文件/agent查重论文网站
npm install
npm run dev
```

访问：

```text
http://localhost:5173
```

## 5. 完整流程

1. 前端注册/登录。
2. 上传论文。
3. Java 保存文件和任务。
4. Java 发送任务 JSON 到 RabbitMQ。
5. Python 消费任务并调用工具链。
6. Python 写入 `analysis_reports` 和 `analysis_sentences`。
7. 前端轮询进度，完成后读取报告。

## 6. 当前 Agent 工具链

- `document_parse_tool`：解析 Word、PDF、TXT、图片 OCR
- `sentence_split_tool`：段落和句子切分
- `ai_style_detect_tool`：AI 风格风险评分和原因识别
- `rag_retrieve_tool`：检索学术写作规范
- `rewrite_suggestion_tool`：生成可复制推荐表达

没有配置大模型 API 时，Python 会使用本地模板生成建议；配置 `.env` 中的 `LLM_API_KEY`、`LLM_MODEL` 后会尝试调用兼容 OpenAI 的模型接口。
