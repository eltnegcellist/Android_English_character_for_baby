# Native TTS validation — 2026-10-01

**Not approved for application integration. Pre-APK validation is incomplete.** No application APK was built or inspected in this phase. All changes remain under `tts-validation/`; production source is unchanged against baseline `62f39abc095dd29a57d86a0efdef5400a2bb4293`. Main has advanced independently; this work does not revert or update it.

## What was actually implemented

An independent, bounded **Kotlin CMU frontend**, direct Java ONNX Runtime backend and isolated speaker controller. This removes Python/Misaki/spaCy from the native execution path but does not claim equivalent unrestricted English G2P. The exact 202-entry Lite catalogue and explicitly tagged Japanese romanized prefixes are accepted. New/Full sentences, unknown words, untagged names and phoneme injection reject before inference. No app fallback or integration was added.

Model: the same pinned Kitten Nano 0.8 FP32, Kiki ID 7, 24 kHz, requested speed 0.8 with stable prior 0.8. Candidate asset selection contains only model, voices, tokens, license and README; no eSpeak data or runtime. CMU provenance and BSD notice remain pinned. Apache output-pause algorithm attribution and ORT notices are retained. This is a selected-input/dependency audit, **not a final APK license inspection**.

## Executed evidence

| Check | Result | Scope |
|---|---|---|
| Independent fixed-Lite word fixtures | 202 utterances; 149 distinct words; 1,260 occurrences; zero failures | Segment and specified primary-stress checks, not acoustic pronunciation |
| Native named lexical matrix | 780 cases; zero failures; unsupported input negatives reject | Five explicit Japanese romanized names; no foreign-name inference |
| Real native ONNX normal/length modes | 202 + 202 valid waveforms | Finite mono 24 kHz; normal duration 0.675–6.580 s, length 0.714–6.602 s |
| Named audio structural audit | 780/780 valid; 3.361–8.218 s | Composite recovery, not an uninterrupted soak; names not acoustically qualified |
| Actual text → native G2P → real JVM ONNX controller | 100 stop/restart cycles; 102 completions; one expected unsupported-text error | Fake sink; zero cancelled terminal callbacks; max observed stop-to-idle 6 ms |
| JVM bounded memory observation | 100 cycles after 10 warmups | Explicit GC every 10 cycles; RSS 349,236–355,972 KiB; not Android/long-term leak proof |
| Official Android ORT AAR/API compile | Succeeded for native frontend, core, runtime and AudioTrack sink | Compile only; no Android execution |
| Production isolation | No changes outside `tts-validation/` | VAD, ASR, conversation control, tutorial and UI unchanged |

### Acoustic diagnostic, not voice-equivalence approval

Same decoder/settings and freshly generated stable audio were used for both 202-case comparisons:

| Candidate | Stable WER | Candidate WER | Difference |
|---|---:|---:|---:|
| Native CMU normal | 7.143% | 8.797% | +1.654 percentage points |
| Native CMU with explicit vowel lengths | 7.143% | 7.218% | +0.075 percentage points |

Both aggregate diagnostics meet the declared +5-point threshold. They do **not** prove naturalness, Kiki speaker similarity, correct name pronunciation or every utterance's correctness. Audio generation is stochastic; the fresh baseline differs from earlier trials. Keep earlier failing ASR results in the archive rather than replacing them.

A targeted five-case **pitter-patter** subset exposes a failure hidden by the aggregate: length-mode WER 38.462% against stable 11.538%, failing the same diagnostic threshold. An independently sourced optional compound experiment uses initial primary/later secondary stress and the US t-flap (`pˈɪɾəɹpˌæɾəɹ`), checked against [Cambridge pronunciation](https://dictionary.cambridge.org/us/pronunciation/english/pitter-patter). Its five-case WER is 15.385%, but three of five transcripts still contain errors. This is improvement evidence, not complete pronunciation approval. The optional repair has not been tested across all 202/780 cases and is not adopted.

### Interrupted name generation must stay visible

The first long native name run acknowledged 650 completed cases; its execution session then disappeared before terminal JSON. 662 raw files existed, but the last 12 were not counted as completed. Cause is unknown; cgroup OOM counters were zero and no OOM conclusion is justified.

Cases 650–779 were regenerated in a separate bounded run (130/130 succeeded in 99.16 seconds), preserving original indexes. Structural checks now cover all 780 files. `native-long-matrix-interruption.json` and `native-lite-names-audio.json` explicitly mark **uninterrupted 780-case soak as unqualified**.

## Blockers before application integration

1. Native unrestricted Full/number/OOV/homograph behavior is unsupported. The limited catalogue cannot satisfy a universal TTS replacement contract.
2. A continuous name-generation endurance run with durable per-case journal and timeout diagnosis remains necessary.
3. Compound pronunciation, names, naturalness and Kiki equivalence are not qualified. ASR alone cannot prove them.
4. Actual Android AudioTrack/JNI lifecycle and stable KittenSpeaker state-transition parity have not been exercised. The isolated controller's fake sink does not close this gate.

`evaluate_native_pre_apk.py` records these blockers and deliberately exits 1. Do not connect this candidate to the application or produce a device APK while they remain.

## Reproduction and preservation

[Native harness instructions](../android-isolated/README.md), [readiness ledger](native-pre-apk-readiness.json), and [structural audit script](../validate_native_audio.py) describe the commands and limitations. Corpus TSVs reproduce from the pinned application source using `export_native_fixtures.py`.

The checkpoint archive contains all changed validation sources, fixtures and reports relative to the local validation checkout, excluding prior checkpoint archives. It excludes model/audio binaries and evaluation virtual environments. Archive inventory and SHA256 are recorded in the matching JSON. Extract at repository root to restore the full evidence alongside the separately published native source files.

The normal/length full audio and controller probes used the initial native probe jar; optional compound support was compiled afterward. It does not change the default normal/length branches. Source hashes in earlier reports describe their recorded phase, while the latest word gate and Android compile pin the current sources. Do not claim byte-identical historical binaries merely from current source hashes.
