import os
import urllib.request
from lib import u8_utils, DEFINITION_AI_LOCALE

class LanguageHsk3Provider(u8_utils.U8Provider):
    """HSK3-level Chinese definitions parsed statically from ccdict.u8.

    Same .u8 logic as the other definition stages: entries are grouped by
    simplified headword and formatted identically. The file was extracted
    from the legacy AiFields LLM cache and is generated externally from
    now on; update() downloads the latest release into the current directory.
    """
    URL = "https://github.com/licryle/CxDICT/releases/download/latest-zh-CN-HSK03/CxDICT-HSK3-SuperFull.u8"
    U8_FILE = os.path.join(os.path.dirname(__file__), "CxDICT-HSK3-SuperFull.u8")
    LANGUAGE = DEFINITION_AI_LOCALE

    def update(self):
        self.logger.info(f"Downloading HSK3 dictionary from {self.URL}...")
        req = urllib.request.Request(self.URL, headers={"User-Agent": "MandarinAssistant/1.0"})
        with urllib.request.urlopen(req) as response, open(self.U8_FILE, "wb") as f:
            f.write(response.read())
