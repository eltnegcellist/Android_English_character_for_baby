# Emma — Local English companion for families with babies

Emma is an Android app that lets a parent speak naturally in Japanese while an on-device English character responds to the baby in short, simple English.

The goal is not literal translation. The parent's Japanese speech is treated as context for what is happening now, and Emma joins the moment as an English-speaking companion.

## Current version

The current public baseline is **v1.4.0-beta8** (`versionCode 60`).

This beta replaces the standard-mode speech stack:

- Japanese ASR: Whisper tiny → **ReazonSpeech K2 v2 (INT8 Zipformer)**
- Emma voice: Kokoro → **Supertonic 3 F3**
- Android TTS remains available as a fallback.

## Two modes

### Standard Emma

Standard Emma does not require Gemma at runtime.

```text
Microphone
  ↓
Voice activity / endpoint detection
  ↓
ReazonSpeech K2 v2 INT8 (Japanese ASR)
  ↓
LiteResponseEngine
  ↓
Short pre-generated English response
  ↓
Supertonic 3 F3 (or Android TTS fallback)
  ↓
Emma avatar
```

The Lite response bank is intentionally compact: typically three very short baby-directed sentences, with repetition and rhythm prioritized over long explanations.

### Emma Full

Emma Full is optional and uses a local Gemma model for more flexible responses and parent conversation.

```text
Microphone
  ↓
Voice activity / endpoint detection
  ↓
Local Gemma ASR
  ↓
Gemma 4 E2B
  ↓
English response
  ↓
Supertonic 3 F3 (or Android TTS fallback)
  ↓
Emma avatar
```

Emma Full requires an additional local model download of more than 2 GB.

## First-run experience

On first launch Emma:

- explains that it is not a literal translation app,
- lets the family configure optional baby settings,
- downloads the ReazonSpeech Japanese ASR files (about 169 MB),
- recommends Supertonic 3 F3 (about 129 MB),
- allows Android TTS as a fallback,
- keeps recognition, response selection/generation, and speech synthesis on-device after model setup.

The standard recommended setup is about 298 MB of downloaded speech models.

## ASR

Standard Emma uses ReazonSpeech K2 v2, a Japanese RNN-T/Zipformer model. Emma downloads only the files needed for INT8 inference (INT8 encoder and joiner, decoder, and tokens) from a pinned model revision instead of downloading the larger package containing unused full-precision variants.

## Voice

Supertonic 3 F3 is the recommended voice backend. Emma runs it locally through sherpa-onnx using the official INT8 conversion package. The Android implementation uses 8 generation steps and keeps PCM amplitude-driven lip sync.

Android TTS remains available as a fallback.

## Privacy

Emma is designed around local processing. Recorded speech and generated conversation are not intended to be sent to a cloud AI API. Network access is used to download required model files.

This public repository does **not** intentionally contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

## Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for initial model downloads
- Sufficient free storage for speech and voice models
- Additional storage if Emma Full is enabled

## Building

The project uses Java 17, Android Gradle Plugin 9.3.0, Kotlin 2.3.21, compileSdk 37.1, and sherpa-onnx 1.13.7.

The sherpa-onnx Android runtime is intentionally not committed to this repository. Download the pinned AAR before building:

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-static-link-onnxruntime-1.13.7.aar \
  -o app/libs/sherpa-onnx-static-1.13.7.aar
```

Then run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Models and major dependencies

- ReazonSpeech K2 v2 — Apache-2.0
- sherpa-onnx — Apache-2.0
- Supertonic 3 model — OpenRAIL-M
- LiteRT-LM — used for optional local Gemma mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Project status

Emma is experimental software. It is not intended to guarantee language-learning outcomes or replace professional guidance about child development.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
