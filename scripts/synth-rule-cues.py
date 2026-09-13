#!/usr/bin/env python
"""Synthesizes the rule flash's sound cues.

One short cue per change kind plus the shared stamp, written as mono 24 kHz Vorbis into
src/main/resources/assets/magical/sounds/rule/. Deterministic: the same script always writes
the same bytes of audio, so the files can be regenerated instead of kept by hand.

    python scripts/synth-rule-cues.py

Needs numpy and soundfile (libsndfile with Vorbis support).
"""

import os
import sys

import numpy as np
import soundfile as sf

RATE = 24000
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical", "sounds", "rule")
PEAK = 0.5
FADE_MS = 5.0
RNG = np.random.default_rng(7)


def seconds(n):
    return np.arange(int(RATE * n)) / RATE


def env_exp(t, decay):
    return np.exp(-t * decay)


def env_swell(t, length):
    return np.clip(t / length, 0.0, 1.0) ** 2


def tone(t, freq, phase=0.0):
    """A sine whose frequency may vary per sample; freq is a scalar or an array over t."""
    freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    return np.sin(2.0 * np.pi * np.cumsum(freq) / RATE + phase)


def sweep(t, start, end):
    return start * (end / start) ** (t / t[-1])


def noise(n):
    return RNG.uniform(-1.0, 1.0, n)


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def click(at_ms, length_ms=6.0, amount=0.8):
    total = int(RATE * (at_ms + length_ms) / 1000.0)
    x = np.zeros(total)
    start = int(RATE * at_ms / 1000.0)
    n = total - start
    x[start:] = noise(n) * np.linspace(1.0, 0.0, n) * amount
    return x


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def finish(x):
    x = np.asarray(x, dtype=float)
    fade = int(RATE * FADE_MS / 1000.0)
    ramp = np.linspace(0.0, 1.0, fade)
    x[:fade] *= ramp
    x[-fade:] *= ramp[::-1]
    peak = np.max(np.abs(x)) or 1.0
    return x / peak * PEAK


def stamp():
    t = seconds(0.16)
    thump = tone(t, sweep(t, 220.0, 150.0)) * env_exp(t, 28.0)
    burst = lowpass(noise(len(t)), 0.35) * env_exp(t, 60.0) * 0.9
    return mix(thump, burst)


def raise_cue():
    t = seconds(0.34)
    a = tone(t, 440.0) * env_exp(t, 9.0) * np.clip(1.0 - t / 0.18, 0.0, 1.0)
    b = tone(t, 660.0) * env_exp(np.clip(t - 0.12, 0.0, None), 8.0) * env_swell(t, 0.14)
    shimmer = tone(t, 1320.0) * env_exp(np.clip(t - 0.12, 0.0, None), 14.0) * env_swell(t, 0.14) * 0.25
    return mix(a, b, shimmer)


def lower_cue():
    t = seconds(0.34)
    a = tone(t, 660.0) * env_exp(t, 9.0) * np.clip(1.0 - t / 0.18, 0.0, 1.0)
    b = tone(t, 440.0) * env_exp(np.clip(t - 0.12, 0.0, None), 7.0) * env_swell(t, 0.14)
    return lowpass(mix(a, b), 0.25)


def zero_cue():
    t = seconds(0.3)
    body = np.sign(tone(t, sweep(t, 900.0, 80.0))) * env_exp(t, 10.0) * 0.6
    body = lowpass(body, 0.5)
    tail = np.zeros(int(RATE * 0.08))
    return mix(click(0.0, 5.0, 1.0), np.concatenate([np.zeros(int(RATE * 0.02)), body]), np.concatenate([np.zeros(len(t)), tail]))


def flip_cue():
    t = seconds(0.36)
    carrier = tone(t, 520.0)
    ring = tone(t, sweep(t, 90.0, 260.0))
    swell = carrier * ring * env_swell(t, 0.2) * np.clip(1.0 - (t - 0.2) / 0.16, 0.0, 1.0)
    hit = tone(t, 780.0) * env_exp(np.clip(t - 0.2, 0.0, None), 22.0) * (t >= 0.2)
    return mix(swell, hit * 0.7)


def lock_cue():
    t = seconds(0.2)
    ping = tone(t, 1760.0) * env_exp(t, 30.0)
    return mix(click(0.0, 4.0, 1.0), click(38.0, 4.0, 0.9), np.concatenate([np.zeros(int(RATE * 0.04)), ping])[: len(t) + int(RATE * 0.04)])


def surge_cue():
    t = seconds(0.42)
    sub = tone(t, sweep(t, 70.0, 45.0)) * env_exp(t, 6.0)
    whoosh = lowpass(noise(len(t)), 0.08) * env_swell(t, 0.1) * env_exp(np.clip(t - 0.1, 0.0, None), 7.0) * 2.5
    return mix(sub, whoosh)


def aim_cue():
    t = seconds(0.34)
    ping = tone(t, 1200.0) * env_exp(t, 16.0)
    echo = np.concatenate([np.zeros(int(RATE * 0.15)), ping * 0.35])[: len(t)]
    return mix(ping, echo)


def restore_cue():
    t = seconds(0.55)
    out = np.zeros(len(t))
    for i, f in enumerate((523.25, 659.25, 783.99)):
        start = int(RATE * 0.05 * i)
        part = tone(t, f) * env_exp(t, 5.0)
        out[start:] += part[: len(t) - start] * (0.9 - 0.15 * i)
    return out


CUES = {
    "stamp": stamp,
    "raise": raise_cue,
    "lower": lower_cue,
    "zero": zero_cue,
    "flip": flip_cue,
    "lock": lock_cue,
    "surge": surge_cue,
    "aim": aim_cue,
    "restore": restore_cue,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, make in CUES.items():
        path = os.path.join(OUT, name + ".ogg")
        sf.write(path, finish(make()), RATE, format="OGG", subtype="VORBIS")
        print(name, os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
