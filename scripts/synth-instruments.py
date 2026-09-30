#!/usr/bin/env python
"""Synthesizes the Authority of Sound's instruments.

Every file is one note at a fixed centre pitch, and the game moves it with the sound pitch
parameter (0.5..2.0, two octaves), so pitch 1.0 is always the centre note named below. Two sets:

    note/<instrument>  the Riff's voices, one per vanilla NoteBlockInstrument name, a note every
                       four ticks, so every attack is sharp and every tail short
    song/<part>        the Song's kit (kick, snare, hat, bass, lead), looping under play at
                       75-150 BPM, so it is soft and sits together

None of it is a vanilla sample: plucks are Karplus-Strong, bells are FM, bars are additive, the
drums are swept sines and filtered noise. Mono 32 kHz Vorbis into
src/main/resources/assets/magical/sounds/. Deterministic: each file draws from its own generator,
seeded from its own path, so adding an instrument never re-rolls another.

    python scripts/synth-instruments.py

Needs numpy and soundfile (libsndfile with Vorbis support).
"""

import os
import sys
import zlib

import numpy as np
import soundfile as sf

RATE = 32000
SOUNDS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical", "sounds")
PEAK = 0.6
FADE_IN_MS = 2.0
FADE_OUT_MS = 8.0
# Tonal content stays under this so a note pitched up an octave never folds over.
PARTIAL_LIMIT = 10000.0
# Room left after a signal when it is filtered through the FFT, so an IIR tail never wraps round.
FILTER_TAIL_S = 0.5

F_SHARP_2 = 92.4986
F_SHARP_3 = 184.9972
F_SHARP_4 = 369.9944
F_SHARP_5 = 739.9888


# ---------------------------------------------------------------- building blocks


def seconds(n):
    return np.arange(int(RATE * n)) / RATE


def env_exp(t, decay):
    return np.exp(-t * decay)


def attack(t, ms):
    return np.clip(t / (ms / 1000.0), 0.0, 1.0)


def release(t, length):
    """A raised-cosine close over the last `length` seconds, for sustained voices."""
    start = t[-1] - length
    x = np.clip((t - start) / length, 0.0, 1.0)
    return 0.5 + 0.5 * np.cos(np.pi * x)


def phase_of(t, freq):
    """Running phase in radians; freq is a scalar or an array over t."""
    freq = np.broadcast_to(np.asarray(freq, dtype=float), t.shape)
    return 2.0 * np.pi * np.cumsum(freq) / RATE


def tone(t, freq, phase=0.0):
    return np.sin(phase_of(t, freq) + phase)


def glide(t, start, end, time):
    """A frequency that falls (or rises) from start to end with time constant `time`."""
    return end + (start - end) * np.exp(-t / time)


def partials(t, freq, ratios, amps, decays):
    """Additive bar or bell: sine partials at ratios of freq, each with its own decay."""
    base = phase_of(t, freq)
    top = float(np.max(freq))
    out = np.zeros_like(t)
    for ratio, amp, decay in zip(ratios, amps, decays):
        if top * ratio < PARTIAL_LIMIT:
            out += amp * np.sin(base * ratio) * env_exp(t, decay)
    return out


def noise(rng, n):
    return rng.uniform(-1.0, 1.0, n)


def _rbj(kind, freq, q):
    w0 = 2.0 * np.pi * freq / RATE
    c, alpha = np.cos(w0), np.sin(w0) / (2.0 * q)
    if kind == "low":
        b = ((1 - c) / 2, 1 - c, (1 - c) / 2)
    elif kind == "high":
        b = ((1 + c) / 2, -(1 + c), (1 + c) / 2)
    else:
        b = (alpha, 0.0, -alpha)
    return b, (1 + alpha, -2 * c, 1 - alpha)


