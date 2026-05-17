import json
import urllib.request
from urllib.error import URLError

from app.config import get_settings


class WebSearchTool:
    name = "web_search_tool"

    def __init__(self):
        self.settings = get_settings()

    def run(self, query: str, max_results: int | None = None) -> list[str]:
        if not self.settings.web_search_enabled:
            return []
        if self.settings.web_search_provider.lower() != "tavily":
            return []
        if not self.settings.web_search_api_key:
            return []
        return self._search_tavily(query, max_results or self.settings.web_search_max_results)

    def _search_tavily(self, query: str, max_results: int) -> list[str]:
        payload = json.dumps(
            {
                "api_key": self.settings.web_search_api_key,
                "query": query,
                "search_depth": "basic",
                "max_results": max(1, min(max_results, 5)),
            }
        ).encode("utf-8")
        request = urllib.request.Request(
            "https://api.tavily.com/search",
            data=payload,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                data = json.loads(response.read().decode("utf-8"))
        except (URLError, TimeoutError, json.JSONDecodeError, OSError):
            return []

        results = []
        for item in data.get("results", [])[:max_results]:
            title = item.get("title") or "搜索结果"
            url = item.get("url") or ""
            content = item.get("content") or ""
            results.append(f"{title}：{content} {url}".strip())
        return results
