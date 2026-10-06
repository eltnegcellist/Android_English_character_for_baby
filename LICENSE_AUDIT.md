# Android GPL / eSpeak Dependency Audit

Audit date: 2026-10-03

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
main-branch CI. v1.9.20, v1.9.21, v1.9.22, v1.9.23, and v1.9.24 retain the same
non-GPL TTS architecture. v1.9.21 changes Lite topic detection and adds the
topic-guide link. v1.9.22 bundles the topic guide inside the app for offline
viewing. v1.9.23 only reorganizes the Settings layout around shared speech
recognition and Lite/Full controls. v1.9.24 introduces a shared Web/Android
topic-test contract and generates the bundled guide from the same data.
None of these changes modifies the
Kitten, CMUDict, ONNX Runtime, Moonshine, OkHttp, or WorkManager dependency
set.

Release CI verifies:

- no known eSpeak/sherpa TTS identifiers in the source/build path;
- no eSpeak/sherpa filenames or DEX references in the APK;
- Moonshine's reduced ONNX Runtime is removed before packaging;
- the APK contains the official full ONNX Runtime Android 1.23.2 library for
  arm64-v8a and armeabi-v7a.

## Conclusion

For the stable v1.9.19+ / v1.9.20 / v1.9.21 / v1.9.22 / v1.9.23 / v1.9.24 line, no
known eSpeak NG or sherpa TTS runtime dependency remains in the audited
Android Kitten TTS execution path.

This audit is technical rather than legal advice. The overall application is
not "permissive-only": Moonshine and ONNX Runtime provenance includes
separately licensed third-party material, including MPL-2.0 components.

## v1.9.25 character colors

v1.9.25 changes character palettes and palette selection only. It adds four selectable soft palettes and vivid accents on white faces and bodies, using the same colors as Web. It retains the existing TTS/runtime dependency set and APK provenance checks.

## v1.9.26 character color update

White/black/red is merged into vivid Coral red; color shift uses the same vivid accents on a white face and body as Web. This update changes no dependencies, bundled models, or license declarations.

## v1.9.27 character color update

Adds Filled mode and per-mode Gradient selections. This update changes no dependencies, bundled models, or license declarations.

## v1.9.28 character color update

Brightens Vivid and Filled colors, adds gradient explanations, and uses a 60-second cycle. No dependencies, bundled models, or license declarations change.


## v1.9.29 interaction features

Adds optional screen-off conversation, on-device text history, Tap to Listen, and tutorial/notification behavior changes. No TTS dependencies, bundled models, or license declarations change. The existing eSpeak/sherpa rejection and APK provenance checks remain in CI.


## v1.9.30 tutorial and notification fixes

Adjusts tutorial scrolling/highlighting and screen-off conversation notification setup/permission flow. No TTS dependencies, bundled models, or license declarations change. Existing eSpeak/sherpa rejection and APK provenance checks remain in CI.


## v1.9.31 tutorial start-button fix

Changes only tutorial state handling and button enablement. No TTS dependencies, bundled models, or license declarations change. Existing eSpeak/sherpa rejection and APK provenance checks remain in CI.


## v1.9.32 tutorial step-3 target

Changes only tutorial highlighting and explanatory copy. No TTS dependencies, bundled models, or license declarations change. Existing eSpeak/sherpa rejection and APK provenance checks remain in CI.


## v1.9.33 Tap to Listen vocabulary expansion

Expands only the bundled short English phrase data used by Tap to Listen, reusing curated Lite vocabulary. No TTS dependencies, bundled models, or license declarations change. Existing eSpeak/sherpa rejection and APK provenance checks remain in CI.


## v1.9.34 Tap to Listen sentence-count setting

Adds only local UI preference handling and phrase splitting for the existing bundled English play data. No TTS dependencies, models, or third-party license declarations change.


## v1.9.35 subpage Back navigation

Adds only Android UI navigation handling for Settings and About screens. No TTS dependencies, bundled models, or license declarations change.


## v1.9.37 Tap to Listen layout refinement

Changes only Compose layout/alignment for the existing play screen. No TTS dependencies, bundled models, or third-party license declarations change.


## v1.9.38 Settings card organization

Changes only Compose Settings layout and removes a duplicate UI control for Tap to Listen sentence count. No TTS dependencies, bundled models, or third-party license declarations change.
## v1.9.39 Web/Android Semantic parity

v1.9.39 adds the pinned Ruri v3 70M INT8 Semantic topic model to Android Lite, using the existing full ONNX Runtime Android runtime. The model and tokenizer are Apache-2.0/MIT-family assets documented under `licenses/semantic/`; this change does not add eSpeak NG, sherpa-onnx TTS, GPL TTS code, or a new native inference runtime. CI continues to reject the known GPL/eSpeak/sherpa regression identifiers and verifies the packaged ONNX Runtime provenance.

## v1.9.40 hidden developer-tool parity

v1.9.40 expands hidden diagnostics only: typed Japanese can be compared across legacy Lite, Ruri Semantic, and Guard selection; model/runtime readiness is shown; and existing diagnostic TXT / crash-detail ZIP export controls are restored. It does not add a new runtime, model family, TTS backend, eSpeak NG, sherpa-onnx, or GPL TTS dependency. The v1.9.39 Ruri / ModernBERT attribution and existing ONNX Runtime provenance checks remain unchanged.