def biquad(x, kind, freq, q=0.707):
    """An RBJ biquad applied through the FFT: causal, exact, and no per-sample Python loop."""
    b, a = _rbj(kind, freq, q)
    n = 1 << (len(x) + int(RATE * FILTER_TAIL_S) - 1).bit_length()
    z = np.exp(-2j * np.pi * np.arange(n // 2 + 1) / n)
    h = (b[0] + b[1] * z + b[2] * z * z) / (a[0] + a[1] * z + a[2] * z * z)
    return np.fft.irfft(np.fft.rfft(x, n) * h, n)[: len(x)]


def lowpass(x, freq, q=0.707):
    return biquad(x, "low", freq, q)


def highpass(x, freq, q=0.707):
    return biquad(x, "high", freq, q)


def bandpass(x, freq, q=1.0):
    return biquad(x, "band", freq, q)


def karplus(rng, freq, length, ring, soften=1, pick=0.5):
    """A plucked string. The delay line is a whole number of samples, so the string is tuned
    near the note and then resampled onto it exactly. `ring` is the amplitude time constant in
    seconds, `soften` smooths the pluck (a finger rather than a pick), `pick` is where along the
    string it is plucked (small is near the bridge, brighter and thinner)."""
    period = max(2, int(round(RATE / freq - 0.5)))
    ratio = freq / (RATE / (period + 0.5))
    m = int(RATE * length)
    n = int(m * ratio) + 2
    burst = noise(rng, period)
    if soften > 1:
        burst = np.convolve(burst, np.ones(soften) / soften, mode="same")
    burst = burst - np.roll(burst, max(1, int(pick * period)))
    burst -= burst.mean()
    loss = np.exp(-period / (RATE * ring))
    y = np.zeros(n)
    y[:period] = burst
    k = period
    while k < n:  # one period per step, each drawn from the period before it
        end = min(k + period, n)
        cur = y[k - period : end - period]
        lo = k - period - 1
        prev = y[lo : end - period - 1] if lo >= 0 else np.concatenate(([0.0], y[: end - period - 1]))
        y[k:end] = loss * 0.5 * (cur + prev)
        k = end
    return np.interp(np.arange(m) * ratio, np.arange(n), y)


def fm(t, carrier, modulator, index):
    """Two-operator FM; index is a scalar or an envelope over t."""
    return np.sin(2.0 * np.pi * carrier * t + index * np.sin(2.0 * np.pi * modulator * t))


def pulse(t, freq, duty):
    """A band-limited pulse wave, summed from its Fourier series under PARTIAL_LIMIT."""
    base = phase_of(t, freq)
    out = np.zeros_like(t)
    for k in range(1, int(PARTIAL_LIMIT / freq) + 1):
        out += (2.0 / (k * np.pi)) * np.sin(np.pi * k * duty) * np.cos(base * k)
    return out


def square(t, freq):
    return pulse(t, freq, 0.5)


def drive(x, amount):
    """Soft-clips x at `amount` times its own peak, so a pluck's first spike stops setting the
    level and the body of the note comes up to meet the rest of the set."""
    top = np.max(np.abs(x)) or 1.0
    return np.tanh(amount * x / top)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def finish(x, peak=PEAK):
    x = np.asarray(x, dtype=float) - np.mean(x)
    fade_in = int(RATE * FADE_IN_MS / 1000.0)
    fade_out = int(RATE * FADE_OUT_MS / 1000.0)
    x[:fade_in] *= np.linspace(0.0, 1.0, fade_in)
    x[-fade_out:] *= 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, fade_out))
    top = np.max(np.abs(x)) or 1.0
    return x / top * peak


# ---------------------------------------------------------------- the Riff's voices


def harp(rng):
    # A soft finger pluck with a faint octave glint over it: the magical harp.
    t = seconds(0.9)
    string = karplus(rng, F_SHARP_4, 0.9, ring=0.28, soften=3, pick=0.3)
    glint = tone(t, 2 * F_SHARP_4) * env_exp(t, 5.0) * 0.08 + tone(t, 3 * F_SHARP_4) * env_exp(t, 9.0) * 0.04
    return drive(lowpass(string, 6000.0), 2.5) * 0.9 + glint * 2.5


def basedrum(rng):
    # A sine swept down into the chest, with a short click on top so it cuts at any pitch.
    t = seconds(0.32)
    body = tone(t, glide(t, 190.0, 52.0, 0.03)) * env_exp(t, 10.0)
    click = highpass(noise(rng, len(t)), 2500.0) * env_exp(t, 350.0) * 0.5
    return np.tanh(1.6 * (body + click)) * release(t, 0.08)


