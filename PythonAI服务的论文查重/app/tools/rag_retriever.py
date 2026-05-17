import re
from dataclasses import dataclass

from app.config import get_settings
from app.vector_store import UserVectorStore, cosine, embed_text


@dataclass
class KnowledgeItem:
    title: str
    content: str
    keywords: tuple[str, ...]


KNOWLEDGE_BASE = [
    KnowledgeItem(
        "引言写作",
        "引言部分应明确研究对象、问题范围、材料来源和研究意义，避免只有宽泛背景描述。",
        ("本文旨在", "探讨", "应用", "影响", "发展趋势", "引言"),
    ),
    KnowledgeItem(
        "研究对象",
        "学术表达中应尽量说明具体对象、场景、样本、数据来源或案例，以增强原创性和可信度。",
        ("研究对象", "场景", "案例", "数据", "样本", "高校", "教学评价"),
    ),
    KnowledgeItem(
        "论证支撑",
        "当句子包含效率、准确性、影响、问题等判断时，建议补充证据、来源或限定条件。",
        ("效率", "准确性", "影响", "问题", "提高", "降低", "可能"),
    ),
    KnowledgeItem(
        "过渡句",
        "章节过渡句应提示后文结构，避免只使用从多个层面进行分析等空泛表达。",
        ("因此", "多个层面", "综合分析", "技术", "制度", "伦理"),
    ),
    KnowledgeItem(
        "结论限定",
        "结论表述应避免绝对化，建议使用研究范围、材料条件和分析维度来限定结论。",
        ("表明", "说明", "证明", "总之", "综上"),
    ),
]


class RagRetrieveTool:
    name = "rag_retrieve_tool"

    def __init__(self):
        self.settings = get_settings()
        self.vector_store = UserVectorStore()
        self._vectors = [(item, embed_text(item.title + item.content)) for item in KNOWLEDGE_BASE]

    def run(self, query: str, top_k: int = 2, user_id: int | None = None) -> list[str]:
        results: list[str] = []
        if self.settings.vector_rag_enabled and user_id is not None:
            results.extend(self.vector_store.search(user_id, query, top_k=top_k, source_types=("paper_report", "assistant_chat", "image_analysis")))
        if self.settings.vector_rag_enabled:
            results.extend(self._vector_search(query, top_k))
        if not results:
            results.extend(self._keyword_search(query, top_k))
        return _deduplicate(results)[:top_k]

    def _vector_search(self, query: str, top_k: int) -> list[str]:
        query_vector = embed_text(query)
        scored = []
        for item, vector in self._vectors:
            score = cosine(query_vector, vector)
            keyword_score = sum(0.08 for keyword in item.keywords if keyword in query)
            scored.append((score + keyword_score, item))
        scored.sort(key=lambda pair: pair[0], reverse=True)
        return [item.content for score, item in scored[:top_k] if score > 0]

    @staticmethod
    def _keyword_search(query: str, top_k: int) -> list[str]:
        scores = []
        tokens = set(re.findall(r"[\u4e00-\u9fa5]{2,}|[a-zA-Z]{2,}", query))
        for item in KNOWLEDGE_BASE:
            keyword_score = sum(4 for keyword in item.keywords if keyword in query)
            token_score = len(tokens.intersection(set(item.keywords)))
            scores.append((keyword_score + token_score, item))
        scores.sort(key=lambda pair: pair[0], reverse=True)
        return [item.content for score, item in scores[:top_k] if score > 0] or [KNOWLEDGE_BASE[1].content]


def _deduplicate(items: list[str]) -> list[str]:
    seen = set()
    result = []
    for item in items:
        key = item[:80]
        if key in seen:
            continue
        seen.add(key)
        result.append(item)
    return result
