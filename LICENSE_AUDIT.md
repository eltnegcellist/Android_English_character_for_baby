# Android GPL / eSpeak Dependency Audit

Audit date: 2026-10-02

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

## Stable-release verification

v1.9.19 was promoted after Galaxy S25 device verification and successful
main-branch CI. v1.9.20 and v1.9.21 retain the same non-GPL TTS architecture.
v1.9.21 changes Lite topic detection and adds the topic-guide link; it does not
change the Kitten, CMUDict, ONNX Runtime, Moonshine, OkHttp, or WorkManager
dependency set.

Release CI verifies:

- no known eSpeak/sherpa TTS identifiers in the source/build path;
- no eSpeak/sherpa filenames or DEX references in the APK;
- Moonshine's reduced ONNX Runtime is removed before packaging;
- the APK contains the official full ONNX Runtime Android 1.23.2 library for
  arm64-v8a and armeabi-v7a.

## Conclusion

For the stable v1.9.19+ / v1.9.20 / v1.9.21 line, no known eSpeak NG or
sherpa TTS runtime dependency remains in the audited Android Kitten TTS
execution path.

This audit is technical rather than legal advice. The overall application is
not "permissive-only": Moonshine and ONNX Runtime provenance includes
separately licensed third-party material, including MPL-2.0 components.
