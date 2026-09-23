# Emma — Local English companion for families with babies

Emma is an Android app that lets a parent speak naturally in Japanese while an on-device English character responds to the baby in short, simple English.

The goal is not literal translation. The parent's Japanese speech is treated as context for what is happening now, and Emma joins the moment as an English-speaking companion.

## Current version

The current public baseline is **v1.4.0-beta15** (`versionCode 67`).

Android Emma now contains two editions: **Lite** and **Full**. They share the same speech input/output stack; the main difference is how Emma decides what to say.

## Emma Lite

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
- Approximate speech-model download: about 64 MB
- Processing after setup: on-device

## Emma Full

Full reuses the exact same ASR and TTS stack. The only large addition is Gemma.

```text
Microphone
  ├─→ Moonshine Japanese Tiny Streaming ─→ Japanese transcript ─┐
  └──────────────────── original audio ──────────────────────────┤
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

The Moonshine transcript is the **primary source for linguistic meaning**. The original audio is supplied to Gemma only as secondary context for information that text can lose, such as intonation, laughter, cooing, babbling, squealing, or crying. A clear transcript is not overridden by Gemma's own interpretation of the waveform.

Full requires more than 2 GB of additional local Gemma model data.

## First-run experience

On first launch, the family chooses one of:

- **Lite** — Moonshine + LiteResponseEngine + Kitten TTS Nano, about 64 MB of speech models
- **Full** — the same Moonshine + Kitten stack, plus Gemma for context-aware response generation

The selected edition is retained and can be changed later from Settings.

Existing saved `STANDARD`, legacy `LITE`, and `WEB_LITE` values are migrated to the current **Lite** edition.

## Shared speech stack

Lite and Full intentionally share:

- Moonshine Japanese Tiny Streaming for Japanese ASR,
- Kitten TTS Nano 0.8 / Kiki for English speech,
- baby-name pronunciation and optional `-chan` suffix,
- family/gender settings,
- appearance settings,
- automatic endpoint detection and reply flow,
- non-verbal baby response behavior,
- screen-awake preference.

The edition boundary is therefore simple:

- **Lite** = fixed lightweight response knowledge
- **Full** = Gemma generates the response from transcript + secondary audio context + conversation history

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
- sherpa-onnx — Apache-2.0
- LiteRT-LM / Gemma — used for optional Full mode

Model files are downloaded separately and are not committed to this repository.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for additional information.

## Project status

Emma is experimental software. It is not intended to guarantee language-learning outcomes or replace professional guidance about child development.

## License

No license for the Emma application source code has been granted yet. Third-party components remain subject to their respective licenses.
