from contextlib import asynccontextmanager
import base64
import io

from fastapi import FastAPI
from PIL import Image
from pydantic import BaseModel
from sqlalchemy import text

from app.agent import PaperAnalysisAgent
from app.config import get_settings
from app.db import db_session, ping_db
from app.schemas import AnalysisTaskMessage
from app.tools.rag_retriever import RagRetrieveTool
from app.tools.rewrite_generator import RewriteSuggestionTool
from app.vector_store import UserVectorStore
from app.worker import worker

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.rabbitmq_autostart:
        worker.start_background()
    yield


app = FastAPI(title=settings.app_name, lifespan=lifespan)


class AssistantHistoryItem(BaseModel):
    role: str
    content: str


class AssistantChatRequest(BaseModel):
    userId: int
    sessionId: int | None = None
    message: str = ""
    history: list[AssistantHistoryItem] = []
    llmBaseUrl: str | None = None
    llmApiKey: str | None = None
    llmModel: str | None = None
    imageName: str | None = None
    imageContentType: str | None = None
    imageBase64: str | None = None


@app.get("/health")
def health():
    return {"status": "ok", "app": settings.app_name}


@app.get("/health/db")
def health_db():
    return {"status": "ok", "db": ping_db()}


@app.post("/assistant/chat")
async def assistant_chat(request: AssistantChatRequest):
    llm_config = RewriteSuggestionTool()._resolve_llm_config(
        request.llmBaseUrl,
        request.llmApiKey,
        request.llmModel,
    )
    if not llm_config.api_key or not llm_config.model:
        return {
            "answer": "当前账号还没有配置大模型 API Key 或模型名称，请先到个人设置中保存后再使用论文助手。",
            "model": llm_config.model or "",
            "imageInfo": "",
            "ragReferences": [],
        }

    vector_store = UserVectorStore()
    rag_tool = RagRetrieveTool()
    image_info = ""
    image_data_url = ""
    if request.imageBase64:
        image_bytes = base64.b64decode(request.imageBase64)
        image_info = _inspect_image(image_bytes, request.imageName or "upload-image")
        content_type = request.imageContentType or "image/png"
        image_data_url = f"data:{content_type};base64,{request.imageBase64}"

    query = request.message or image_info or "论文图片分析"
    rag_references = rag_tool.run(query, top_k=5, user_id=request.userId)
    history_text = "\n".join(
        f"{item.role}: {item.content[:400]}"
        for item in request.history[-8:]
        if item.content
    )

    try:
        from openai import OpenAI

        client = OpenAI(api_key=llm_config.api_key, base_url=llm_config.base_url)
        content_items: list[dict] = [
            {
                "type": "text",
                "text": _build_assistant_prompt(request, history_text, image_info, rag_references),
            }
        ]
        if image_data_url:
            content_items.append({"type": "image_url", "image_url": {"url": image_data_url}})

        try:
            response = client.chat.completions.create(
                model=llm_config.model,
                messages=[{"role": "user", "content": content_items}],
                temperature=0.35,
            )
        except Exception:
            response = client.chat.completions.create(
                model=llm_config.model,
                messages=[{"role": "user", "content": _build_assistant_prompt(request, history_text, image_info, rag_references)}],
                temperature=0.35,
            )
        answer = response.choices[0].message.content or ""
    except Exception as exc:
        answer = (
            "论文助手调用大模型失败，已返回本地兜底建议：请检查个人设置中的 Base URL、模型名称和 API Key 是否匹配。"
            f"\n\n错误原因：{str(exc)[:220]}"
        )
        if image_info:
            answer += f"\n\n图片分析工具结果：{image_info}"

    memory_content = f"用户问题：{request.message}\n图片信息：{image_info}\n助手回答：{answer}"
    vector_store.add_text(
        request.userId,
        "assistant_chat",
        memory_content,
        {"sessionId": request.sessionId, "model": llm_config.model},
    )
    if image_info:
        vector_store.add_text(
            request.userId,
            "image_analysis",
            image_info,
            {"sessionId": request.sessionId, "imageName": request.imageName or ""},
        )
    return {
        "answer": answer,
        "model": llm_config.model,
        "imageInfo": image_info,
        "ragReferences": rag_references,
    }


@app.post("/tasks/{task_id}/run")
def run_task_manually(task_id: int):
    with db_session() as session:
        row = session.execute(
            text(
                """
                SELECT
                    t.id AS task_id,
                    t.user_id,
                    t.paper_id,
                    t.depth,
                    t.style,
                    t.enable_rag,
                    t.suggestion_count,
                    p.storage_path,
                    s.llm_base_url,
                    s.llm_api_key,
                    s.llm_model
                FROM analysis_tasks t
                JOIN papers p ON p.id = t.paper_id
                LEFT JOIN user_settings s ON s.user_id = t.user_id
                WHERE t.id = :task_id
                """
            ),
            {"task_id": task_id},
        ).mappings().first()

    if not row:
        return {"status": "not_found", "taskId": task_id}

    message = AnalysisTaskMessage(
        taskId=row["task_id"],
        userId=row["user_id"],
        paperId=row["paper_id"],
        storagePath=row["storage_path"],
        depth=row["depth"],
        style=row["style"],
        enableRag=bool(row["enable_rag"]),
        suggestionCount=row["suggestion_count"],
        llmBaseUrl=row["llm_base_url"],
        llmApiKey=row["llm_api_key"],
        llmModel=row["llm_model"],
    )
    PaperAnalysisAgent().handle_task(message)
    return {"status": "done", "taskId": task_id}


def _build_assistant_prompt(
    request: AssistantChatRequest,
    history_text: str,
    image_info: str,
    rag_references: list[str],
) -> str:
    return (
        "你是论文写作与修改助手，只回答和论文写作、语句润色、AI风格风险、报告解读、研究表达优化相关的问题。"
        "如果用户偏离主题，请温和引导回论文修改场景。不要承诺规避检测，不要编造数据来源。"
        "回答要具体、可直接操作，必要时给出可复制的改写版本。\n\n"
        f"用户问题：{request.message or '请分析这张图片中的论文内容，并给出写作建议'}\n"
        f"图片分析工具结果：{image_info or '无图片'}\n"
        f"本会话历史：\n{history_text or '暂无'}\n\n"
        f"用户个人向量记忆与RAG参考：\n{chr(10).join(rag_references) or '暂无'}\n\n"
        "请用中文回答，结构清晰，优先给出修改建议和示例表达。"
    )


def _inspect_image(image_bytes: bytes, filename: str) -> str:
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            info = f"{filename}，格式 {img.format}，尺寸 {img.width}x{img.height}"
    except Exception:
        return f"{filename}，图片元信息读取失败"

    try:
        import pytesseract

        text = pytesseract.image_to_string(Image.open(io.BytesIO(image_bytes)), lang="chi_sim+eng").strip()
        if text:
            info += f"，OCR 识别文本：{text[:500]}"
    except Exception:
        pass
    return info
