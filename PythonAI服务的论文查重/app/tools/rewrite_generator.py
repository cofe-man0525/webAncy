from dataclasses import dataclass
from urllib.parse import urlparse, urlunparse

from app.config import get_settings


@dataclass(frozen=True)
class LlmConfig:
    base_url: str | None
    api_key: str | None
    model: str | None
    source: str


class RewriteSuggestionTool:
    name = "rewrite_suggestion_tool"

    def __init__(self):
        self.settings = get_settings()
        self.last_source = "fallback"
        self.last_error = ""
        self.last_llm_source = "none"

    def run(
        self,
        sentence: str,
        paragraph_text: str,
        rag_references: list[str],
        style: str,
        count: int,
        memory_context: str = "",
        llm_base_url: str | None = None,
        llm_api_key: str | None = None,
        llm_model: str | None = None,
    ) -> tuple[str, list[str]]:
        advice = self._build_advice(rag_references, memory_context)
        llm_result = self._try_llm(
            sentence,
            paragraph_text,
            rag_references,
            style,
            count,
            memory_context,
            llm_base_url,
            llm_api_key,
            llm_model,
        )
        if llm_result:
            self.last_source = "llm"
            self.last_error = ""
            return advice, llm_result

        self.last_source = "fallback"
        return advice, self._fallback_suggestions(sentence, style, count)

    def _build_advice(self, rag_references: list[str], memory_context: str = "") -> str:
        if rag_references:
            base = "；".join(rag_references[:2])
        else:
            base = "建议补充具体研究对象、材料来源、数据依据或分析维度，降低泛化表达比例。"
        if memory_context and "暂无历史分析记忆" not in memory_context:
            return base + " 同时可结合该用户历史高频问题，减少模板化表达。"
        return base

    def _try_llm(
        self,
        sentence: str,
        paragraph_text: str,
        rag_references: list[str],
        style: str,
        count: int,
        memory_context: str = "",
        llm_base_url: str | None = None,
        llm_api_key: str | None = None,
        llm_model: str | None = None,
    ) -> list[str] | None:
        llm_config = self._resolve_llm_config(llm_base_url, llm_api_key, llm_model)
        self.last_llm_source = llm_config.source
        if not llm_config.api_key or not llm_config.model:
            self.last_error = "当前用户未配置大模型 API Key 或模型名称"
            print(f"[LLM] rewrite skipped: {self.last_error}")
            return None

        try:
            from openai import OpenAI

            client = OpenAI(api_key=llm_config.api_key, base_url=llm_config.base_url)
            prompt = (
                "你是一个学术表达优化助手。请根据原句、段落上下文、RAG参考资料和用户历史分析记忆，"
                "给出可以直接复制使用的中文学术表达改写句。不要声称可以规避检测，不要编造不存在的数据。"
                "每条建议都要围绕原句的真实含义展开，并尽量补充研究对象、场景、证据或限定条件。\n\n"
                f"写作风格: {style}\n"
                f"建议数量: {count}\n"
                f"用户历史分析记忆: {memory_context}\n"
                f"原句: {sentence}\n"
                f"段落上下文: {paragraph_text}\n"
                f"RAG参考: {'；'.join(rag_references)}\n\n"
                "请只输出改写建议，一行一条。"
            )
            response = client.chat.completions.create(
                model=llm_config.model,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.4,
            )
            content = response.choices[0].message.content or ""
            lines = [line.strip(" -0123456789.、，") for line in content.splitlines() if line.strip()]
            print(
                "[LLM] rewrite success, "
                f"source={llm_config.source}, model={llm_config.model}, "
                f"base_url={self._safe_base_url(llm_config.base_url)}, suggestions={len(lines[:count])}"
            )
            return lines[:count] or None
        except Exception as exc:
            self.last_error = self._format_llm_error(exc)
            print(
                "[LLM] rewrite failed, fallback used: "
                f"source={llm_config.source}, model={llm_config.model}, "
                f"base_url={self._safe_base_url(llm_config.base_url)}, error={self.last_error}"
            )
            return None

    def _resolve_llm_config(
        self,
        llm_base_url: str | None,
        llm_api_key: str | None,
        llm_model: str | None,
    ) -> LlmConfig:
        user_base_url = self._clean(llm_base_url)
        user_api_key = self._clean(llm_api_key)
        user_model = self._clean(llm_model)

        has_user_config = any([user_base_url, user_api_key, user_model])
        if has_user_config:
            return LlmConfig(
                base_url=self._normalize_base_url(user_base_url),
                api_key=user_api_key,
                model=user_model,
                source="user_settings",
            )

        return LlmConfig(
            base_url=self._normalize_base_url(self._clean(self.settings.llm_base_url)),
            api_key=self._clean(self.settings.llm_api_key),
            model=self._clean(self.settings.llm_model),
            source="env_fallback",
        )

    @staticmethod
    def _clean(value: str | None) -> str | None:
        if value is None:
            return None
        value = str(value).strip()
        return value or None

    @staticmethod
    def _normalize_base_url(base_url: str | None) -> str | None:
        if not base_url:
            return None

        value = base_url.strip().rstrip("/")
        for suffix in ("/chat/completions", "/completions", "/responses"):
            if value.endswith(suffix):
                value = value[: -len(suffix)].rstrip("/")

        parsed = urlparse(value)
        if not parsed.scheme or not parsed.netloc:
            return value

        path = parsed.path.rstrip("/")
        if "dashscope.aliyuncs.com" in parsed.netloc:
            if "/compatible-mode/v1" in path:
                path = path[: path.index("/compatible-mode/v1")] + "/compatible-mode/v1"
            elif not path or path == "/v1":
                path = "/compatible-mode/v1"
            elif not path.endswith("/v1"):
                path = f"{path}/compatible-mode/v1"
            return urlunparse(parsed._replace(path=path))

        if path.endswith("/v1"):
            return urlunparse(parsed._replace(path=path))

        known_openai_compatible_hosts = (
            "api.deepseek.com",
            "api.openai.com",
            "api.moonshot.cn",
            "open.bigmodel.cn",
            "api.siliconflow.cn",
        )
        if any(host in parsed.netloc for host in known_openai_compatible_hosts):
            path = f"{path}/v1" if path else "/v1"
            return urlunparse(parsed._replace(path=path))
        return urlunparse(parsed._replace(path=path))

    @staticmethod
    def _safe_base_url(base_url: str | None) -> str:
        return base_url or "openai_default"

    @staticmethod
    def _format_llm_error(exc: Exception) -> str:
        status_code = getattr(exc, "status_code", None)
        message = str(exc)
        response = getattr(exc, "response", None)
        if response is not None:
            try:
                detail = response.text
                if detail:
                    message = f"{message}; response={detail[:240]}"
            except Exception:
                pass
        if status_code:
            if status_code == 404:
                message += "；如果使用 qwen-max / 通义千问，请确认 Base URL 为 https://dashscope.aliyuncs.com/compatible-mode/v1"
            return f"HTTP {status_code}: {message[:360]}"
        return message[:360]

    @staticmethod
    def _fallback_suggestions(sentence: str, style: str, count: int) -> list[str]:
        style_label = {
            "natural": "自然清晰",
            "concise": "简洁严谨",
            "academic": "正式学术",
        }.get(style, "正式学术")
        safe_count = max(1, min(5, count))
        return [
            f"建议表达 {index + 1}：在{style_label}风格下，可围绕“{sentence}”补充具体研究对象、材料来源或案例条件，使判断更有依据。"
            for index in range(safe_count)
        ]
