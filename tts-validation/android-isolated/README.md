# Isolated native alternative — not integrated

`NativeLitePlanner` is an independent Kotlin CMU implementation, **not** a Misaki/spaCy port. It accepts only the exact pinned 202-entry Lite catalogue, plus an explicitly typed `JapaneseRomajiName` prefix. Full, arbitrary new text, foreign names and embedded phoneme syntax reject before inference. Do not use it as a general drop-in speaker.

The candidate uses the exact Kitten Nano 0.8 FP32 model, Kiki style ID 7, requested speed 0.8 with the stable 0.8 prior, direct ORT, and output pause shortening. `Runtime.kt` pins all five asset hashes. No eSpeak/Piper/Sherpa runtime or original Misaki dictionary is loaded. Apache output-pause algorithm attribution is in `../candidates/SHERPA_CONTRACT_NOTICE` and `SHERPA_CONTRACT_LICENSE`. ORT/MIT notices and the CMU/BSD notice must accompany any eventual packaging. Evaluation-only stable audio uses a separate Sherpa environment; never bundle it.

## Reproduce

Required: JDK17, Kotlin2.3.21, official JVM ORT1.22.0 jar (SHA256 `41bb3b51b6a2e489cd39f538d5a1c74e4333845ab1ccad63e752b6f19ebeee45`), CMU dictionary at commit `74790861f652b15e4ac49015a90074ad62a27690`, and the five assets downloaded by `../candidates/fetch_kitten_assets.py`. ASR dependencies are separate, pinned under `../reference/`. Python structural audit needs NumPy. Work from repository root; set `TTS_KOTLIN`, `TTS_ORT`, `TTS_CMU`, `TTS_ASSETS`, `TTS_WAVES` to their corresponding local paths.

```sh
python tts-validation/export_native_fixtures.py
"$TTS_KOTLIN" tts-validation/android-isolated/Runtime.kt tts-validation/android-isolated/SpeakerCore.kt tts-validation/android-isolated/NativeLitePlanner.kt tts-validation/android-isolated/NativeLiteProbe.kt tts-validation/android-isolated/NativeControllerProbe.kt tts-validation/android-isolated/NativeMemoryProbe.kt -cp "$TTS_ORT" -include-runtime -d /tmp/native-tts.jar
java -Xmx128m -cp "/tmp/native-tts.jar:$TTS_ORT" validation.tts.NativeLiteProbe "$TTS_CMU" tts-validation/fixtures/native-lite-catalogue.txt tts-validation/fixtures/native-lite-fixed.tsv tts-validation/results/native-lite-lexical.json
python tts-validation/validate_native_lite.py
java -Xmx128m -cp "/tmp/native-tts.jar:$TTS_ORT" validation.tts.NativeLiteProbe "$TTS_CMU" tts-validation/fixtures/native-lite-catalogue.txt tts-validation/fixtures/native-lite-fixed.tsv tts-validation/results/native-lite-audio.json "$TTS_ASSETS" "$TTS_WAVES" normal
python tts-validation/validate_native_audio.py --waveforms "$TTS_WAVES" --count 202 --output tts-validation/results/native-normal-structure.json
python tts-validation/evaluate_native_pre_apk.py
```

The final command deliberately exits 1 while blockers remain. For named coverage use `native-lite-names.tsv` (780). `native-lite-names-remaining.tsv` preserves original indexes 650–779 solely to recover this recorded interrupted run. It is not a replacement for a future continuous soak. Probe JSON is written only on completion; raw waveforms alone do not prove completed execution.

The optional `length` mode adds explicit Kitten input vowel lengths; it is not a claim that CMU encodes quantity. `length-compound` additionally tests independently sourced US initial primary/secondary stress and t-flap for pitter-patter. This five-case repair has not been qualified across the full corpus or names. Neither mode is adopted in the app.

`SpeakerCore` tests run real ORT with an injected fake sink and dispatcher. `AndroidSink` compiles against the official Android AAR/API37.1, but has not run on Android. Context/model-download provisioning, actual playback behavior, and complete stable KittenSpeaker lifecycle parity remain unqualified. Explicit-GC JVM memory sampling is a bounded observation, not proof against Android leaks.

See [executed findings](../results/2026-10-01-native-findings.md) and [readiness ledger](../results/native-pre-apk-readiness.json).
