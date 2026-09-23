# Third-party components

Emma downloads or uses third-party software and model files. Each component remains subject to its own license.

## ReazonSpeech K2 v2

- Upstream: https://huggingface.co/reazon-research/reazonspeech-k2-v2
- License: Apache-2.0
- Used for Japanese speech recognition in standard Emma.

Emma pins a specific model revision and downloads only the INT8 encoder, decoder, INT8 joiner, and token file required by the Android runtime.

## sherpa-onnx

- Upstream: https://github.com/k2-fsa/sherpa-onnx
- License: Apache-2.0
- Used as the Android inference runtime for ReazonSpeech and Supertonic 3.

Emma currently pins sherpa-onnx 1.13.7.

## Supertonic 3

- Upstream model: https://huggingface.co/Supertone/supertonic-3
- Model license: OpenRAIL-M
- Upstream sample/runtime code: MIT
- Used for local English speech synthesis.

Emma downloads the sherpa-onnx INT8 conversion package and uses the F3 built-in voice style. Model files are not committed to this repository.

## LiteRT-LM and Gemma

Emma Full optionally uses LiteRT-LM and a local Gemma 4 E2B model. The model is downloaded separately and is not committed to this repository.

Before redistributing a packaged APK or model bundle, verify the current license and notice requirements for every included dependency and model.
