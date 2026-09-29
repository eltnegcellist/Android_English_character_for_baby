# GPL-free TTS validation gate

This branch is a validation harness only. It must not change production VAD, ASR, endpoint detection, conversation state, UI, or the stable TTS path.

## Gate before any device APK

A replacement TTS may be integrated only after all of these pass automatically:

1. License gate: no eSpeak, Piper, sherpa TTS runtime, GPL phonemizer data, or undeclared fallback is packaged by the candidate.
2. G2P reference gate: candidate output is compared against pinned reference fixtures, including infant-directed vocabulary, names, contractions, numbers, punctuation, homographs, and OOV cases.
3. Corpus gate: the complete Lite response corpus plus generated stress corpus produces valid token sequences with zero unhandled words.
4. ONNX gate: every fixture produces finite, non-empty 24 kHz audio with bounded duration and amplitude.
5. Soak gate: repeated synthesis/cancel/restart cycles complete without deadlock, leaked state, or callback loss.
6. Contract gate: speak/stop/onDone/onError semantics match the stable KittenSpeaker contract.
7. Packaging gate: APK inspection rejects GPL/eSpeak/Piper/sherpa-TTS artifacts.
8. Production isolation gate: git diff must show no changes to VAD, ASR, endpoint detector, conversation flow, tutorial, or UI.

Failing any gate means no integration APK is offered for device testing.

## Candidate policy

Misaki is not assumed safe merely because fallback=None is documented. The pinned implementation and every vendored dictionary/data file must be license-audited, and reference fixtures must establish behavior before a Kotlin port is accepted.
