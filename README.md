# Emma — Local English companion for families with babies

Emma is an Android app that lets a parent speak naturally in Japanese while an on-device English character responds to the baby in short, simple English.

The goal is not literal translation. The parent's Japanese speech is treated as context for what is happening now, and Emma joins the moment as an English-speaking companion.

## Features

- **Emma (standard mode)** — Japanese speech recognition with Whisper tiny, scene matching, short pre-generated English responses, and Kokoro TTS. Gemma is not required at runtime.
- **Emma Full** — optional local Gemma 4 E2B model for more flexible responses and parent conversation.
- **Baby-directed speech** — short, rhythmic responses designed to finish quickly.
- **Local-first processing** — speech recognition, response generation/selection, and TTS run on the Android device after model download.
- **Kokoro voice** — warm English speech with sentence-by-sentence playback and mouth animation.
- **Baby name support** — kana names can be romanized automatically; Emma addresses the baby as `Hana-chan`, for example.
- **Multiple visual themes** — including gentle colors, high-contrast themes, and animated color changes.

## How it works

### Standard Emma

```text
Microphone
  ↓
Voice activity / endpoint detection
  ↓
Whisper tiny (Japanese ASR)
  ↓
Scene matching
  ↓
Short English response
  ↓
Kokoro TTS
  ↓
Emma avatar
```

### Emma Full

```text
Microphone
  ↓
Voice activity / endpoint detection
  ↓
Local ASR
  ↓
Gemma 4 E2B
  ↓
English response
  ↓
Kokoro TTS
  ↓
Emma avatar
```

Emma Full requires an additional local model download of more than 2 GB.

## Current version

This public repository starts from **v1.4.0-beta4** (`versionCode 56`).

The standard mode includes 20 everyday baby-care scenes with five response variants per scene. These responses are fixed in the app at runtime so the standard mode remains lightweight.

## Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for the initial model downloads
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

GitHub Actions in this repository performs the same build on `ubuntu-latest`.

## Models and major dependencies

- OpenAI Whisper tiny multilingual — MIT
- sherpa-onnx — Apache-2.0
- Kokoro-82M — Apache-2.0
- LiteRT-LM — used for the optional local Gemma mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Privacy

Emma is designed around local processing. Recorded speech and generated conversation are not intended to be sent to a cloud AI API. Network access is used to download required model files.

Diagnostic information may be stored locally on the device to help troubleshoot audio or model issues.

## Project status

Emma is experimental software. It is not intended to guarantee language-learning outcomes or replace professional guidance about child development.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