def snare(rng):
    t = seconds(0.25)
    shell = (tone(t, 190.0) + 0.6 * tone(t, 330.0)) * env_exp(t, 28.0) * 0.6
    wires = highpass(bandpass(noise(rng, len(t)), 3800.0, 0.6), 1400.0) * env_exp(t, 17.0) * 1.6
    return shell + wires


def hat(rng):
    # Six detuned squares through a high band - a metal plate - over a whisper of noise.
    t = seconds(0.16)
    metal = sum(square(t, f) for f in (205.3, 304.4, 369.6, 522.7, 540.0, 800.0))
    metal = highpass(bandpass(metal, 8200.0, 1.2), 6000.0)
    air = highpass(noise(rng, len(t)), 7000.0) * 0.5
    return (metal + air) * env_exp(t, 42.0)


def bass(rng):
    # A plucked synth bass: the pluck opens bright and closes over a round sine.
    t = seconds(0.6)
    freq = glide(t, F_SHARP_2 * 1.04, F_SHARP_2, 0.012)
    k = np.arange(1, 13)
    stack = partials(t, freq, k, 0.9 / k, 3.0 + 2.8 * k)
    sub = tone(t, freq) * env_exp(t, 4.5)
    thump = lowpass(noise(rng, len(t)), 900.0) * env_exp(t, 120.0) * 0.4
    return np.tanh(1.3 * (sub + 0.7 * stack + thump)) * attack(t, 3.0) * release(t, 0.12)


def flute(rng):
    # A breathy sine with a chiff on its lip, a late vibrato and a faint octave halo.
    t = seconds(0.55)
    vibrato = 1.0 + 0.004 * np.sin(2.0 * np.pi * 5.6 * t) * np.clip((t - 0.12) / 0.1, 0.0, 1.0)
    freq = F_SHARP_5 * vibrato
    body = tone(t, freq) + 0.14 * tone(t, 2 * freq) + 0.05 * tone(t, 3 * freq) + 0.05 * tone(t, 2.004 * freq)
    breath = bandpass(noise(rng, len(t)), F_SHARP_5, 5.0) * 0.8 + highpass(noise(rng, len(t)), 3000.0) * 0.06
    chiff = bandpass(noise(rng, len(t)), 2 * F_SHARP_5, 2.0) * env_exp(t, 45.0) * 1.2
    return (body + breath + chiff) * attack(t, 12.0) * env_exp(t, 2.0) * release(t, 0.12)


def bell(rng):
    # FM at an inharmonic ratio: a bright strike that mellows into a hum.
    t = seconds(1.2)
    # The index falls fast, so the strike is bright and the note under it is plainly F#5.
    index = 2.2 * env_exp(t, 5.0) + 0.25
    ring = fm(t, F_SHARP_5, F_SHARP_5 * 3.5, index) * env_exp(t, 3.2)
    body = partials(t, F_SHARP_5, (2.0, 3.01), (0.22, 0.08), (4.0, 6.0))
    strike = highpass(noise(rng, len(t)), 5000.0) * env_exp(t, 160.0) * 0.25
    return ring + body + strike


def guitar(rng):
    t = seconds(0.9)
    string = karplus(rng, F_SHARP_3, 0.9, ring=0.3, soften=1, pick=0.2)
    body = bandpass(string, 230.0, 2.0) * 0.5
    return drive(lowpass(string + body, 5500.0), 2.5) * attack(t, 1.0)


def chime(rng):
    # Glass bars: the modes of a free bar, a beating twin at the fundamental, a high sparkle.
    t = seconds(1.1)
    bars = partials(t, F_SHARP_5, (1.0, 2.756, 5.404, 8.933), (1.0, 0.45, 0.22, 0.1), (3.0, 5.0, 8.0, 12.0))
    twin = tone(t, F_SHARP_5 * 1.004) * env_exp(t, 3.4) * 0.35
    sparkle = tone(t, F_SHARP_5 * 4.0) * env_exp(t, 18.0) * 0.12
    tick = highpass(noise(rng, len(t)), 6000.0) * env_exp(t, 250.0) * 0.2
    return bars + twin + sparkle + tick


def xylophone(rng):
    # A hard wooden bar, tuned 1:3, dry, with the mallet's knock.
    t = seconds(0.4)
    bar = partials(t, F_SHARP_5, (1.0, 3.0, 6.0), (1.0, 0.35, 0.08), (9.0, 18.0, 32.0))
    knock = bandpass(noise(rng, len(t)), 2500.0, 1.5) * env_exp(t, 160.0) * 0.6
    return bar + knock


