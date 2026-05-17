import json
from datetime import datetime

from sqlalchemy import text

from app.agent_router import AgentPlan, AgentRouter
from app.db import db_session
from app.id_generator import next_id
from app.memory import UserAnalysisMemory, UserAnalysisMemoryStore
from app.schemas import AnalysisTaskMessage, SentenceAnalysis
from app.tools.document_parser import DocumentParseTool
from app.tools.rag_retriever import RagRetrieveTool
from app.tools.rewrite_generator import RewriteSuggestionTool
from app.tools.search_tool import WebSearchTool
from app.tools.sentence_splitter import SentenceSplitTool
from app.tools.style_detector import AiStyleDetectTool
from app.vector_store import UserVectorStore


class PaperAnalysisAgent:
    def __init__(self):
        self.router = AgentRouter()
        self.memory_store = UserAnalysisMemoryStore()
        self.document_parser = DocumentParseTool()
        self.sentence_splitter = SentenceSplitTool()
        self.style_detector = AiStyleDetectTool()
        self.rag_retriever = RagRetrieveTool()
        self.web_search = WebSearchTool()
        self.rewrite_generator = RewriteSuggestionTool()
        self.vector_store = UserVectorStore()
        self.rewrite_source_count = {"llm": 0, "fallback": 0}

    def handle_task(self, message: AnalysisTaskMessage) -> None:
        self._update_task(message.taskId, "processing", 8)
        self.rewrite_source_count = {"llm": 0, "fallback": 0}
        try:
            memory = self.memory_store.load(message.userId)
            plan = self.router.plan(message, message.storagePath)

            self._update_task(message.taskId, "processing", 18)
            raw_text = self.document_parser.run(message.storagePath)
            if not raw_text.strip():
                raw_text = "未能从文件中提取到有效文本。请检查文件是否为扫描件，或配置 OCR 服务后重试。"

            self._update_task(message.taskId, "processing", 36)
            paragraphs = self.sentence_splitter.run(raw_text)

            self._update_task(message.taskId, "processing", 60)
            sentence_results = self._analyze_sentences(paragraphs, message, memory, plan)

            self._update_task(message.taskId, "processing", 84)
            self._write_report(message, sentence_results, memory, plan)

            self._update_task(message.taskId, "done", 100)
        except Exception as exc:
            self._update_task(message.taskId, "failed", 100, str(exc)[:480])
            raise

    def _analyze_sentences(
        self,
        paragraphs: list[dict],
        message: AnalysisTaskMessage,
        memory: UserAnalysisMemory,
        plan: AgentPlan,
    ) -> list[SentenceAnalysis]:
        results: list[SentenceAnalysis] = []
        count = 0
        search_count = 0
        memory_context = memory.as_context()

        for paragraph in paragraphs:
            paragraph_text = paragraph["text"]
            for sentence in paragraph["sentences"]:
                if count >= plan.max_sentences:
                    break
                count += 1
                original_text = sentence["text"]
                detect = self.style_detector.run(original_text, paragraph_text)

                references: list[str] = []
                if plan.use_rag and message.enableRag:
                    references.extend(self.rag_retriever.run(original_text, top_k=2, user_id=message.userId))

                if (
                    plan.use_web_search
                    and detect["risk_score"] >= 50
                    and search_count < self.web_search.settings.web_search_sentence_limit
                ):
                    references.extend(self.web_search.run(original_text, max_results=1))
                    search_count += 1

                advice, suggested_texts = self.rewrite_generator.run(
                    sentence=original_text,
                    paragraph_text=paragraph_text,
                    rag_references=references,
                    style=message.style,
                    count=message.suggestionCount,
                    memory_context=memory_context,
                    llm_base_url=message.llmBaseUrl,
                    llm_api_key=message.llmApiKey,
                    llm_model=message.llmModel,
                )
                self.rewrite_source_count[self.rewrite_generator.last_source] += 1
                results.append(
                    SentenceAnalysis(
                        paragraph_index=paragraph["paragraph_index"],
                        sentence_index=sentence["sentence_index"],
                        original_text=original_text,
                        risk_score=detect["risk_score"],
                        risk_level=detect["risk_level"],
                        reasons=detect["reasons"],
                        advice=advice,
                        suggested_texts=suggested_texts,
                        rag_references=references,
                    )
                )
            if count >= plan.max_sentences:
                break
        return results

    def _write_report(
        self,
        message: AnalysisTaskMessage,
        sentences: list[SentenceAnalysis],
        memory: UserAnalysisMemory,
        plan: AgentPlan,
    ) -> None:
        now = datetime.now()
        overall_score = self._overall_score(sentences)
        summary = self._summary(overall_score, sentences, memory)
        title = self._paper_title(message.paperId)
        agent_trace = self._agent_trace(sentences, memory, plan)

        with db_session() as session:
            existing = session.execute(
                text("SELECT id FROM analysis_reports WHERE task_id = :task_id"),
                {"task_id": message.taskId},
            ).mappings().first()
            if existing:
                return

            report_id = next_id()
            session.execute(
                text(
                    """
                    INSERT INTO analysis_reports
                    (id, task_id, user_id, paper_id, title, overall_risk_score, summary, agent_trace_json, created_at, updated_at)
                    VALUES
                    (:id, :task_id, :user_id, :paper_id, :title, :overall_risk_score, :summary, :agent_trace_json, :created_at, :updated_at)
                    """
                ),
                {
                    "id": report_id,
                    "task_id": message.taskId,
                    "user_id": message.userId,
                    "paper_id": message.paperId,
                    "title": title,
                    "overall_risk_score": overall_score,
                    "summary": summary,
                    "agent_trace_json": json.dumps(agent_trace, ensure_ascii=False),
                    "created_at": now,
                    "updated_at": now,
                },
            )

            for item in sentences:
                session.execute(
                    text(
                        """
                        INSERT INTO analysis_sentences
                        (id, report_id, task_id, paragraph_index, sentence_index, original_text, risk_score,
                         risk_level, reasons_json, advice, suggested_texts_json, rag_references_json, created_at)
                        VALUES
                        (:id, :report_id, :task_id, :paragraph_index, :sentence_index, :original_text, :risk_score,
                         :risk_level, :reasons_json, :advice, :suggested_texts_json, :rag_references_json, :created_at)
                        """
                    ),
                    {
                        "id": next_id(),
                        "report_id": report_id,
                        "task_id": message.taskId,
                        "paragraph_index": item.paragraph_index,
                        "sentence_index": item.sentence_index,
                        "original_text": item.original_text,
                        "risk_score": item.risk_score,
                        "risk_level": item.risk_level,
                        "reasons_json": json.dumps(item.reasons, ensure_ascii=False),
                        "advice": item.advice,
                        "suggested_texts_json": json.dumps(item.suggested_texts, ensure_ascii=False),
                        "rag_references_json": json.dumps(item.rag_references, ensure_ascii=False),
                        "created_at": now,
                    },
                )
        self._index_report_memory(message, title, summary, sentences)

    def _index_report_memory(
        self,
        message: AnalysisTaskMessage,
        title: str,
        summary: str,
        sentences: list[SentenceAnalysis],
    ) -> None:
        self.vector_store.add_text(
            message.userId,
            "paper_report",
            f"{title}\n{summary}",
            {"taskId": message.taskId, "paperId": message.paperId, "title": title},
        )
        for item in sentences[:80]:
            content = (
                f"原句：{item.original_text}\n"
                f"风险：{item.risk_score}% {item.risk_level}\n"
                f"原因：{'；'.join(item.reasons)}\n"
                f"建议：{item.advice}\n"
                f"改写：{'；'.join(item.suggested_texts[:2])}"
            )
            self.vector_store.add_text(
                message.userId,
                "paper_report",
                content,
                {"taskId": message.taskId, "paperId": message.paperId, "title": title},
            )

    def _agent_trace(
        self,
        sentences: list[SentenceAnalysis],
        memory: UserAnalysisMemory,
        plan: AgentPlan,
    ) -> list[dict]:
        rag_count = sum(1 for item in sentences if item.rag_references)
        high_count = sum(1 for item in sentences if item.risk_score >= 75)
        llm_count = self.rewrite_source_count.get("llm", 0)
        fallback_count = self.rewrite_source_count.get("fallback", 0)
        rewrite_detail = f"DeepSeek/兼容大模型生成 {llm_count} 个分析单元；本地规则兜底 {fallback_count} 个分析单元"
        if fallback_count and self.rewrite_generator.last_error:
            rewrite_detail += f"；最近一次兜底原因：{self.rewrite_generator.last_error[:120]}"

        return [
            {"name": "Agent 自动路由", "status": "done", "detail": plan.trace_detail()},
            {"name": "用户历史记忆", "status": "done", "detail": memory.as_context()},
            {"name": "文档解析工具", "status": "done", "detail": "已解析 Word、PDF、TXT 或图片 OCR 文本"},
            {"name": "句子切分工具", "status": "done", "detail": f"已识别 {len(sentences)} 个句子级分析单元"},
            {"name": "RAG/搜索检索工具", "status": "done", "detail": f"已为 {rag_count} 个句子补充写作规范或外部资料参考"},
            {"name": "风险识别工具", "status": "done", "detail": f"识别到 {high_count} 个高风险句子"},
            {"name": "表达优化工具", "status": "done", "detail": rewrite_detail},
        ]

    def _update_task(self, task_id: int, status: str, progress: int, error_message: str | None = None) -> None:
        now = datetime.now()
        with db_session() as session:
            session.execute(
                text(
                    """
                    UPDATE analysis_tasks
                    SET status = :status,
                        progress = :progress,
                        error_message = :error_message,
                        started_at = CASE WHEN started_at IS NULL AND :status = 'processing' THEN :now ELSE started_at END,
                        finished_at = CASE WHEN :status IN ('done', 'failed') THEN :now ELSE finished_at END,
                        updated_at = :now,
                        version = version + 1
                    WHERE id = :task_id
                    """
                ),
                {
                    "task_id": task_id,
                    "status": status,
                    "progress": progress,
                    "error_message": error_message,
                    "now": now,
                },
            )

    def _paper_title(self, paper_id: int) -> str:
        with db_session() as session:
            row = session.execute(
                text("SELECT original_name FROM papers WHERE id = :paper_id"),
                {"paper_id": paper_id},
            ).mappings().first()
        if not row:
            return "未命名论文"
        name = row["original_name"]
        for suffix in [".docx", ".doc", ".pdf", ".txt", ".png", ".jpg", ".jpeg"]:
            if name.lower().endswith(suffix):
                return name[: -len(suffix)]
        return name

    @staticmethod
    def _overall_score(sentences: list[SentenceAnalysis]) -> int:
        if not sentences:
            return 0
        high_weight = sum(item.risk_score for item in sentences if item.risk_score >= 75)
        average = sum(item.risk_score for item in sentences) / len(sentences)
        if high_weight:
            return min(96, round(average * 0.75 + (high_weight / len(sentences)) * 0.25))
        return round(average)

    @staticmethod
    def _summary(overall_score: int, sentences: list[SentenceAnalysis], memory: UserAnalysisMemory) -> str:
        high_count = sum(1 for item in sentences if item.risk_score >= 75)
        medium_count = sum(1 for item in sentences if 50 <= item.risk_score < 75)
        if overall_score >= 75:
            level = "整体 AI 风格风险偏高"
        elif overall_score >= 50:
            level = "整体 AI 风格风险中等"
        else:
            level = "整体 AI 风格风险较低"

        memory_tip = ""
        if memory.report_count:
            memory_tip = f" 结合该用户历史分析，近期平均风险分约 {memory.average_score}，建议优先关注历史高频问题。"
        return (
            f"{level}。共识别 {len(sentences)} 个句子，其中高风险 {high_count} 个，中风险 {medium_count} 个。"
            f"建议优先处理高风险句子，补充具体研究对象、数据来源和论证过程。{memory_tip}"
        )
