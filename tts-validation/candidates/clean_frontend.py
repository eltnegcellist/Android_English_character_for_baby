"""CMU-backed Misaki experimental frontend, never an app runtime.

No original Misaki dictionary is read. No guessed fallback for unknown English.
Name conversion is opt-in with separately supplied Japanese-romaji metadata.
"""
import hashlib
import importlib.resources
import json
from pathlib import Path
import re
from vendor.misaki import en

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / 'source-manifest.json').read_text())
ARPABET = {'AA': 'ɑ', 'AE': 'æ', 'AH': 'ʌ', 'AO': 'ɔ', 'AW': 'W', 'AY': 'I',
           'B': 'b', 'CH': 'ʧ', 'D': 'd', 'DH': 'ð', 'EH': 'ɛ', 'ER': 'ɜɹ',
           'EY': 'A', 'F': 'f', 'G': 'ɡ', 'HH': 'h', 'IH': 'ɪ', 'IY': 'i',
           'JH': 'ʤ', 'K': 'k', 'L': 'l', 'M': 'm', 'N': 'n', 'NG': 'ŋ',
           'OW': 'O', 'OY': 'Y', 'P': 'p', 'R': 'ɹ', 'S': 's', 'SH': 'ʃ',
           'T': 't', 'TH': 'θ', 'UH': 'ʊ', 'UW': 'u', 'V': 'v', 'W': 'w',
           'Y': 'j', 'Z': 'z', 'ZH': 'ʒ'}
OWN_EXPRESSIVE = {'peekaboo': 'pˈikəbˌu', 'pitter': 'pˈɪtəɹ', 'mmm': 'mː',
                  "'m": 'm', "'s": 'z', "'re": 'əɹ', "'ve": 'v', "'ll": 'əl',
                  "'d": 'd', "n't": 'nt'}


def convert_arpabet(phones):
    out = []
    for token in phones:
        match = re.fullmatch(r'([A-Z]+)([012]?)', token)
        if not match or match[1] not in ARPABET:
            raise ValueError(f'Unknown ARPABET token: {token}')
        base, stress = match.groups()
        ps = 'ə' if base == 'AH' and stress == '0' else 'əɹ' if base == 'ER' and stress == '0' else ARPABET[base]
        out.append({'1': 'ˈ', '2': 'ˌ'}.get(stress, '') + ps)
    return ''.join(out)


def romaji_name(name):
    """Limited pronunciation policy; caller must explicitly identify a Japanese name.

    Unsupported foreign spellings/apostrophes fail instead of being guessed.
    This is English-TTS approximation, not Japanese native phonetics.
    """
    text = name.lower().strip()
    suffix = bool(re.search(r'(?:-|\s)chan$', text))
    if suffix:
        text = re.sub(r'(?:-|\s)chan$', '', text)
    if not re.fullmatch('[a-z]+(?:[ -][a-z]+)*', text):
        raise ValueError('Unsupported name spelling')
    clusters = {'shi': 'ʃi', 'chi': 'tʃi', 'tsu': 'tsu', 'fu': 'fu', 'ji': 'dʒi',
                'sha': 'ʃɑ', 'shu': 'ʃu', 'sho': 'ʃo', 'cha': 'tʃɑ', 'chu': 'tʃu', 'cho': 'tʃo',
                'ja': 'dʒɑ', 'ju': 'dʒu', 'jo': 'dʒo'}
    vowel = {'a': 'ɑ', 'i': 'i', 'u': 'u', 'e': 'e', 'o': 'o'}
    consonant = {'k': 'k', 's': 's', 't': 't', 'n': 'n', 'h': 'h', 'm': 'm',
                 'r': 'ɹ', 'y': 'j', 'w': 'w', 'g': 'ɡ', 'z': 'z', 'd': 'd',
                 'b': 'b', 'p': 'p', 'f': 'f', 'v': 'v'}
    outputs = []
    for part in re.split('[ -]', text):
        out, i = [], 0
        while i < len(part):
            found = next((c for c in sorted(clusters, key=len, reverse=True) if part.startswith(c, i)), None)
            if found:
                out.append(clusters[found]); i += len(found); continue
            ch = part[i]
            if ch in vowel:
                out.append(vowel[ch]); i += 1; continue
            if ch == 'n' and (i + 1 == len(part) or part[i+1] not in 'aeiouy'):
                out.append('n'); i += 1; continue
            if ch in consonant and i + 1 < len(part):
                if part[i+1] == ch:
                    out.append(consonant[ch]); i += 1; continue
                if part[i+1] in vowel:
                    out.append(consonant[ch] + vowel[part[i+1]]); i += 2; continue
                if part[i+1] == 'y' and i + 2 < len(part) and part[i+2] in 'auo':
                    out.append(consonant[ch] + 'j' + vowel[part[i+2]]); i += 3; continue
            raise ValueError(f'Unsupported romaji sequence at {part[i:]}')
        if not out:
            raise ValueError('Empty name')
        first = out[0]
        pos = next((j for j, c in enumerate(first) if c in 'ɑiueo'), None)
        if pos is not None:
            out[0] = first[:pos] + 'ˈ' + first[pos:]
        outputs.append(''.join(out))
    return ' '.join(outputs) + (' tʃɑn' if suffix else '')


