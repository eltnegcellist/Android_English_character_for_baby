"""Isolated Kitten diagnostic runner. No playback, app, fallback or SDK imports."""
import hashlib
from pathlib import Path
import numpy as np
import onnxruntime as ort

ASSET_HASHES = {
    'model.fp32.onnx': '2174dbf67b58b7b50d7b65294f89c2c53c172834533519b853c579879a04cc22',
    'voices.bin': 'd520519c4a3519d44fcfcd943ed0b1e3c5da5cee0eea501d922fac1a93cd24dc',
    'tokens.txt': '934a4188addc7665dd3410256bb622169242357fbb99d840d9351209b486dabb',
    'LICENSE': 'cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30',
}


class Engine:
    def __init__(self, assets):
        root = Path(assets)
        if sorted(p.name for p in root.iterdir()) != sorted([*ASSET_HASHES, 'README.md']):
            raise ValueError('Unexpected assets; only the explicitly selected model resources may be used')
        for name, digest in ASSET_HASHES.items():
            if hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
                raise ValueError(f'Asset checksum mismatch: {name}')
        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        self.session = ort.InferenceSession(str(root / 'model.fp32.onnx'), sess_options=options, providers=['CPUExecutionProvider'])
        self.meta = self.session.get_modelmeta().custom_metadata_map
        if (self.meta['version'], self.meta['sample_rate'], self.meta['speaker_names'].split(',')[7]) != ('8', '24000', 'expr-voice-5-f'):
            raise ValueError('Unexpected model version/sample rate/Kiki speaker mapping')
        shape = tuple(map(int, self.meta['style_dim'].split(',')))
        self.voices = np.fromfile(root / 'voices.bin', dtype='<f4').reshape(int(self.meta['n_speakers']), *shape)
        if not np.isfinite(self.voices).all():
            raise ValueError('Invalid voice embeddings')
        self.tokens = {line.rsplit(' ', 1)[0]: int(line.rsplit(' ', 1)[1]) for line in (root / 'tokens.txt').read_text().splitlines()}
        self.speed = np.asarray([0.8 * float(self.meta['speaker_speed_priors'].split(',')[7])], dtype=np.float32)

    def generate(self, ipa):
        if not ipa or any(c not in self.tokens for c in ipa):
            raise ValueError('Unqualified or unsupported phoneme input')
        content = [self.tokens[c] for c in ipa]
        if len(content) >= int(self.meta['max_token_len']):
            raise ValueError('Chunking for long inputs is not qualified')
        ids = np.asarray([[int(self.meta['start_id']), *content, int(self.meta['end_id']), int(self.meta['pad_id'])]], dtype=np.int64)
        row = min(len(content), self.voices.shape[1]-1)
        samples = self.session.run(['waveform'], {'input_ids': ids, 'style': self.voices[7, row:row+1], 'speed': self.speed})[0]
        if samples.ndim != 1 or not samples.size or not np.isfinite(samples).all():
            raise ValueError('Invalid generated waveform')
        return samples
