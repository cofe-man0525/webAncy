import json
from dataclasses import dataclass

from app.config import get_settings
from app.schemas import AnalysisTaskMessage
from app.tools.rewrite_generator import RewriteSuggestionTool


@dataclass
class AgentPlan:
    use_document_parser: bool = True
    use_ocr: bool = False
    use_rag: bool = True
    use_web_search: bool = False
    use_rewrite: bool = True
    max_sentences: int = 120
    route_reason: str = "按分析深度和文件类型自动选择工具"

    def trace_detail(self) -> str:
        tools = ["文档解析", "句子切分", "风险识别"]
        if self.use_ocr:
            tools.append("OCR")
        if self.use_rag:
            tools.append("私有知识库 RAG")
        if self.use_web_search:
            tools.append("联网搜索")
        if self.use_rewrite:
            tools.append("改写建议")
        return f"已选择工具：{'、'.join(tools)}；原因：{self.route_reason}"


class AgentRouter:
    def __init__(self):
        self.settings = get_settings()

    def plan(self, message: AnalysisTaskMessage, storage_path: str) -> AgentPlan:
        if self.settings.agent_llm_router:
            llm_plan = self._try_llm_plan(message, storage_path)
            if llm_plan:
                return llm_plan

        suffix = storage_path.lower()
        use_ocr = suffix.endswith((".png", ".jpg", ".jpeg", ".webp"))
        depth = (message.depth or "standard").lower()
        max_sentences = 180 if depth == "deep" else 120 if depth == "standard" else 60
        use_web_search = bool(self.settings.web_search_enabled and depth == "deep")
        return AgentPlan(
            use_ocr=use_ocr,
            use_rag=bool(message.enableRag),
            use_web_search=use_web_search,
            max_sentences=max_sentences,
            route_reason=f"当前深度为 {depth}，文件类型需要 OCR={use_ocr}",
        )

    def _try_llm_plan(self, message: AnalysisTaskMessage, storage_path: str) -> AgentPlan | None:
        llm_config = RewriteSuggestionTool()._resolve_llm_config(
            message.llmBaseUrl,
            message.llmApiKey,
            message.llmModel,
        )
        if not llm_config.api_key or not llm_config.model:
            return None
        try:
            from openai import OpenAI

            client = OpenAI(api_key=llm_config.api_key, base_url=llm_config.base_url)
            prompt = (
                "你是论文分析 Agent 的工具路由器。请只输出 JSON。\n"
                "字段：use_rag, use_web_search, use_rewrite, max_sentences, route_reason。\n"
                f"任务深度：{message.depth}\n"
                f"写作风格：{message.style}\n"
                f"启用RAG：{message.enableRag}\n"
                f"文件路径：{storage_path}\n"
            )
            response = client.chat.completions.create(
                model=llm_config.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0,
            )
            raw = response.choices[0].message.content or "{}"
            data = json.loads(raw.strip().strip("`").removeprefix("json"))
            return AgentPlan(
                use_ocr=storage_path.lower().endswith((".png", ".jpg", ".jpeg", ".webp")),
                use_rag=bool(data.get("use_rag", message.enableRag)),
                use_web_search=bool(data.get("use_web_search", False)) and self.settings.web_search_enabled,
                use_rewrite=bool(data.get("use_rewrite", True)),
                max_sentences=int(data.get("max_sentences", 120)),
                route_reason=str(data.get("route_reason", "LLM 自动路由")),
            )
        except Exception as exc:
            print(
                "[LLM] router failed, rule router used: "
                f"source={llm_config.source}, model={llm_config.model}, error={str(exc)[:180]}"
            )
            return None
