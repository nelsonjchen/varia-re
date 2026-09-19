#!/usr/bin/env python3
"""Generate original chimes approximating Varia alert cadence, without sampling its audio."""
from pathlib import Path
import math
import struct
import wave
RATE = 24000
OVERSAMPLE = 4
PEAK = 10 ** (-1.5 / 20)  # Headroom for reconstruction and Android resampling.


def master(samples):
    """Soft saturation at 96 kHz, anti-alias filtering, then peak normalization."""
    driven = [math.tanh(4 * value) for value in samples]
    # Windowed-sinc low-pass before downsampling; do not alias saturation harmonics.
    half = 32
    cutoff = .45 / OVERSAMPLE
    kernel = []
    for i in range(-half, half + 1):
        sinc = 2 * cutoff if i == 0 else math.sin(2 * math.pi * cutoff * i) / (math.pi * i)
        kernel.append(sinc * (.54 + .46 * math.cos(math.pi * i / half)))
    total = sum(kernel)
    kernel = [value / total for value in kernel]
    padded = [0.] * half + driven + [0.] * half
    filtered = [sum(padded[i + k] * weight for k, weight in enumerate(kernel))
                for i in range(0, len(driven), OVERSAMPLE)]
    gain = PEAK / max(abs(value) for value in filtered)
    return [round(value * gain * 32767) for value in filtered]

PATTERNS = {
    'approach': [(0, [523.25, 698.46, 987.77]), (.18, [523.25, 698.46, 987.77])],
    'urgent': [(0, [523.25, 698.46, 987.77]), (.18, [523.25, 698.46, 987.77]), (.36, [523.25, 698.46, 987.77])],
    'connected': [(0, [783.99]), (.09, [987.77]), (.18, [1174.66])],
    'lost': [(0, [1174.66]), (.09, [987.77]), (.18, [783.99])],
    'empty': [(0, [523.25]), (.10, [1046.50])],
}
def generate(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for name, notes in PATTERNS.items():
        samples = []
        for i in range(round((notes[-1][0] + .32) * RATE * OVERSAMPLE)):
            value = 0
            for start, frequencies in notes:
                t = i / (RATE * OVERSAMPLE) - start
                if 0 <= t < .30:
                    envelope = min(1, t / .004) * math.exp(-10*t) * min(1, (.30-t)/.02)
                    value += envelope * sum(math.sin(2*math.pi*f*t) + .18*math.sin(4*math.pi*f*t) for f in frequencies) / len(frequencies)
            samples.append(value)
        samples = master(samples)
        with wave.open(str(destination / ('alert_' + name + '.wav')), 'wb') as output:
            output.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
            output.writeframes(struct.pack('<' + 'h'*len(samples), *samples))
if __name__ == '__main__':
    generate(Path(__file__).resolve().parents[1] / 'app/src/main/res/raw')
