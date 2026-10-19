# Third-party components

Updated: 2026-10-14

Mitsukotoba Android downloads or uses third-party software, models, and data.
Each component remains subject to its own license. See also `LICENSE_AUDIT.md`.

## Adopted prerecorded voice

The current fixed English bank uses audio generated locally with Qwen3-TTS.
The APK bundles AAC audio and its provenance manifest; it does not bundle the
Qwen3 model weights or MLX runtime. Android decodes AAC with platform media APIs.
The Kitten generation path described below remains for uncatalogued free text
when its model is installed.

- Reference: synthesized VoiceDesign `surprise_02`, selected by the user, seed1202.
- Generation: `mlx-community/Qwen3-TTS-12Hz-1.7B-Base-8bit`, revision
  `e7dd0585652209fa0d7783659aad4e8a324de11c`, seed1202 per utterance.
- Model card license: Apache-2.0; [pinned model card](https://huggingface.co/mlx-community/Qwen3-TTS-12Hz-1.7B-Base-8bit/blob/e7dd0585652209fa0d7783659aad4e8a324de11c/README.md).
- Upstream: https://github.com/QwenLM/Qwen3-TTS
- Full text/audio hashes, reference hash, source commits, and encoding details:
  `app/src/main/assets/voice-1202/manifest.json`.

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

The v1.9.19 stable release and the v1.9.20 / v1.9.21 / v1.9.22 / v1.9.23 / v1.9.24
continuation releases passed source, DEX, and APK checks for known
eSpeak/sherpa TTS payloads. v1.9.21 changed Lite topic detection and
documentation links. v1.9.22 bundled the topic guide inside the app for
offline viewing. v1.9.23 only reorganizes Settings so shared speech
recognition appears above Lite/Full selection and descriptions. v1.9.24
adds shared topic-test data and generates the offline guide from that data.
None of these
releases adds or replaces any TTS/runtime dependency. This means "GPL-free"
for the audited TTS execution path; it does not mean the whole dependency
graph is permissive-only. Moonshine/ONNX Runtime provenance can include
separately licensed material such as Eigen under MPL-2.0.

## Major dependency summary

| Component | Version / model | License / terms |
| --- | --- | --- |
| Moonshine Voice | 0.1.5 / Japanese Streaming | MIT |
| Kitten TTS Nano | 0.8 FP32 | Apache-2.0 |
| CMUDict | pinned 74790861… | BSD-style |
| ONNX Runtime Android | 1.23.2 | MIT |
| Ruri v3 Semantic | cl-nagoya/ruri-v3-70m, pinned INT8 ONNX | Apache-2.0 |
| ModernBERT-Ja provenance | Ruri base architecture | MIT |
| Eigen provenance | ORT / native dependency surface | MPL-2.0 |
| OkHttp | 4.12.0 | Apache-2.0 |
| AndroidX WorkManager | 2.12.0 | Apache-2.0 |

## Moonshine Voice / Japanese Streaming STT

- Upstream: https://github.com/moonshine-ai/moonshine
- Android package: `ai.moonshine:moonshine-voice:0.1.5`
- Project/runtime license: MIT
- Used for Japanese speech recognition in Lite.

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
- Used to execute the Kitten FP32 ONNX model directly and shared with Moonshine.

Moonshine 0.1.5 ships a reduced `libonnxruntime.so` in its Android AAR.
Mitsukotoba removes that duplicate through a Gradle Artifact Transform and
packages the official full ONNX Runtime Android 1.23.2 library instead. CI
verifies the packaged arm64-v8a and armeabi-v7a runtime.

ONNX Runtime has its own ThirdPartyNotices. In particular, its dependency
surface includes Eigen material under MPL-2.0. MPL-2.0 is not GPL, but its
notice/source-availability obligations remain applicable.

## Ruri v3 70M Semantic topic model

- Upstream model: `cl-nagoya/ruri-v3-70m`
- Source revision: `07a8b0aba47d29d2ca21f89b915c1efe2c23d1cc`
- Android/Web shared INT8 model SHA-256:
  `bd500193003fdeaba8c5422a47b974b40b49a5e0c91285c76f6bd949d2205264`
- Ruri model license: Apache-2.0
- ModernBERT-Ja provenance notice: MIT
- Tokenizer provenance notice: MIT

Android v1.9.39+ downloads the same pinned Semantic Lite model, tokenizer and
topic-classification head used by the Web app. The model is executed locally
with the existing ONNX Runtime Android dependency; no parent speech is sent to
a cloud inference service. The copied notices and model card are under
`licenses/semantic/`.
## OkHttp

- Version: 4.12.0
- Upstream: https://square.github.io/okhttp/
- License: Apache-2.0
- Used for model downloads, redirects, retry-on-connection-failure, and HTTP
  Range resume.

## AndroidX WorkManager

- Version: 2.12.0
- Upstream: https://developer.android.com/jetpack/androidx/releases/work
- License: Apache-2.0
- Used for foreground/background model preparation and retry scheduling.

## v1.9.48 Full/Gemma removal

Full and Gemma were removed from the Android source and runtime dependencies in v1.9.48. LiteRT-LM is no longer packaged. Legacy Gemma files stored in private app storage are removed upon upgrade; Moonshine, Ruri and recorded speech remain.

## v1.9.25 character colors

v1.9.25 changes character palettes and palette selection only. It adds four selectable soft palettes and vivid accents on white faces and bodies, using the same colors as Web. It retains the existing TTS/runtime dependency set and APK provenance checks.

## v1.9.26 character color update

White/black/red is merged into vivid Coral red; color shift uses the same vivid accents on a white face and body as Web. This update changes no dependencies, bundled models, or license declarations.

## v1.9.27 character color update

Adds Filled mode and per-mode Gradient selections. This update changes no dependencies, bundled models, or license declarations.

## v1.9.28 character color update

Brightens Vivid and Filled colors, adds gradient explanations, and uses a 60-second cycle. No dependencies, bundled models, or license declarations change.

## Silero VAD for microphone endpoint detection

- Source: https://github.com/snakers4/silero-vad
- Pinned revision: `1e261b036686cd0017d500ee96acd1c4ba572a9d`.
- License: MIT.
- Bundled ONNX model: 2,327,524 bytes; SHA-256 `1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3`.
- Model and notice are bundled together; see `app/src/main/assets/vad/model-provenance.json` and `LICENSE-SILERO-VAD-MIT.txt` in that directory.
- Runs through the existing full ONNX Runtime Android 1.23.2. This adds no eSpeak or sherpa TTS dependency.

## Multilingual Whisper Small and E5

Non-Japanese mode packages a local Transformers.js 3.8.1 (Apache-2.0) /
ONNX Runtime Web 1.30.0 (MIT) worker. It downloads pinned multilingual
Whisper Small (OpenAI MIT; Xenova conversion Apache-2.0) and multilingual E5
Small (Microsoft MIT) only when needed. No speech is sent to cloud inference.
See `app/src/main/assets/multilingual/licenses/multilingual/NOTICE.md`, the
included license copies and pinned `src/multilingual/assets.json`.
Japanese native Moonshine/Ruri assets and notices remain unchanged.
