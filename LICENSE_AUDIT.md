# Android GPL / eSpeak Dependency Audit

Audit date: 2026-10-01

Baseline before remediation:
`b3671b1dd132b522d110d60617c97fd4bef1dc8c` (v1.9.18)

## Previous TTS path

Android v1.9.18 used:

- `sherpa-onnx-static-1.13.8.aar`;
- sherpa `OfflineTts`;
- a Kitten archive containing `espeak-ng-data`;
- `OfflineTtsKittenModelConfig.dataDir` pointing directly at that directory.

That path could not be made GPL-free by deleting only the data directory,
because sherpa performed text-to-phoneme conversion internally.

## v1.9.19 remediation

The Kitten TTS path was reimplemented to mirror the non-GPL Web architecture:

1. download the official Kitten Nano 0.8 FP32 ONNX model;
2. download `voices.npz` and select Kiki / `expr-voice-5-f`;
3. download a pinned BSD-style CMUDict revision;
4. normalize text locally;
5. phonemize with CMUDict + Mitsukotoba context/OOV rules;
6. map IPA symbols to Kitten token IDs;
7. execute the ONNX model directly with ONNX Runtime Android;
8. play the resulting 24 kHz PCM through Android `AudioTrack`.

The Gradle dependency on the sherpa AAR is removed.

## Regression checks

Android CI rejects source/build references to:

- `espeak-ng-data`
- `com.k2fsa.sherpa`
- `sherpa-onnx-static`
- sherpa's Kitten model config

The built APK is also inspected for eSpeak/sherpa filenames and DEX strings.

## Conclusion

For the v1.9.19 candidate, the Android Kitten TTS execution path is designed to
contain no eSpeak NG or sherpa TTS runtime dependency.

This audit is technical rather than legal advice. The overall application is
not "permissive-only": Moonshine and ONNX Runtime provenance includes
separately licensed third-party material, including MPL-2.0 components.
