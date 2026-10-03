#!/usr/bin/env python3
"""Sync/check the Android mirror against the canonical Web contract at an immutable commit."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'shared/lite-topic-source.json'
REPOSITORY = 'eltnegcellist/Web_EmmaLocal_English_for_babies'
FILES = ['shared/lite-topic-contract.json', 'scripts/generate-lite-topic-contract.py', 'scripts/test-lite-topic-contract.py']


def fetch(url):
    last_error = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'mitsukotoba-topic-contract'})
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except OSError as error:
            last_error = error
            if attempt < 2:
                time.sleep(attempt + 1)
    raise last_error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--check', action='store_true', help='Verify the pinned source; do not change files')
    modes.add_argument('--check-latest', action='store_true', help='Detect upstream drift; do not change files')
    parser.add_argument('--ref', help='Commit to adopt (default: resolve current Web main)')
    args = parser.parse_args()
    if args.ref and (args.check or args.check_latest):
        parser.error('--ref is only used when adopting a source version')
    manifest = json.loads(SOURCE.read_text()) if SOURCE.exists() else None
    if args.check:
        if manifest is None:
            raise ValueError('Missing source manifest; sync first')
        commit = manifest['commit']
    else:
        commit = args.ref or json.loads(fetch(f'https://api.github.com/repos/{REPOSITORY}/git/ref/heads/main'))['object']['sha']
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Use a full immutable commit SHA')
    incoming = {name: fetch(f'https://raw.githubusercontent.com/{REPOSITORY}/{commit}/{name}') for name in FILES}
    hashes = {name: hashlib.sha256(raw).hexdigest() for name, raw in incoming.items()}
    if args.check or args.check_latest:
        stale = [name for name, raw in incoming.items() if not (ROOT / name).exists() or (ROOT / name).read_bytes() != raw]
        if args.check and (manifest['repository'] != REPOSITORY or manifest['sha256'] != hashes):
            raise ValueError('Pinned source hashes do not match the manifest')
        if stale:
            raise ValueError('Shared source drift: ' + ', '.join(stale) +
                             f'. Run python3 scripts/sync-lite-topic-contract.py --ref {commit}, then run both test suites.')
        print(f'Shared source {commit}: ' + ('latest data matches' if args.check_latest else 'pinned copy verified'))
        return
    # Validate all downloads before changing the mirror. The generator comes from the same trusted source commit.
    with tempfile.TemporaryDirectory() as temporary:
        generator = Path(temporary) / 'generator.py'
        generator.write_bytes(incoming[FILES[1]])
        runpy.run_path(str(generator))['validate'](json.loads(incoming[FILES[0]]))
    for name, raw in incoming.items():
        path = ROOT / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    SOURCE.write_text(json.dumps({'repository': REPOSITORY, 'commit': commit, 'sha256': hashes}, indent=2) + '\n')
    subprocess.run([sys.executable, str(ROOT / FILES[1]), '--target', 'android'], check=True)
    print(f'Adopted shared source {commit}; run Android tests before committing.')


if __name__ == '__main__':
    main()
