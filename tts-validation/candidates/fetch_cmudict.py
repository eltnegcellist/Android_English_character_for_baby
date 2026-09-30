"""Fetch the explicitly pinned CMU dictionary, verifying dictionary and notice hashes."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

HERE = Path(__file__).resolve().parent
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    manifest = json.loads((HERE / 'source-manifest.json').read_text())['cmudict']
    args.destination.mkdir(parents=True, exist_ok=True)
    for name, key in [('cmudict.dict', 'dictionary_sha256'), ('LICENSE', 'license_sha256')]:
        url = f"https://raw.githubusercontent.com/cmusphinx/cmudict/{manifest['commit']}/{name}"
        with urllib.request.urlopen(url, timeout=60) as response:
            content = response.read()
        if hashlib.sha256(content).hexdigest() != manifest[key]:
            raise ValueError(f'Checksum mismatch: {name}')
        (args.destination / name).write_bytes(content)
        print(f'Verified {name}')
