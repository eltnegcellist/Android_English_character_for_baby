# Emma — Local English companion for families with babies

Emma is an Android app that lets a parent speak naturally in Japanese while an on-device English character responds to the baby in short, simple English.

The goal is not literal translation. The parent's Japanese speech is treated as context for what is happening now, and Emma joins the moment as an English-speaking companion.

## Current version

The current public baseline is **v1.4.0-beta14** (`versionCode 66`).

Android Emma now contains three editions. They share the same family settings, avatar, endpoint detection, baby-name handling, and local-first design. Lite and Standard also share the same `LiteResponseEngine`; their main difference is the speech stack.

## Emma Lite

The lightest edition, aligned with Emma Web Lite.

```text
Microphone
  ↓
Moonshine Japanese Tiny Streaming
  ↓
LiteResponseEngine
  ↓
Kitten TTS Nano 0.8 INT8 / Kiki
  ↓
Emma avatar
```

- Japanese ASR: Moonshine Tiny Streaming
- TTS: Kitten TTS Nano 0.8 INT8, Kiki
- Approximate model download: about 64 MB
- Processing after setup: on-device
- Response logic: same LiteResponseEngine as Standard

The Web edition uses the same model families through browser/WASM runtimes; Android Lite uses native Android runtimes.

## Emma Standard

In beta12, the voice path is fixed by edition: Lite uses Kitten TTS Nano, while Standard and Full use Supertonic 3 F3. Android system TTS is not used as a fallback.

The Android-oriented default edition.

```text
Microphone
  ↓
ReazonSpeech K2 v2 INT8
  ↓
LiteResponseEngine
  ↓
Supertonic 3 F3
  ↓
Emma avatar
```

- Japanese ASR: ReazonSpeech K2 v2 INT8
- TTS: Supertonic 3 F3 female voice (24 kHz output)
- Approximate model download: about 298 MB
- Processing after setup: on-device
- Response logic: same LiteResponseEngine as Lite

## Emma Full

Emma Full adds a local Gemma model for more flexible responses and parent conversation.

```text
Microphone
  ↓
Local Gemma speech/context processing
  ↓
Gemma response generation
  ↓
Supertonic 3 F3
  ↓
Emma avatar
```

Full requires more than 2 GB of additional local model data.

## First-run experience

Beta12 introduces a new onboarding revision so existing installations are shown the Lite / Standard / Full choice once after updating. After that choice is completed, the selected edition is retained.

On first launch, the family chooses one of:

- **Lite** — lightest; Moonshine + Kitten TTS Nano, about 64 MB
- **Standard** — Android default; ReazonSpeech + Supertonic 3, about 298 MB
- **Full** — Gemma-powered; more than 2 GB

The selected edition is prepared automatically. The edition can also be changed later from Settings.

Existing installations from beta9 and earlier that stored the old Android `LITE` mode are migrated to **Standard**, because that old mode used ReazonSpeech + Supertonic and corresponds to the new Standard edition.

## Shared behavior

Lite and Standard intentionally share:

- the same LiteResponseEngine scene/reply logic,
- baby name pronunciation and optional `-chan` suffix,
- family/gender settings,
- appearance settings,
- automatic endpoint detection and reply flow,
- non-verbal baby response behavior,
- screen-awake preference.

## Privacy

Emma is designed around local processing. Recorded speech and generated conversation are not intended to be sent to a cloud AI API. Network access is used to download required model files.

This public repository does **not** intentionally contain signing keys, keystores, passwords, API tokens, private runner configuration, or family-specific private data.

## Requirements

- Android 9 (API 28) or later
- Microphone permission
- Internet access for initial model downloads
- Sufficient free storage for the selected edition

## Building

The project uses Java 17, Android Gradle Plugin 9.3.0, Kotlin 2.3.21, compileSdk 37.1, sherpa-onnx 1.13.8, and Moonshine Voice 0.1.5.

The sherpa-onnx Android runtime is intentionally not committed to this repository. Download the pinned AAR before building:

```bash
mkdir -p app/libs
curl -fL \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-static-link-onnxruntime-1.13.8.aar \
  -o app/libs/sherpa-onnx-static-1.13.8.aar
```

Then run:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Models and major dependencies

- Moonshine Voice / Moonshine Japanese Tiny Streaming — MIT
- Kitten TTS Nano 0.8 — Apache-2.0
- ReazonSpeech K2 v2 — Apache-2.0
- sherpa-onnx — Apache-2.0
- Supertonic 3 model — OpenRAIL-M
- LiteRT-LM / Gemma — used for optional Full mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Project status

Emma is experimental software. It is not intended to guarantee language-learning outcomes or replace professional guidance about child development.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
