from pydantic import BaseModel


class AnalysisTaskMessage(BaseModel):
    taskId: int
    userId: int
    paperId: int
    storagePath: str
    depth: str = "standard"
    style: str = "academic"
    enableRag: bool = True
    suggestionCount: int = 2
    llmBaseUrl: str | None = None
    llmApiKey: str | None = None
    llmModel: str | None = None


class SentenceAnalysis(BaseModel):
    paragraph_index: int
    sentence_index: int
    original_text: str
    risk_score: int
    risk_level: str
    reasons: list[str]
    advice: str
    suggested_texts: list[str]
    rag_references: list[str]
