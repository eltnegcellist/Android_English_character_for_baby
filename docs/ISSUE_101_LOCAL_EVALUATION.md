# Issue #101: private S25 replay audit

**Research only — not a release approval.** Run the same 16-kHz mono PCM16 WAVs
and human-corrected UTF-8 .txt references under all six Moonshine settings.
Keep source WAV, raw results, individual transcripts and gold labels **local only**.
Do not upload them to public GitHub, CI, PR attachments or Actions artifacts.

## On Mac

Prepare private pairs such as .private-eval/issue101/inputs/01.wav and 01.txt.
A suggested minimum is 40 independent phrases: 10 childcare keyterms, 10
negations/sequences, 10 no-keyterm controls and 10 natural-noise examples.
This is a smoke set, not population accuracy certification.

Install the debug app and matching AndroidTest APK on Galaxy S25 with
Moonshine Japanese Small and Ruri downloaded. Then:

    APP=com.eltnegcellist.emma
    adb shell -T run-as "$APP" sh -c 'mkdir -p files/asr-bias-lab/inputs'
    for f in .private-eval/issue101/inputs/*; do
      adb shell -T run-as "$APP" sh -c "cat > files/asr-bias-lab/inputs/$(basename "$f")" < "$f"
    done

Use simple, trusted filenames without spaces or shell metacharacters.
The transfer goes directly into app-private storage without /sdcard staging.

    adb shell am instrument -w \
      -e class 'com.eltnegcellist.emma.asr.MoonshineAsrBiasReplayTest#replaySameAudioAcrossMoonshineSettings' \
      com.eltnegcellist.emma.test/androidx.test.runner.AndroidJUnitRunner

Retrieve the PRIVATE file:

    adb exec-out run-as com.eltnegcellist.emma \
      cat files/asr-bias-lab/results.json > .private-eval/issue101/results.json

## Independent human annotations

Optional for initial CER and latency inspection, REQUIRED for semantic claims.
Create private file .private-eval/issue101/gold.json of this form:

    {
      "cases": [
        {
          "file": "01.wav",
          "human_verified": true,
          "expected_topic": null,
          "expected_interaction": "reassurance",
          "forbidden_terms": ["泣いている"]
        }
      ]
    }

This is a schema EXAMPLE, not measured ground truth. Human reviewers should
assign Topic/Interaction and forbidden-term watchlists without copying model
outputs. Use human_verified=false for undecided clips. A detected forbidden
word is a possible insertion only; human judgment determines actual harm.
Review both the labels and the final English sentence for negation/time errors.

## Local aggregate

    python3 scripts/audit-asr-replay.py \
      .private-eval/issue101/results.json \
      --gold .private-eval/issue101/gold.json \
      --output .private-eval/issue101/aggregate.json

Omit --gold for exploratory counts without semantic accuracy. The summary is
aggregate-only (no filenames/transcripts), but review all metadata for privacy
before publishing any numbers. It always reports research-only status.

Compare 13 tokens/s no-keyterms vs boost1/boost2/boost3/expanded vocabulary;
compare the legacy 6.5 tokens/s setting separately. Inspect requested/applied/
skipped term counts because invalid 喃語 must not disable supported terms.
Results report CER, paired CER improvement/regression, ASR median/p95,
both-generic rate, human-scored accuracy and suspected word intrusion counts.
Generic-rate improvement alone cannot justify a production threshold.

## Still required for Issue #101 closure

- Repeat comparable evaluation for Mac/Web; Android JNI is not Web WASM.
- Adjudicate meaning errors in ご機嫌ななめだね, 泣いてはいないんだよ,
  今は眠くないんだね and ミルクを飲んだらお風呂.
- Listen to all 26 recorded audio candidates in Web voice-content-review-26.html.
- Measure real microphone-to-audible playback median/p95 on Mac and Galaxy S25.
  ASR elapsed time excludes capture, VAD, synthesis-independent playback and acoustics.
- Obtain explicit human release approval before closing Issue #101.

Public CI may run ONLY the synthetic contract test:

    python3 scripts/test-audit-asr-replay.py
