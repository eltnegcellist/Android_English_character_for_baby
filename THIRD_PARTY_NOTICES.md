# Third-party components

Emma downloads or uses third-party software and model files. Each component remains subject to its own license.

## Moonshine Voice / Moonshine Japanese Tiny Streaming

- Upstream: https://github.com/moonshine-ai/moonshine
- Android package: ai.moonshine:moonshine-voice:0.1.5
- License: MIT
- Used for Japanese speech recognition in Emma Lite.

Emma Lite uses the Tiny Streaming architecture and downloads the Japanese model files on first setup.

## Kitten TTS Nano 0.8

- Upstream model: https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8
- Upstream project: https://github.com/KittenML/KittenTTS
- License: Apache-2.0
- Used for local English speech synthesis in Emma Lite.

Emma uses the sherpa-onnx conversion package `kitten-nano-en-v0_8-int8` and the Kiki / `expr-voice-5-f` voice (speaker id 6).

## ReazonSpeech K2 v2

- Upstream: https://huggingface.co/reazon-research/reazonspeech-k2-v2
- License: Apache-2.0
- Used for Japanese speech recognition in Emma Standard.

Emma pins a specific model revision and downloads only the files required for INT8 inference.

## sherpa-onnx

- Upstream: https://github.com/k2-fsa/sherpa-onnx
- License: Apache-2.0
- Used as the Android inference runtime for Kitten TTS and Supertonic 3, and for ReazonSpeech inference.

Emma currently pins sherpa-onnx 1.13.7.

## Supertonic 3

- Upstream model: https://huggingface.co/Supertone/supertonic-3
- Model license: OpenRAIL-M
- Upstream sample/runtime code: MIT
- Used for local English speech synthesis in Emma Standard and Full.

Emma downloads the sherpa-onnx INT8 conversion package and uses the F3 built-in voice style.

## LiteRT-LM and Gemma

Emma Full optionally uses LiteRT-LM and a local Gemma model. The model is downloaded separately and is not committed to this repository.

Before redistributing a packaged APK or model bundle, verify the current license and notice requirements for every included dependency and model.
