TEMPLATE_PHRASES = [
    "随着",
    "不断发展",
    "越来越广泛",
    "本文旨在",
    "探讨",
    "及其影响",
    "未来发展趋势",
    "实际应用过程中",
    "多个层面",
    "综合分析",
    "具有重要意义",
    "显著提升",
    "带来新的机遇",
]

EVIDENCE_MARKERS = [
    "数据",
    "案例",
    "访谈",
    "样本",
    "问卷",
    "实验",
    "文献",
    "课程",
    "调研",
    "统计",
]


class AiStyleDetectTool:
    name = "ai_style_detect_tool"

    def run(self, sentence: str, paragraph_text: str) -> dict:
        score = 25
        reasons: list[str] = []

        matched = [phrase for phrase in TEMPLATE_PHRASES if phrase in sentence]
        if matched:
            score += min(35, len(matched) * 8)
            reasons.append("存在较常见的模板化表达：" + "、".join(matched[:4]))

        if len(sentence) < 35 and any(word in sentence for word in ["影响", "问题", "意义", "趋势", "价值"]):
            score += 14
            reasons.append("句子较短但判断较大，缺少具体材料或限定条件")

        if not any(marker in sentence for marker in EVIDENCE_MARKERS):
            score += 12
            reasons.append("未体现明确的数据来源、样本、案例或研究过程")

        if sentence.count("、") >= 2 or "等" in sentence:
            score += 7
            reasons.append("并列表达较多，容易呈现概括式总结")

        if len(paragraph_text) > 0 and paragraph_text.count(sentence) > 1:
            score += 10
            reasons.append("段落中存在重复表达")

        score = max(5, min(96, score))
        risk_level = "high" if score >= 75 else "medium" if score >= 50 else "low"
        if not reasons:
            reasons.append("句子包含较具体的限定信息，AI 风格风险较低")

        return {"risk_score": score, "risk_level": risk_level, "reasons": reasons}
