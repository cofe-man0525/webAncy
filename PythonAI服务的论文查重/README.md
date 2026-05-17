# Python AI 服务：论文 AI 风格风险分析 Agent

这个服务用于对接 Java 后端的 RabbitMQ 任务，完成文档解析、OCR、RAG 检索、句子级风险分析和推荐语句生成，并把结果写回 Java 使用的 MySQL 数据库。

## 技术栈

- FastAPI
- RabbitMQ / pika
- SQLAlchemy + PyMySQL
- PyMuPDF / pdfplumber / python-docx
- pytesseract OCR，可替换为 PaddleOCR
- OpenAI-compatible LLM API，可选
- 本地 RAG 知识库，可后续替换为 pgvector / Milvus / Qdrant

## 启动

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --host 0.0.0.0 --port 8090
```

如果 `.env` 中 `RABBITMQ_AUTOSTART=true`，FastAPI 启动后会自动启动 RabbitMQ 消费线程。

## 与 Java 后端联动

Java 使用 AI 模式启动：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ai
```

流程：

1. 前端登录并上传论文。
2. Java 保存文件、创建 `analysis_tasks`、发送 JSON 消息到 `papercheck.analysis.queue`。
3. Python 消费任务，读取 Java 保存的文件。
4. Python Agent 调用工具链分析，并写入 `analysis_reports`、`analysis_sentences`。
5. Java 报告接口读取 MySQL，前端展示报告。

## 合规定位

本服务定位为“论文 AI 风格风险分析与学术表达优化建议”，不承诺规避检测，也不提供绕过检测的保证。
