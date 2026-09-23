# Emma — Local English companion for families with babies

Emma is an Android app that lets a parent speak naturally in Japanese while an on-device English character responds to the baby in short, simple English.

The goal is not literal translation. The parent's Japanese speech is treated as context for what is happening now, and Emma joins the moment as an English-speaking companion.

## Current version

The current public baseline is **v1.4.0-beta7** (`versionCode 59`).

The evaluated stable reference remains v1.2.22, while the v1.4 beta line adds the lightweight standard Emma flow, optional Emma Full, first-run model setup, shorter baby-directed replies, and the newer family-facing UI.

## Two modes

### Standard Emma

Standard Emma is designed to start quickly and does not require Gemma at runtime.

```text
Microphone
  ↓
Voice activity / endpoint detection
  ↓
Whisper tiny (Japanese ASR)
  ↓
LiteResponseEngine
  ↓
Short pre-generated English response
  ↓
Kokoro or Android TTS
  ↓
Emma avatar
```

The current Lite response bank is intentionally compact: typically three very short baby-directed sentences, with repetition and rhythm prioritized over long explanations.

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
Kokoro or Android TTS
  ↓
Emma avatar
```

Emma Full requires an additional local model download of more than 2 GB. Selecting the parent conversation mode explains this before setup begins.

## First-run experience

On first launch Emma:

- explains that it is not a literal translation app,
- explains why the parent can keep speaking Japanese,
- lets the family configure optional baby settings,
- prepares the Japanese ASR model,
- recommends the Kokoro `af_heart` voice,
- allows Android TTS as a fallback,
- keeps the main conversation processing on-device after model setup.

## Voice

Kokoro is the recommended voice backend. The current baby-directed default uses a warm female voice with slightly slower pacing and deliberate pauses between short sentences.

Android TTS remains available as a fallback when the user prefers not to install the Kokoro model.

## Privacy

Emma is designed around local processing. Recorded speech and generated conversation are not intended to be sent to a cloud AI API. Network access is used to download required model files.

This public repository does **not** contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

## Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for initial model downloads
- Sufficient free storage for speech and voice models
- Additional storage if Emma Full is enabled

## Building

The project uses Java 17, Android Gradle Plugin 9.3.0, Kotlin 2.3.21, and compileSdk 37.

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

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Models and major dependencies

- OpenAI Whisper tiny multilingual — MIT
- sherpa-onnx — Apache-2.0
- Kokoro-82M — Apache-2.0
- LiteRT-LM — used for optional local Gemma mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Project status

Emma is experimental software. It is not intended to guarantee language-learning outcomes or replace professional guidance about child development.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
