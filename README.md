# Emma — Local English companion for families with babies

Emma is a local-first English companion designed for families with babies.

親は普段どおり日本語で赤ちゃんに話しかけます。Emmaはその日本語を単純に英訳するのではなく、「いま何が起きているか」を理解するためのコンテキストとして扱い、その場面に合った短くやさしい英語で赤ちゃんに話しかけます。

## Stable release

**Current stable Android release: v1.5.0**  
**versionCode: 75**

- Release: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/tag/emma-v1.5.0
- Direct APK: https://github.com/eltnegcellist/Android_English_character_for_baby/releases/download/emma-v1.5.0/EmmaLocal-v1.5.0-android-arm-debug.apk
- Web edition: https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

v1.5.0 is the current stable application baseline. The stable tag/release is treated as fixed; later development should use a new version rather than replacing the v1.5.0 release.

> The distributed APK is currently CI debug-signed. It is a stable project baseline, but not a Play Store production-signed build.

## Editions

Android Emma has two editions.

| Edition | Response engine | Main purpose |
| --- | --- | --- |
| **Lite** | LiteResponseEngine | Lightweight, predictable, fully local responses |
| **Full** | Gemma + recent conversation context | More flexible, context-aware responses |

Lite and Full share the same basic speech input/output stack. The main difference is how Emma decides what to say.

## Emma Lite

```text
Microphone
  ↓
Moonshine Japanese Tiny / Small Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 / Kiki
  ↓
Emma avatar
```

- Japanese ASR: Moonshine Japanese Tiny / Small Streaming
- Lite default: Tiny
- TTS: Kitten TTS Nano 0.8 / Kiki
- Processing after setup: on-device
- Small ASR can also be selected manually

Lite is designed to stay compact and predictable. It chooses from a curated response system rather than asking a generative model to create every utterance.

## Emma Full

Full keeps the same speech stack and adds Gemma for response generation.

```text
Microphone
  ├─→ Moonshine Japanese Tiny / Small Streaming → Japanese transcript ─┐
  └──────────────── original audio ────────────────────────────────────┤
                                                                       ↓
                                                              Gemma 4 E2B
                                                    + recent conversation history
                                                                       ↓
                                                              English response
                                                                       ↓
                                                      Kitten TTS Nano / Kiki
                                                                       ↓
                                                                Emma avatar
```

The Moonshine transcript is the primary source for linguistic meaning. Original audio can provide secondary context for information that text may lose, such as intonation, laughter, cooing, babbling, squealing, or crying.

Full requires more than 2 GB of additional local Gemma model data.

## First launch

On first launch, the family selects:

- **Lite** — Moonshine Tiny by default + LiteResponseEngine + Kitten TTS
- **Full** — Moonshine Small by default + Gemma + Kitten TTS

The selected edition is saved and can later be changed from Settings.

Legacy saved edition values are migrated to the current Lite/Full structure.

## Shared features

Lite and Full intentionally share:

- Moonshine Japanese Tiny / Small Streaming ASR
- Kitten TTS Nano 0.8 / Kiki
- automatic speech endpoint detection and reply flow
- baby-name pronunciation settings
- optional `-chan` suffix
- family / gender settings
- appearance settings
- non-verbal baby response behavior
- screen-awake preference

The edition boundary is intentionally simple:

- **Lite** = lightweight fixed response knowledge
- **Full** = generative response using transcript + secondary audio context + recent conversation history

## Web edition

Emma also has a browser-based Lite edition that does not require APK installation.

**Open Emma Web:**  
https://eltnegcellist.github.io/Web_EmmaLocal_English_for_babies/

**Web repository:**  
https://github.com/eltnegcellist/Web_EmmaLocal_English_for_babies

The Web edition is Lite-only. Full is provided by the Android application.

## Privacy and local processing

Emma is designed around local processing.

- Speech recognition runs locally after the required model files are downloaded.
- Response selection/generation runs locally.
- Speech synthesis runs locally.
- Recorded speech and generated conversation are not intended to be sent to a cloud AI API.
- Network access is primarily used for downloading required model files.

This public repository does not intentionally contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

## Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for initial model downloads
- Sufficient free storage for the selected edition
- ARM Android device (`arm64-v8a` or `armeabi-v7a`)

## Build environment

The current project uses:

- Java 17
- Android Gradle Plugin 9.3.0
- Kotlin 2.3.21
- compileSdk 37.1
- targetSdk 36
- sherpa-onnx 1.13.8
- Moonshine Voice 0.1.5

The sherpa-onnx Android runtime is intentionally not committed to the repository.

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar \
  -o app/libs/sherpa-onnx-static-1.13.8.aar
```

Then build with:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Models and major dependencies

- Moonshine Voice / Moonshine Japanese Tiny/Small Streaming — MIT
- Kitten TTS Nano 0.8 — Apache-2.0
- sherpa-onnx — Apache-2.0
- LiteRT-LM / Gemma — optional Full mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Project status

**v1.5.0 is the current stable baseline.**

Emma remains an independently developed project and does not claim to guarantee language-learning outcomes or replace professional guidance about child development.

Future functional changes should be released under a new version so that v1.5.0 remains reproducible as the stable reference point.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
