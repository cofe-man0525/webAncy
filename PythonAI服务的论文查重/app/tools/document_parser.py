from pathlib import Path

import fitz
import pdfplumber
from docx import Document
from PIL import Image

from app.config import get_settings


class DocumentParseTool:
    name = "document_parse_tool"

    def __init__(self):
        self.settings = get_settings()

    def run(self, storage_path: str) -> str:
        path = self._resolve_path(storage_path)
        suffix = path.suffix.lower()
        if suffix in {".docx", ".doc"}:
            return self._read_docx(path)
        if suffix == ".pdf":
            return self._read_pdf(path)
        if suffix in {".txt", ".md"}:
            return path.read_text(encoding="utf-8", errors="ignore")
        if suffix in {".png", ".jpg", ".jpeg", ".webp"}:
            return self._read_image(path)
        return path.read_text(encoding="utf-8", errors="ignore")

    def _resolve_path(self, storage_path: str) -> Path:
        raw_path = Path(storage_path)
        if raw_path.exists():
            return raw_path
        resolved = self.settings.java_backend_root / raw_path
        if resolved.exists():
            return resolved
        raise FileNotFoundError(f"文件不存在: {storage_path}")

    def _read_docx(self, path: Path) -> str:
        document = Document(path)
        paragraphs = [paragraph.text.strip() for paragraph in document.paragraphs if paragraph.text.strip()]
        return "\n".join(paragraphs)

    def _read_pdf(self, path: Path) -> str:
        texts: list[str] = []
        try:
            with pdfplumber.open(path) as pdf:
                for page in pdf.pages:
                    text = page.extract_text() or ""
                    if text.strip():
                        texts.append(text)
        except Exception:
            pass
        if texts:
            return "\n".join(texts)

        document = fitz.open(path)
        for page in document:
            text = page.get_text("text")
            if text.strip():
                texts.append(text)
        return "\n".join(texts)

    def _read_image(self, path: Path) -> str:
        try:
            import pytesseract

            return pytesseract.image_to_string(Image.open(path), lang="chi_sim+eng").strip()
        except Exception:
            return ""
