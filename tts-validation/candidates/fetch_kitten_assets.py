#!/usr/bin/env python3
"""Download pinned baseline archive in quarantine; extract only approved assets.

Never extracts the bundled eSpeak data. Temporary archive is discarded even on
error. This is a lab acquisition tool, not a proposed Android downloader.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import tarfile
import tempfile
import urllib.request
from isolated_onnx import ASSET_HASHES

URL='https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kitten-nano-en-v0_8-fp32.tar.bz2'
ARCHIVE_SHA256='16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e'
PREFIX='kitten-nano-en-v0_8-fp32/'
HASHES={**ASSET_HASHES,'README.md':'e8751a029481521364265c7c95acf0394fa3580671f19e99c1e9ce51c74ba9d6'}


def main():
    p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args()
    if a.output.exists() and any(a.output.iterdir()):raise ValueError('Output must be empty; assets will not be overwritten')
    with tempfile.TemporaryDirectory(prefix='kitten-quarantine-') as temporary:
        root=Path(temporary);archive=root/'reference.tar.bz2'
        with urllib.request.urlopen(URL,timeout=60) as response, archive.open('wb') as dest:
            shutil.copyfileobj(response,dest)
        if hashlib.sha256(archive.read_bytes()).hexdigest()!=ARCHIVE_SHA256:raise ValueError('Archive checksum mismatch')
        approved={}
        with tarfile.open(archive) as tar:
            for name,digest in HASHES.items():
                member=tar.getmember(PREFIX+name)
                if not member.isfile() or member.size>128*1024*1024:raise ValueError('Invalid member: '+name)
                content=tar.extractfile(member).read()
                if hashlib.sha256(content).hexdigest()!=digest:raise ValueError('Checksum mismatch: '+name)
                approved[name]=content
        a.output.mkdir(parents=True,exist_ok=True)
        for name,content in approved.items():(a.output/name).write_bytes(content)
    print('Verified and selected: '+', '.join(sorted(HASHES)))

if __name__=='__main__':main()