def iron_xylophone(rng):
    # A vibraphone: metal bars tuned 1:4:10 under a slow tremolo.
    t = seconds(0.9)
    bar = partials(t, F_SHARP_4, (1.0, 4.0, 10.0), (1.0, 0.3, 0.1), (3.2, 7.0, 14.0))
    tremolo = 1.0 - 0.22 * (0.5 - 0.5 * np.cos(2.0 * np.pi * 6.0 * t))
    hit = bandpass(noise(rng, len(t)), 4000.0, 2.0) * env_exp(t, 200.0) * 0.4
    return (bar * tremolo + hit) * release(t, 0.1)


def cow_bell(rng):
    # Two squares a stretched fifth apart through a band: a short clank and a ringing tail.
    t = seconds(0.35)
    clank = bandpass(square(t, F_SHARP_5) + 0.7 * square(t, F_SHARP_5 * 1.48), 1200.0, 1.4)
    level = env_exp(t, 24.0) + 0.22 * env_exp(t, 6.5)
    hit = highpass(noise(rng, len(t)), 4000.0) * env_exp(t, 300.0) * 0.2
    return clank * level + hit


def didgeridoo(rng):
    # A buzzing drone whose two formants wobble: harmonics of the pipe, weighted by where the
    # mouth's resonances are at every instant.
    t = seconds(0.75)
    freq = F_SHARP_2 * (1.0 + 0.004 * np.sin(2.0 * np.pi * 4.7 * t))
    base = phase_of(t, freq)
    k = np.arange(1, int(PARTIAL_LIMIT * 0.7 / F_SHARP_2) + 1)[:, None]
    at = k * F_SHARP_2
    f1 = 450.0 + 220.0 * np.sin(2.0 * np.pi * 3.2 * t)
    f2 = 1400.0 + 380.0 * np.sin(2.0 * np.pi * 3.2 * t + 1.2)
    gain = k ** -0.7 * (0.3 + 2.6 * np.exp(-(((at - f1) / 140.0) ** 2)) + 1.6 * np.exp(-(((at - f2) / 220.0) ** 2)))
    drone = np.sum(gain * np.exp(-at / 3000.0) * np.sin(k * base), axis=0)
    breath = bandpass(noise(rng, len(t)), 900.0, 1.0) * 0.15
    level = attack(t, 8.0) * (0.72 + 0.28 * env_exp(t, 9.0)) * release(t, 0.2)
    return (drone + breath) * level


def bit(rng):
    # A chiptune pulse: a thin eighth-duty blip, then a quarter duty, on a stepped envelope.
    t = seconds(0.35)
    split = int(RATE * 0.03)
    wave = np.concatenate([pulse(t, F_SHARP_4, 0.125)[:split], pulse(t, F_SHARP_4, 0.25)[split:]])
    steps = np.floor(env_exp(t, 6.0) * 15.0 + 0.5) / 15.0
    steps = np.convolve(steps, np.ones(16) / 16, mode="same")  # no clicks on the steps
    return wave * steps * release(t, 0.06)


def banjo(rng):
    # A bright string near the bridge over a drum-skin twang.
    t = seconds(0.5)
    string = karplus(rng, F_SHARP_4, 0.5, ring=0.14, soften=1, pick=0.12)
    head = bandpass(string, 1200.0, 3.0) * 0.6
    snap = highpass(noise(rng, len(t)), 3500.0) * env_exp(t, 220.0) * 0.4
    return drive(string + head, 3.5) + snap


def pling(rng):
    # An FM electric piano: the tine's tick, the bark of the index, a sparkle an octave up.
    t = seconds(0.8)
    body = fm(t, F_SHARP_4, F_SHARP_4, 2.4 * env_exp(t, 7.0) + 0.3) * env_exp(t, 3.4)
    tine = tone(t, F_SHARP_4 * 14.0) * env_exp(t, 45.0) * 0.12
    sparkle = tone(t, F_SHARP_4 * 2.0) * env_exp(t, 4.5) * 0.2
    return (body + tine + sparkle) * release(t, 0.1)


