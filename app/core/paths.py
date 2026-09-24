from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = PROJECT_ROOT / "data"
DEFAULT_CIRCUIT_DATA_PATH = DATA_DIR / "circuit-data.csv"
DEFAULT_KEYWORDS_PATH = DATA_DIR / "keywords.txt"
