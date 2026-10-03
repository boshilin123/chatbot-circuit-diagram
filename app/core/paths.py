# 统一数据路径：从当前源文件位置推导项目根目录，避免数据路径依赖启动目录。

from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = PROJECT_ROOT / "data"
DEFAULT_CIRCUIT_DATA_PATH = DATA_DIR / "circuit-data.csv"
DEFAULT_KEYWORDS_PATH = DATA_DIR / "keywords.txt"
