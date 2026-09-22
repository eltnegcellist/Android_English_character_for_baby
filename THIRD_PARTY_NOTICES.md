# Third-party components

Emma downloads or uses third-party software and model files. Each component remains subject to its own license.

## OpenAI Whisper tiny multilingual

- Upstream: https://github.com/openai/whisper
- License: MIT
- Used for Japanese speech recognition in standard Emma.

The Android app uses a sherpa-onnx-compatible Whisper tiny package and verifies the encoder and decoder SHA-256 hashes before installation.

## sherpa-onnx

- Upstream: https://github.com/k2-fsa/sherpa-onnx
- License: Apache-2.0
- Used as the Android runtime for Whisper and Kokoro.

## Kokoro-82M

- Upstream: https://github.com/hexgrad/kokoro
- Model: https://huggingface.co/hexgrad/Kokoro-82M
- License: Apache-2.0
- Emma uses the Kokoro model through sherpa-onnx.

The Kokoro distribution also contains additional data such as espeak-ng resources. Those components retain their own license and notice requirements.

## LiteRT-LM and Gemma

Emma Full optionally uses LiteRT-LM and a local Gemma 4 E2B model. The model is downloaded separately and is not committed to this repository.

Before redistributing a packaged APK or model bundle, verify the current license and notice requirements for every included dependency and model.
