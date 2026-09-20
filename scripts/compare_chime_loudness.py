"""Compare local chimes without gain normalization; requires ffmpeg on PATH."""
import array
import json
import math
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PAIRS = {
    'approach': 'threat_level_changed_normal_1',
    'urgent': 'threat_level_changed_high',
    'connected': 'remote_device_connected',
    'lost': 'remote_device_disconnected',
    'empty': 'threat_level_all_clear',
}


def db(value):
    return round(20 * math.log10(max(value, 1e-12)), 2)


def measure(file):
    # Keep original channels for BS.1770; decode mono separately for envelope/RMS.
    result = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(file),
        '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, check=True)
    log = result.stderr.decode()
    summary = log.rsplit('Summary:', 1)[1]
    integrated = float(re.search(r'I:\s*([-\d.]+) LUFS', summary)[1])
    true_peak = float(re.search(r'Peak:\s*([-\d.]+) dBFS', summary)[1])
    momentary = max(map(float, re.findall(r'\bM:\s*([-\d.]+)', log)))
    mono_result = subprocess.run(['ffmpeg', '-hide_banner', '-i', str(file),
        '-af', 'aformat=channel_layouts=mono,ebur128=peak=true', '-f', 'null', '-'],
        capture_output=True, check=True)
    mono_momentary = max(map(float, re.findall(r'\bM:\s*([-\d.]+)', mono_result.stderr.decode())))
    raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', str(file),
        '-ac', '1', '-ar', '48000', '-f', 'f32le', '-'])
    samples = array.array('f', raw)
    powers = [sum(x*x for x in samples[i:i+480]) / len(samples[i:i+480])
              for i in range(0, len(samples), 480)]
    # Operational "active" envelope, not an audibility threshold: 10ms blocks
    # within 40dB of the strongest block. Report to expose long silent tails.
    active = [p for p in powers if p > max(powers) * 1e-4]
    return dict(duration_ms=round(len(samples)/48, 1),
        integrated_lufs=integrated, maximum_momentary_lufs=momentary,
        true_peak_dbfs=true_peak, mono_maximum_momentary_lufs=mono_momentary,
        whole_rms_dbfs=db(math.sqrt(sum(x*x for x in samples)/len(samples))),
        active_rms_dbfs=db(math.sqrt(sum(active)/len(active))),
        active_ms=len(active)*10)


if __name__ == '__main__':
    rows = []
    for event, original in PAIRS.items():
        garmin = measure(ROOT / 'artifacts/decompiled/varia/resources/res/raw' / f'{original}.mp3')
        ours = measure(ROOT / 'app/src/main/res/raw' / f'alert_{event}.wav')
        rows.append(dict(event=event, garmin=garmin, ridewatch=ours,
            momentary_difference_lu=round(ours['maximum_momentary_lufs']-garmin['maximum_momentary_lufs'], 1)))
    print(json.dumps(rows, indent=2))
