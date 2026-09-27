import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Input Directories
INPUTS_DIR = 'inputs'
BASE_DICT_DIR = os.path.join(INPUTS_DIR, '0_basedict')
HSK_DIR = os.path.join(INPUTS_DIR, '1_HSK')
POPULARITY_DIR = os.path.join(INPUTS_DIR, '2_Popularity')
AI_FIELDS_DIR = os.path.join(INPUTS_DIR, '3_AiFields1')
ANNOTATIONS_DIR = os.path.join(INPUTS_DIR, '4_Annotations')
COLLOCATIONS_DIR = os.path.join(INPUTS_DIR, '5_Collocations')
LANGUAGE_FRENCH_DIR = os.path.join(INPUTS_DIR, '6_Language_French')

# File Paths
CEDICT_FILE = os.path.join(BASE_DICT_DIR, 'CxDICT-English-SuperFull.u8')
ANNOTATIONS_FILE = os.path.join(ANNOTATIONS_DIR, 'annotations.csv')
DB_FILE = 'output/Mandarin_Assistant.db'
AI_CACHE_DB = os.path.join(AI_FIELDS_DIR, 'ai_fields_cache.db')
COLLOCATIONS_CACHE_DB = os.path.join(COLLOCATIONS_DIR, 'collocations_cache.db')

# HSK Submodule
HSK_SUBMODULE_PATH = os.path.join(HSK_DIR, 'new_hsk')
HSK_FILES = [
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 7-9.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 6.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 5.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 4.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 3.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 2.txt'),
    os.path.join(HSK_SUBMODULE_PATH, 'HSK List (Frequency)/HSK 1.txt')
]

# AI Configuration
BATCH_SIZE = 10
API_ENDPOINT = os.getenv('LLM_API_ENDPOINT', 'http://localhost:1234/v1/chat/completions')
MODEL_NAME = os.getenv('LLM_MODEL_NAME', 'Yi-1.5-6B-Chat-Q6_K')
MAX_CONSECUTIVE_FAILURES = 10
AI_WORDS_WHERE_CLAUSE = "(hsk_level != 'NOT_HSK')"
DEFINITION_AI_LOCALE = 'zh-CN-HSK03'
