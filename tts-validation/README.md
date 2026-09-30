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


## Misaki audit finding

Upstream documents English `G2P(trf=false, british=false, fallback=None)` as the no-eSpeak mode. Unknown tokens remain detectable rather than silently invoking eSpeak. The hosted demo is not a valid no-GPL reference because it explicitly constructs `EspeakFallback`.

The validation reference therefore MUST instantiate Misaki with fallback=None and MUST fail fixtures containing unresolved tokens. No eSpeak-derived fallback output may be used as expected data.

A native port is not accepted by license label alone: dictionary/data provenance is audited separately and parity fixtures are pinned before implementation.

## Executed discovery probe (2026-09-30)

See [results/2026-09-30-findings.md](results/2026-09-30-findings.md) for the executed corpus/name/contrast probe and its blockers. The current candidate is **not approved for integration**. `validate_g2p.py` is a read-only discovery tool and exits nonzero; its selective reference environment still contains LGPL num2words and is not a production non-GPL implementation.

## Additional CMU-backed candidate

See [results/2026-09-30-cmu-candidate-findings.md](results/2026-09-30-cmu-candidate-findings.md). The second candidate excludes original Misaki dictionaries and LGPL num2words, and resolves the fixed bank plus the configured Japanese-name matrix. This is lexical/token coverage only; pronunciation, unrestricted Full output, voice parity, Android lifecycle, and APK gates are still unqualified. `validate_clean_candidate.py` remains nonzero and does not authorize integration.

## Expanded pronunciation gate

See [results/2026-09-30-pronunciation-findings.md](results/2026-09-30-pronunciation-findings.md). Independent word/sense fixtures exposed errors in the CMU-backed candidate, including two actual Lite replies. The repaired candidate passes 39 expanded checks and the earlier 61 checks, while retaining zero lexical/token failures in the 202/780 corpus matrix. This is still not comprehensive pronunciation or audio approval. `validate_pronunciation.py` returns success for its fixtures only; `validate_clean_candidate.py` continues to block integration.
