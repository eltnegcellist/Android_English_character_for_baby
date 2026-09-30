This directory selectively includes `en.py` and `token.py` from Misaki 0.9.4,
https://github.com/hexgrad/misaki (hexgrad), under Apache License 2.0.
The original license is included as LICENSE.

`en.py` is modified in one place: the `num2words` import is replaced by the
independently written `number_words` implementation in this validation project.
Original file and modified file checksums are recorded in source-manifest.json.

The package and data `__init__.py` files are new placeholders. No original
Misaki dictionary JSON, eSpeak wrapper, phonemizer, or language-specific assets
are included here. This subset is a validation prototype, not an Android SDK.
