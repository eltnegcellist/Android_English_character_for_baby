# Third-party components

Emma downloads or uses third-party software and model files. Each component remains subject to its own license.

## Moonshine Voice / Moonshine Japanese Streaming

- Upstream: https://github.com/moonshine-ai/moonshine
- Android package: ai.moonshine:moonshine-voice:0.1.5
- License: MIT
- Used for Japanese speech recognition in Emma Lite and Emma Full.

Emma downloads the selected Japanese Tiny/Small Streaming model files during setup.

## Kitten TTS Nano 0.8 FP32

- Upstream model: https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32
- Upstream project: https://github.com/KittenML/KittenTTS
- License: Apache-2.0
- Voice: Kiki / `expr-voice-5-f`
- Used for local English speech synthesis in Emma Lite and Emma Full.

Emma downloads the official FP32 ONNX model and `voices.npz` from pinned KittenML revision `87b12ff7859cdebd9c055c987a586101fad5b650` and verifies both SHA-256 hashes. The experimental non-GPL TTS branch does not use the
sherpa-onnx Kitten frontend or eSpeak NG data.

## ONNX Runtime Android

- Upstream: https://github.com/microsoft/onnxruntime
- Android package: com.microsoft.onnxruntime:onnxruntime-android:1.23.2
- License: MIT
- Used to execute the Kitten TTS ONNX model directly on Android.

## CMU Pronouncing Dictionary (CMUDict)

- Upstream: https://github.com/cmusphinx/cmudict
- Pinned commit: `74790861f652b15e4ac49015a90074ad62a27690`
- License: BSD-style CMUdict license
- Used as the primary English pronunciation dictionary for Kitten TTS.

Emma converts CMUDict ARPABET entries to the IPA symbol inventory expected by
Kitten TTS, with local context rules for homographs and a dedicated pronunciation
path for the configured Japanese baby name.

## LiteRT-LM and Gemma

Emma Full optionally uses LiteRT-LM and a local Gemma model. The model is downloaded separately and is not committed to this repository.

Before redistributing a packaged APK or model bundle, verify the current license and notice requirements for every included dependency and model.


### CMUDict license notice

Copyright (C) 1993-2015 Carnegie Mellon University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.
   The contents of this file are deemed to be source code.

2. Redistributions in binary form must reproduce the above copyright
   notice, this list of conditions and the following disclaimer in
   the documentation and/or other materials provided with the
   distribution.

This work was supported in part by funding from the Defense Advanced
Research Projects Agency, the Office of Naval Research and the National
Science Foundation of the United States of America, and by member
companies of the Carnegie Mellon Sphinx Speech Consortium. We acknowledge
the contributions of many volunteers to the expansion and improvement of
this dictionary.

THIS SOFTWARE IS PROVIDED BY CARNEGIE MELLON UNIVERSITY ``AS IS'' AND
ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
