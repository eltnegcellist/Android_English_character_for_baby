# Third-party components

Updated: 2026-10-01

Mitsukotoba Android downloads or uses third-party software, models, and data.
Each component remains subject to its own license. See also `LICENSE_AUDIT.md`.

## GPL / eSpeak NG status

Starting with the v1.9.19 implementation, Android Kitten TTS no longer uses
sherpa-onnx's eSpeak-backed Kitten path and no longer downloads
`espeak-ng-data`.

The TTS path is:

```text
English text
→ Mitsukotoba CMUDict phonemizer
→ Kitten token IDs
→ Kitten Nano FP32 ONNX
→ ONNX Runtime Android
→ PCM audio
```

The build no longer depends on `sherpa-onnx-static-1.13.8.aar`.

This means "GPL-free" for the audited TTS execution path; it does not mean the
whole dependency graph is permissive-only. Moonshine/ONNX Runtime provenance
can include separately licensed material such as Eigen under MPL-2.0.

## Moonshine Voice / Japanese Streaming STT

- Upstream: https://github.com/moonshine-ai/moonshine
- Android package: `ai.moonshine:moonshine-voice:0.1.5`
- Project/runtime license: MIT
- Used for Japanese speech recognition in Lite and Full.

Mitsukotoba uses the Streaming Japanese model family, not Moonshine's legacy
non-commercial Japanese Base/Tiny models.

## Kitten TTS Nano 0.8 FP32

- Model: https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32
- Upstream project: https://github.com/KittenML/KittenTTS
- License: Apache-2.0
- Model file pinned by SHA-256:
  `320564d2615f235de972ca27a7f39551c94185cfa24ca85b07a29084135f1e5e`
- Voice archive pinned by SHA-256:
  `8aa7cee235abb0739cb51e6559685f65a4dacd95568833d05699b1633f519b3f`

Mitsukotoba uses Kiki / `expr-voice-5-f`.

## CMU Pronouncing Dictionary (CMUDict)

- Upstream: https://github.com/cmusphinx/cmudict
- Pinned revision: `74790861f652b15e4ac49015a90074ad62a27690`
- SHA-256:
  `81917843c7f44ce2b094ac63873c2c7a4cf802040792c455ba3ca406891c3d22`
- License: BSD-style CMUDict license

CMUDict is the primary English pronunciation dictionary for the Android
phonemizer. Mitsukotoba adds local ARPABET→IPA conversion, heteronym context
rules, infant-directed vocabulary exceptions, and a small OOV fallback.

Copyright (C) 1993-2015 Carnegie Mellon University. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the applicable CMUDict copyright,
conditions, and disclaimer are retained/reproduced as required by its license.

## ONNX Runtime Android

- Maven: `com.microsoft.onnxruntime:onnxruntime-android:1.23.2`
- Upstream: https://github.com/microsoft/onnxruntime
- License: MIT
- Used to execute the Kitten FP32 ONNX model directly.

ONNX Runtime has its own ThirdPartyNotices. In particular, its dependency
surface includes Eigen material under MPL-2.0. MPL-2.0 is not GPL, but its
notice/source-availability obligations remain applicable.

## LiteRT-LM and Gemma

Full optionally uses LiteRT-LM and a local Gemma model. The model is downloaded
separately and is not committed to this repository. Their applicable licenses
and notices remain independent from the Kitten TTS GPL-removal work.