def kitten_ipa(ps):
    # Candidate semantics, tested by explicit expected fixtures. Not audio parity proof.
    conversion = {'A': 'eɪ', 'I': 'aɪ', 'O': 'oʊ', 'W': 'aʊ', 'Y': 'ɔɪ',
                  'Q': 'əʊ', 'ʤ': 'dʒ', 'ʧ': 'tʃ', 'ᵊ': 'ə', 'T': 'ɾ'}
    if '❓' in ps:
        raise ValueError('Unresolved phonemes cannot be tokenized')
    expanded = ''.join(conversion.get(c, c) for c in ps)
    if re.search('[A-Z]', expanded):
        raise ValueError('Unmapped semantic alias')
    return expanded


class Frontend:
    def __init__(self, dictionary_path):
        path = Path(dictionary_path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != MANIFEST['cmudict']['dictionary_sha256']:
            raise ValueError('CMU dictionary checksum mismatch')
        pronunciations = {}
        for line in path.read_text().splitlines():
            word, *phones = line.split('#', 1)[0].split()
            pronunciations[word] = convert_arpabet(phones)
        lexicon = {word: ps for word, ps in pronunciations.items() if not re.search(r'\(\d+\)$', word)}
        # Grammatical contrasts; CMU variants retain exact source phonemes.
        lexicon['read'] = {'DEFAULT': pronunciations['read(2)'], 'VBD': pronunciations['read'], 'VBN': pronunciations['read']}
        lexicon['wind'] = {'DEFAULT': pronunciations['wind(2)'], 'VERB': pronunciations['wind']}
        lexicon['tear'] = {'DEFAULT': pronunciations['tear(2)'], 'VERB': pronunciations['tear']}
        lexicon['used'] = {'DEFAULT': pronunciations['used'], 'VBD': 'jˈust'}
        # CMU outside has two primary stresses. Choose one main stress for this
        # candidate's Kitten input convention; retain the other as secondary.
        # Segment sequence remains CMU; stress policy is independently defined.
        outside = pronunciations['outside']
        lexicon['outside'] = {
            'DEFAULT': outside.replace('ˈ', 'ˌ', 1),
            'ADJ': outside[::-1].replace('ˈ', 'ˌ', 1)[::-1],
            'NOUN': outside[::-1].replace('ˈ', 'ˌ', 1)[::-1],
        }
        # CMU numbered alternatives do not encode POS. Select only audited pairs;
        # a successful lookup alone is not evidence of the intended pronunciation.
        for word, default, overrides in [
            ('close', 'close', {'VERB': 'close(2)'}),
            ('live', 'live', {'VERB': 'live(2)'}),
            ('present', 'present', {'VERB': 'present(2)'}),
            ('record', 'record(2)', {'VERB': 'record'}),
            ('lead', 'lead', {'VERB': 'lead(2)'}),
            ('object', 'object', {'VERB': 'object(2)'}),
            ('content', 'content', {'ADJ': 'content(2)'}),
            ('use', 'use', {'VERB': 'use(2)'}),
        ]:
            lexicon[word] = {'DEFAULT': pronunciations[default],
                             **{tag: pronunciations[source] for tag, source in overrides.items()}}
        # Keep both bass senses; only narrow, explicit music/fish contexts qualify.
        self.bass_music = pronunciations['bass(2)']
        self.close_verb = pronunciations['close(2)']
        self.lead_role = pronunciations['lead(2)']
        self.does_deer = pronunciations['doe'] + 'z'
        self.provenance = {'source': MANIFEST['cmudict'], 'cmu_words': len(lexicon),
                           'own_entries': {**OWN_EXPRESSIVE, 'used_before_to': 'jˈust'},
                           'original_misaki_dictionary_assets': 'not included',
                           'punctuation_policy': 'unresolved punctuation-only tokens preserve their literal punctuation'}
        lexicon.update(OWN_EXPRESSIVE)

        class CmuLexicon(en.Lexicon):
            def __init__(inner, british):
                if british:
                    raise ValueError('Only CMU American English candidate is supported')
                inner.british = False
                inner.cap_stresses = (0.5, 2)
                inner.golds = en.Lexicon.grow_dictionary(lexicon)
                inner.silvers = {}

        original = en.Lexicon
        en.Lexicon = CmuLexicon
        try:
            self.g2p = en.G2P(version='2.0', trf=False, british=False, fallback=None)
        finally:
            en.Lexicon = original

    def __call__(self, text, japanese_name=None):
        if '[' in text or ']' in text or '\n' in text:
            raise ValueError('Untrusted embedded phoneme syntax')
        # Short nursery imperatives are tagged JJ by the small POS model.
        # Preserve the adjective in "nice and close"; override only this action.
        text = re.sub(r'\b(open\s*(?:,|and)\s+)(close)(?=\s*[.!?])',
                      lambda m: m[1] + f'[{m[2]}](/{self.close_verb}/)', text, flags=re.I)
        # Noun senses are not separable by POS alone. These are bounded idioms,
        # not a general semantic classifier. Doe plural is CMU doe + voiced /z/.
        text = re.sub(r'\b((?:take|takes|taking|took|taken|hold|holds|held|holding|in|into)\s+the\s+)(lead)\b',
                      lambda m: m[1] + f'[{m[2]}](/{self.lead_role}/)', text, flags=re.I)
        text = re.sub(r'\b((?:the|these|those|two|three)\s+)(does)(?=\s+(?:graze|roam|feed|leap)\b)',
                      lambda m: m[1] + f'[{m[2]}](/{self.does_deer}/)', text, flags=re.I)
        # Never let a clear sense in one sentence resolve a separate occurrence.
        # Multiple occurrences in one clause remain unqualified, conservatively.
        ambiguous, bass_decisions = [], []
        clauses = re.split(r'([.!?;,])', text)
        for i in range(0, len(clauses), 2):
            clause = clauses[i]
            hits = list(re.finditer(r'\bbass\b', clause, re.I))
            if not hits:
                continue
            music = bool(re.search(r'\bbass\s+(?:guitar|clef|drum|player|singer)\b|\bdouble\s+bass\b|\bplay(?:ing|ed)?\s+the\s+bass\b', clause, re.I))
            fish = bool(re.search(r'\bbass\s+(?:fish|swims?|swimming)\b|\b(?:caught|catch|fishing\s+for)\s+(?:a\s+)?bass\b', clause, re.I))
            decision = 'music' if len(hits) == 1 and music and not fish else 'fish' if len(hits) == 1 and fish and not music else None
            bass_decisions.extend([decision] * len(hits))
            if decision is None:
                ambiguous.extend(['bass'] * len(hits))
            elif decision == 'music':
                clauses[i] = re.sub(r'\bbass\b', lambda m: f'[{m[0]}](/{self.bass_music}/)', clause, flags=re.I)
        text = ''.join(clauses)
        if japanese_name:
            ps = romaji_name(japanese_name)
            # Misaki explicit phoneme aliases for separately authorized name metadata only.
            pattern = r'(?<![A-Za-z])' + re.escape(japanese_name) + r'(?![A-Za-z-])'
            text = re.sub(pattern, lambda m: f'[{m[0]}](/{ps}/)', text, flags=re.I)
        phonemes, tokens = self.g2p(text)
        repaired_punctuation = []
        for token in tokens:
            if token.phonemes is None and token.text and all(c in en.PUNCTS for c in token.text):
                # POS tagging can classify standalone ! as NNP after a name alias.
                # Preserve punctuation; never repair lexical unknowns here.
                token.phonemes = token.text
                repaired_punctuation.append(token.text)
        if repaired_punctuation:
            phonemes = ''.join(('❓' if token.phonemes is None else token.phonemes) + token.whitespace for token in tokens)
        unresolved = [t.text for t in tokens if t.phonemes is None or '❓' in t.phonemes]
        return {'phonemes': phonemes, 'ipa': None if unresolved or ambiguous else kitten_ipa(phonemes),
                'unresolved': unresolved,
                'unresolved_meaning': ambiguous,
                'bass_context_policy': bass_decisions[0] if bass_decisions and len(set(bass_decisions)) == 1 else None,
                'bass_occurrence_policies': bass_decisions,
                'repaired_punctuation': repaired_punctuation,
                'tokens': [{'text': t.text, 'tag': t.tag, 'phonemes': t.phonemes} for t in tokens]}
