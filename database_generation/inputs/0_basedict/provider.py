import os
import urllib.request
from typing import Dict, Any, Iterator, Tuple
from lib import ProviderType, build_u8_searchable_text
from lib import u8_utils

class BaseDictProvider(u8_utils.U8Provider):
    """English definitions parsed statically from CxDICT-English-SuperFull.u8.

    Using the Super avoids removed headwords management.

    Same .u8 logic as the base dictionary stage: entries are grouped by
    simplified headword and formatted identically. The file is generated
    externally; update() downloads the latest release into the current directory.

    With a distinction, because we're the base_dict, also yield chinese_word records.
    """
    URL = "https://github.com/licryle/CxDICT/releases/download/latest-en/CxDICT-English-SuperFull.u8"
    U8_FILE = os.path.join(os.path.dirname(__file__), "CxDICT-English-SuperFull.u8")
    LANGUAGE = "en"
    FILTER_TO_BASE = False

    def update(self):
        self.logger.info(f"Downloading English base dictionary from {self.URL}...")
        req = urllib.request.Request(self.URL, headers={"User-Agent": "MandarinAssistant/1.0"})
        with urllib.request.urlopen(req) as response, open(self.U8_FILE, "wb") as f:
            f.write(response.read())

    def schema(self) -> Dict[str, Dict[str, Any]]:
        schema = super().schema()
        schema["chinese_word"] = {
            "type": ProviderType.TABLE,
            "columns": ["simplified", "traditional", "pinyins", "hsk_level", "searchable_text"]
        }
        return schema

    def data(self) -> Iterator[Tuple[str, Dict[str, Any]]]:
        grouped = self._read_grouped()
        if not grouped:
            return

        for simplified, data in grouped.items():
            traditional, pinyins = data["display"]
            yield ("chinese_word", {
                "simplified": simplified,
                "traditional": traditional,
                "pinyins": pinyins,
                "hsk_level": "NOT_HSK",
                "searchable_text": build_u8_searchable_text(simplified, data["entries"])
            })

        yield from self._yield_definitions(grouped)