# ---------------------------------------------------------------- the Song's kit


def song_kick(rng):
    # Rounder and longer than the Riff's: a slower sweep, a padded click, a little warmth.
    t = seconds(0.36)
    body = tone(t, glide(t, 125.0, 48.0, 0.045)) * env_exp(t, 7.5)
    click = lowpass(noise(rng, len(t)), 1400.0) * env_exp(t, 180.0) * 0.35
    return np.tanh(1.3 * (body + click)) * release(t, 0.08)


def song_snare(rng):
    # A soft snare that leans toward a clap: a short shell, dark wires, a breath of room.
    t = seconds(0.28)
    shell = tone(t, glide(t, 230.0, 195.0, 0.02)) * env_exp(t, 32.0) * 0.5
    wires = lowpass(bandpass(noise(rng, len(t)), 2000.0, 0.6), 7000.0) * env_exp(t, 15.0)
    room = lowpass(noise(rng, len(t)), 3000.0) * env_exp(t, 7.0) * 0.12
    return shell + wires + room


def song_hat(rng):
    # A closed hat of noise alone - no metal - short and a shade dark.
    t = seconds(0.14)
    air = highpass(noise(rng, len(t)), 6500.0)
    sheen = bandpass(noise(rng, len(t)), 9500.0, 1.5) * 0.4
    return lowpass(air + sheen, 12000.0) * env_exp(t, 38.0)


def song_bass(rng):
    # A round sub pluck: a sine with a whisper of its octave, a tiny pitch drop, gently driven.
    t = seconds(0.55)
    freq = glide(t, F_SHARP_2 * 1.02, F_SHARP_2, 0.01)
    body = tone(t, freq) + 0.22 * tone(t, 2 * freq) * env_exp(t, 6.0) + 0.06 * tone(t, 3 * freq) * env_exp(t, 10.0)
    pluck = lowpass(noise(rng, len(t)), 600.0) * env_exp(t, 90.0) * 0.2
    shaped = (body + pluck) * attack(t, 3.0) * env_exp(t, 4.6)
    return np.tanh(1.5 * shaped) / np.tanh(1.5) * release(t, 0.12)


def song_lead(rng):
    # A marimba with a shimmer: wooden partials 1:4:10 and a slow-beating octave pair above.
    t = seconds(0.6)
    bar = partials(t, F_SHARP_4, (1.0, 3.99, 9.9), (1.0, 0.25, 0.06), (5.0, 14.0, 30.0))
    shimmer = (tone(t, 2.003 * F_SHARP_4) + tone(t, 1.997 * F_SHARP_4)) * env_exp(t, 5.5) * 0.07
    mallet = lowpass(noise(rng, len(t)), 2000.0) * env_exp(t, 140.0) * 0.3
    return (bar + shimmer + mallet) * release(t, 0.1)


# ---------------------------------------------------------------- the table

INSTRUMENTS = {
    "note/harp": harp,
    "note/basedrum": basedrum,
    "note/snare": snare,
    "note/hat": hat,
    "note/bass": bass,
    "note/flute": flute,
    "note/bell": bell,
    "note/guitar": guitar,
    "note/chime": chime,
    "note/xylophone": xylophone,
    "note/iron_xylophone": iron_xylophone,
    "note/cow_bell": cow_bell,
    "note/didgeridoo": didgeridoo,
    "note/bit": bit,
    "note/banjo": banjo,
    "note/pling": pling,
    "song/kick": song_kick,
    "song/snare": song_snare,
    "song/hat": song_hat,
    "song/bass": song_bass,
    "song/lead": song_lead,
}

# The kicks carry more weight than the rest; everything else peaks at PEAK.
PEAKS = {
    "note/basedrum": 0.7,
    "song/kick": 0.7,
}


def render(name):
    rng = np.random.default_rng(zlib.crc32(name.encode("utf-8")))
    return finish(INSTRUMENTS[name](rng), PEAKS.get(name, PEAK))


def main():
    for name in INSTRUMENTS:
        path = os.path.join(SOUNDS, *name.split("/")) + ".ogg"
        os.makedirs(os.path.dirname(path), exist_ok=True)
        sf.write(path, render(name), RATE, format="OGG", subtype="VORBIS")
        print(name, os.path.getsize(path), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
