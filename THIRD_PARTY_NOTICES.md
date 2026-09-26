# Third-party components

Emma downloads or uses third-party software and model files. Each component remains subject to its own license.

## Moonshine Voice / Moonshine Japanese Streaming

- Upstream: https://github.com/moonshine-ai/moonshine
- Android package: ai.moonshine:moonshine-voice:0.1.5
- License: MIT
- Used for Japanese speech recognition in Emma Lite and Emma Full.

Emma downloads the selected Japanese Tiny/Small Streaming model files during setup.

## Kitten TTS Nano 0.8 FP32

- Upstream model: https://huggingface.co/KittenML/kitten-tts-nano-0.8-fp32
- Upstream project: https://github.com/KittenML/KittenTTS
- License: Apache-2.0
- Voice: Kiki / `expr-voice-5-f`
- Used for local English speech synthesis in Emma Lite and Emma Full.

Emma downloads the official FP32 ONNX model and `voices.npz` from pinned KittenML revision `87b12ff7859cdebd9c055c987a586101fad5b650` and verifies both SHA-256 hashes. The experimental non-GPL TTS branch does not use the
sherpa-onnx Kitten frontend or eSpeak NG data.

## ONNX Runtime Android

- Upstream: https://github.com/microsoft/onnxruntime
- Android package: com.microsoft.onnxruntime:onnxruntime-android:1.23.2
- License: MIT
- Used to execute the Kitten TTS ONNX model directly on Android.

## CMU Pronouncing Dictionary (CMUDict)

- Upstream: https://github.com/cmusphinx/cmudict
- Pinned commit: `74790861f652b15e4ac49015a90074ad62a27690`
- License: BSD-style CMUdict license
- Used as the primary English pronunciation dictionary for Kitten TTS.

Emma converts CMUDict ARPABET entries to the IPA symbol inventory expected by
Kitten TTS, with local context rules for homographs and a dedicated pronunciation
path for the configured Japanese baby name.

## LiteRT-LM and Gemma

Emma Full optionally uses LiteRT-LM and a local Gemma model. The model is downloaded separately and is not committed to this repository.

Before redistributing a packaged APK or model bundle, verify the current license and notice requirements for every included dependency and model.
