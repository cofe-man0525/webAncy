import re


class SentenceSplitTool:
    name = "sentence_split_tool"

    def run(self, text: str) -> list[dict]:
        normalized = text.replace("\r\n", "\n").replace("\r", "\n").strip()
        raw_paragraphs = [item.strip() for item in re.split(r"\n{1,}", normalized) if item.strip()]
        if not raw_paragraphs and normalized:
            raw_paragraphs = [normalized]

        paragraphs = []
        for paragraph_index, paragraph in enumerate(raw_paragraphs, start=1):
            sentences = [
                item.strip()
                for item in re.split(r"(?<=[。！？!?；;])\s*", paragraph)
                if item.strip()
            ]
            if not sentences:
                sentences = [paragraph]
            paragraphs.append(
                {
                    "paragraph_index": paragraph_index,
                    "text": paragraph,
                    "sentences": [
                        {"sentence_index": sentence_index, "text": sentence}
                        for sentence_index, sentence in enumerate(sentences, start=1)
                    ],
                }
            )
        return paragraphs
