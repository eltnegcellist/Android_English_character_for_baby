"""Source fingerprints prevent reuse of a passed gate after dependency edits."""
import hashlib
from pathlib import Path
HERE=Path(__file__).resolve().parent


def frontend_fingerprint():
    files=[*sorted((HERE/'candidates').rglob('*.py')),HERE/'candidates/source-manifest.json',Path(__file__)]
    return {str(p.relative_to(HERE)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
