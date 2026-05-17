import json
from collections import Counter
from dataclasses import dataclass

from sqlalchemy import text

from app.config import get_settings
from app.db import db_session


@dataclass
class UserAnalysisMemory:
    report_count: int
    average_score: int
    recent_summaries: list[str]
    common_reasons: list[str]

    def as_context(self) -> str:
        if self.report_count == 0:
            return "该用户暂无历史分析记忆。"
        summaries = "；".join(self.recent_summaries[:3]) or "暂无摘要"
        reasons = "、".join(self.common_reasons[:5]) or "暂无高频风险原因"
        return (
            f"该用户已有 {self.report_count} 次历史分析，近期平均风险分约 {self.average_score}。"
            f"近期摘要：{summaries}。高频风险原因：{reasons}。"
        )


class UserAnalysisMemoryStore:
    def __init__(self):
        self.settings = get_settings()

    def load(self, user_id: int) -> UserAnalysisMemory:
        with db_session() as session:
            reports = session.execute(
                text(
                    """
                    SELECT id, summary, overall_risk_score
                    FROM analysis_reports
                    WHERE user_id = :user_id
                    ORDER BY created_at DESC
                    LIMIT :limit
                    """
                ),
                {"user_id": user_id, "limit": self.settings.agent_memory_limit},
            ).mappings().all()

            reasons = session.execute(
                text(
                    """
                    SELECT s.reasons_json
                    FROM analysis_sentences s
                    JOIN analysis_reports r ON r.id = s.report_id
                    WHERE r.user_id = :user_id AND s.risk_score >= 75
                    ORDER BY s.created_at DESC
                    LIMIT 30
                    """
                ),
                {"user_id": user_id},
            ).mappings().all()

        scores = [int(item["overall_risk_score"] or 0) for item in reports]
        average_score = round(sum(scores) / len(scores)) if scores else 0
        counter: Counter[str] = Counter()
        for row in reasons:
            try:
                for reason in json.loads(row["reasons_json"] or "[]"):
                    if reason:
                        counter[str(reason)] += 1
            except Exception:
                continue

        return UserAnalysisMemory(
            report_count=len(reports),
            average_score=average_score,
            recent_summaries=[str(item["summary"]) for item in reports if item["summary"]],
            common_reasons=[reason for reason, _ in counter.most_common(5)],
        )
