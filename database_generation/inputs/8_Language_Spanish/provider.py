import os
import urllib.request
from lib import u8_utils

class LanguageSpanishProvider(u8_utils.U8Provider):
    """Spanish definitions parsed statically from CxDICT-Spanish-SuperFull.u8.

    Same .u8 logic as the base dictionary stage: entries are grouped by
    simplified headword and formatted identically. The file is generated
    externally; update() downloads the latest release into the current directory.
    """
    URL = "https://github.com/licryle/CxDICT/releases/download/latest-es/CxDICT-Spanish-SuperFull.u8"
    U8_FILE = os.path.join(os.path.dirname(__file__), "CxDICT-Spanish-SuperFull.u8")
    LANGUAGE = "es"

    def update(self):
        self.logger.info(f"Downloading Spanish dictionary from {self.URL}...")
        req = urllib.request.Request(self.URL, headers={"User-Agent": "MandarinAssistant/1.0"})
        with urllib.request.urlopen(req) as response, open(self.U8_FILE, "wb") as f:
            f.write(response.read())
