"""Draws what a forged strike is made of: the smear its blade leaves and the matter it throws.

The forge used to paint every strike with noise in a shader, which read as light and never as a
thing. These are drawn instead, pixel by pixel, the way a vanilla sprite is:

  - alpha is ordered-dithered (4x4 Bayer) onto four levels, so nothing is an airbrushed gradient;
  - values are held to five greys, so every sprite reads as hand-placed pixels;
  - everything is greyscale, because each is tinted in game by the element that throws it - an
    ember that cools does so by a colour ramp in the particle, never by a hue baked in here.

Run from the repo root:

    python scripts/forge-sprites.py [--sheet out.png]

and it rewrites textures/effect/forge_smear.png, textures/particle/forge/*.png and the two particle
definitions, particles/forge_matter.json and particles/forge_decal.json. Deterministic: the same
run writes the same bytes.
"""
import argparse
import json
import math
import os
import random
import zlib

from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "magical")
EFFECT = os.path.join(ROOT, "textures", "effect")
PARTICLE = os.path.join(ROOT, "textures", "particle", "forge")
DEFINITIONS = os.path.join(ROOT, "particles")

# ---------------------------------------------------------------------------------------------
# The pixel rules every sprite shares.

GREYS = (85, 125, 170, 215, 255)
ALPHAS = (0, 96, 176, 255)
BAYER = (
    (0, 8, 2, 10),
    (12, 4, 14, 6),
    (3, 11, 1, 9),
    (15, 7, 13, 5),
)


def rng_for(name):
    return random.Random(zlib.crc32(name.encode("utf-8")))


def grey(value):
    """The nearest of the five greys."""
    return min(GREYS, key=lambda g: abs(g - value))


def dither(alpha, x, y):
    """A coverage in [0, 1] onto one of four levels, ordered by the Bayer cell under (x, y)."""
    a = max(0.0, min(1.0, alpha)) * (len(ALPHAS) - 1)
    low = int(math.floor(a))
    if low >= len(ALPHAS) - 1:
        return ALPHAS[-1]
    threshold = (BAYER[y % 4][x % 4] + 0.5) / 16.0
    return ALPHAS[low + 1] if a - low > threshold else ALPHAS[low]


def blank(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def paint(img, x, y, value, alpha):
    """Writes one texel: dithered coverage, quantised grey. Fully clear texels stay black-clear."""
    a = dither(alpha, x, y)
    if a == 0:
        img.putpixel((x, y), (0, 0, 0, 0))
        return
    g = grey(value)
    img.putpixel((x, y), (g, g, g, a))


def solid(img, x, y, value, alpha=255):
    """Writes one texel exactly: for the hard pixels that should never be dithered away."""
    w, h = img.size
    if 0 <= x < w and 0 <= y < h:
        g = grey(value)
        img.putpixel((x, y), (g, g, g, alpha))


# ---------------------------------------------------------------------------------------------
# The smear atlas. 128 x 256: sixteen rows of sixteen texels, one row per way a blade can be drawn.
# u runs along the arc and tiles (every feature is laid out modulo the width); v runs across the
# blade, the inner side at y 0 and the cutting lip at y 13-14 of its row. y 15 is always clear, so
# a blade ends on a hard edge and never on the next row's first line.

ATLAS_W = 128
ROW_H = 16
ROWS = ["steel", "ember", "rime", "filament", "tatter", "run", "grit", "gust", "sheath", "lip"]
ATLAS_H = 256


class Row:
    """One row being drawn: a coverage and a value per texel, flattened to pixels at the end."""

    def __init__(self, name):
        self.name = name
        self.rng = rng_for("smear:" + name)
        self.alpha = [[0.0] * ATLAS_W for _ in range(ROW_H)]
        self.value = [[170.0] * ATLAS_W for _ in range(ROW_H)]
        self.hard = {}

    def body(self, profile, value):
        for y in range(ROW_H):
            for x in range(ATLAS_W):
                self.alpha[y][x] = profile(y)
                self.value[y][x] = value(y)

    def streaks(self, count, lengths, rows, lift):
        """Brush strokes along u: brighter runs whose ends thin out rather than stop."""
        for _ in range(count):
            y = self.rng.randint(*rows)
            start = self.rng.randrange(ATLAS_W)
            length = self.rng.randint(*lengths)
            for i in range(length):
                x = (start + i) % ATLAS_W
                taper = min(i, length - 1 - i) / max(1.0, length * 0.2)
                t = min(1.0, taper)
                self.value[y][x] = min(255.0, self.value[y][x] + lift * t)
                self.alpha[y][x] = max(self.alpha[y][x], 0.55 + 0.45 * t)

    def lip(self, rows=(13, 14), nicks=2):
        for y in rows:
            for x in range(ATLAS_W):
                self.hard[(x, y)] = 255
        for _ in range(nicks):
            x = self.rng.randrange(ATLAS_W)
            for dx in range(self.rng.randint(1, 2)):
                self.hard.pop(((x + dx) % ATLAS_W, rows[-1]), None)
                self.alpha[rows[-1]][(x + dx) % ATLAS_W] = 0.0

    def clear(self, x, y):
        self.alpha[y][x % ATLAS_W] = 0.0
        self.hard.pop((x % ATLAS_W, y), None)

    def draw(self, atlas, index):
        top = index * ROW_H
        for y in range(ROW_H):
            for x in range(ATLAS_W):
                if y == ROW_H - 1:
                    atlas.putpixel((x, top + y), (0, 0, 0, 0))
                    continue
                if (x, y) in self.hard:
                    g = grey(self.hard[(x, y)])
                    atlas.putpixel((x, top + y), (g, g, g, 255))
                    continue
                a = dither(self.alpha[y][x], x, top + y)
                if a == 0:
                    atlas.putpixel((x, top + y), (0, 0, 0, 0))
                else:
                    g = grey(self.value[y][x])
                    atlas.putpixel((x, top + y), (g, g, g, a))


def blade_profile(y):
    """Across a blade: speckled and broken on the inner side, solid by the middle, full at the lip."""
    ramp = (0.22, 0.30, 0.40, 0.52, 0.62, 0.72, 0.80, 0.86, 0.90, 0.92, 0.94, 0.96, 0.98, 1.0, 1.0, 0.0)
    return ramp[y]


def blade_value(y):
    return 170.0 + 45.0 * y / 14.0


def walk(rng, width, low, high, start=None, step=1):
    """A wrapped random walk across u, clamped to [low, high]: the shape of a ragged edge."""
    h = start if start is not None else rng.randint(low, high)
    out = []
    for _ in range(width):
        h = max(low, min(high, h + rng.randint(-step, step)))
        out.append(h)
    # Close the loop so the edge tiles: ease the tail back to where the walk began.
    span = max(1, width // 8)
    for i in range(span):
        t = (i + 1) / span
        out[width - span + i] = round(out[width - span + i] * (1 - t) + out[0] * t)
    return out


def steel():
    row = Row("steel")
    row.body(blade_profile, blade_value)
    row.streaks(12, (40, 100), (3, 12), 45)
    row.lip(nicks=1)
    return row


def ember_row():
    """Fire: the inner edge is torn into tongues licking back from the cut, the body holed."""
    row = Row("ember")
    row.body(blade_profile, blade_value)
    tongues = walk(row.rng, ATLAS_W, 0, 7, step=2)
    for x in range(ATLAS_W):
        edge = tongues[x]
        for y in range(edge):
            row.alpha[y][x] = 0.0
        if edge < ROW_H:
            row.value[edge][x] = 255.0
            row.alpha[edge][x] = max(row.alpha[edge][x], 0.7)
    for _ in range(40):
        x, y = row.rng.randrange(ATLAS_W), row.rng.randint(4, 11)
        row.alpha[y][x] *= 0.3
    row.streaks(9, (18, 50), (5, 12), 60)
    row.lip(nicks=3)
    return row


def rime():
    """Frost: a sawtooth of hoarfrost along the lip, hairline cracks through the body, lit facets."""
    row = Row("rime")
    row.body(blade_profile, lambda y: 190.0 + 30.0 * y / 14.0)
    row.lip(rows=(13,), nicks=0)
    x = 0
    while x < ATLAS_W:
        tooth = row.rng.randint(2, 4)
        for dx in range(tooth // 2 + 1):
            row.hard[((x + dx) % ATLAS_W, 14)] = 255
        row.hard[((x + tooth // 2) % ATLAS_W, 12)] = 255
        x += tooth + row.rng.randint(1, 3)
    for _ in range(7):
        cx, cy = row.rng.randrange(ATLAS_W), row.rng.randint(4, 11)
        for _ in range(row.rng.randint(5, 10)):
            row.value[cy][cx % ATLAS_W] = 125.0
            row.alpha[cy][cx % ATLAS_W] = 1.0
            cx += 1
            cy = max(3, min(12, cy + row.rng.choice((-1, 0, 1))))
    for _ in range(24):
        row.hard[(row.rng.randrange(ATLAS_W), row.rng.randint(5, 12))] = 255
    return row


def filament():
    """Storm: almost no body, a few one-texel filaments wandering along the cut, a thin lip."""
    row = Row("filament")
    row.body(lambda y: 0.0 if y == 15 else 0.18 * blade_profile(y), lambda y: 215.0)
    for strand in range(3):
        y = 5 + strand * 3 + row.rng.randint(0, 1)
        path = walk(row.rng, ATLAS_W, max(2, y - 2), min(12, y + 2), start=y)
        gap = 0
        for x in range(ATLAS_W):
            if gap > 0:
                gap -= 1
                continue
            if row.rng.random() < 0.05:
                gap = row.rng.randint(3, 9)
                continue
            row.hard[(x, path[x])] = 255 if strand != 1 else 215
    for x in range(ATLAS_W):
        row.hard[(x, 14)] = 255
    return row


def tatter():
    """Void and dark: a ragged, holed lip, the body torn through, the inner side gone to specks."""
    row = Row("tatter")
    row.body(lambda y: blade_profile(y) * (0.4 if y < 6 else 1.0), blade_value)
    for x in range(ATLAS_W):
        for y in range(6):
            if row.rng.random() < 0.55:
                row.alpha[y][x] = 0.0
    row.lip(nicks=9)
    for _ in range(9):
        x = row.rng.randrange(ATLAS_W)
        width = row.rng.randint(1, 3)
        top = row.rng.randint(4, 8)
        for dx in range(width):
            for y in range(top, 15):
                if row.rng.random() < 0.8:
                    row.clear(x + dx, y)
    row.streaks(6, (14, 40), (6, 12), 30)
    return row


def run_row():
    """Venom and blood: a heavy wet lip gathering into beads, each with its one lit pixel."""
    row = Row("run")
    row.body(blade_profile, lambda y: 150.0 + 50.0 * y / 14.0)
    row.lip(rows=(12, 13, 14), nicks=0)
    x = row.rng.randrange(10)
    while x < ATLAS_W - 3:
        for dy, (lo, hi) in ((10, (1, 2)), (11, (0, 3))):
            for dx in range(lo, hi + 1):
                row.hard[((x + dx) % ATLAS_W, dy)] = 215
        row.hard[((x + 1) % ATLAS_W, 10)] = 255
        x += row.rng.randint(9, 17)
    row.streaks(4, (20, 60), (5, 10), 35)
    return row


def grit_row():
    """Earth: the blade broken into chipped slabs with daylight between them, speckled like stone."""
    row = Row("grit")
    row.body(blade_profile, blade_value)
    for y in range(ROW_H):
        for x in range(ATLAS_W):
            row.value[y][x] += row.rng.choice((-45, 0, 0, 0, 30))
    row.lip(nicks=6)
    x = row.rng.randrange(20)
    while x < ATLAS_W:
        gap = row.rng.randint(2, 3)
        chip = row.rng.randint(3, 8)
        for dx in range(gap):
            for y in range(ROW_H - 1):
                row.clear(x + dx, y)
        for dy in range(chip):
            row.clear(x - 1, 14 - dy)
            row.clear(x + gap, 3 + dy)
        x += gap + row.rng.randint(18, 30)
    return row


def gust_row():
    """Gale: a few very long, thin strokes far apart, a feathered lip, hardly any body."""
    row = Row("gust")
    row.body(lambda y: 0.0 if y == 15 else 0.28 * blade_profile(y), lambda y: 215.0)
    row.streaks(7, (70, 120), (2, 12), 80)
    for x in range(ATLAS_W):
        row.hard[(x, 13)] = 255
        row.alpha[14][x] = 0.55
        row.value[14][x] = 255.0
    return row


def sheath_row():
    """The glow round a blade: a soft band peaking on the centreline, the same on both sides."""
    row = Row("sheath")
    row.body(lambda y: 0.0 if y == 15 else max(0.0, 1.0 - abs(y - 7.0) / 7.5) ** 1.5,
             lambda y: 235.0)
    for _ in range(8):
        y = row.rng.randint(5, 9)
        start = row.rng.randrange(ATLAS_W)
        for i in range(row.rng.randint(20, 60)):
            row.value[y][(start + i) % ATLAS_W] = 255.0
    return row


def lip_row():
    """The hot line of a cut and nothing else, broken by gaps, flaring at a few sparkle nodes."""
    row = Row("lip")
    row.body(lambda y: 0.0, lambda y: 255.0)
    x = 0
    while x < ATLAS_W:
        run_length = row.rng.randint(9, 26)
        for dx in range(run_length):
            row.hard[((x + dx) % ATLAS_W, 13)] = 255
            if row.rng.random() < 0.7:
                row.hard[((x + dx) % ATLAS_W, 14)] = 215
        x += run_length + row.rng.randint(1, 4)
    for _ in range(10):
        x = row.rng.randrange(ATLAS_W)
        for dx, dy in ((0, 12), (-1, 13), (1, 13), (0, 14)):
            row.hard[((x + dx) % ATLAS_W, dy)] = 255
    return row


def smear_atlas():
    atlas = blank(ATLAS_W, ATLAS_H)
    drawers = {"steel": steel, "ember": ember_row, "rime": rime, "filament": filament, "tatter": tatter,
               "run": run_row, "grit": grit_row, "gust": gust_row, "sheath": sheath_row, "lip": lip_row}
    for index, name in enumerate(ROWS):
        drawers[name]().draw(atlas, index)
    return atlas


# ---------------------------------------------------------------------------------------------
# The matter: 8x8 particle frames (16x16 for the hit nick, 32x32 for a crack decal).


def spark(frame):
    """A struck spark: a hot core that shrinks as it cools, with a faint cross of glow round it."""
    img = blank(8, 8)
    cores = (((3, 3), (4, 3), (3, 4), (4, 4)), ((3, 3), (3, 4)), ((3, 3),), ((3, 3),))
    halo = 0.62 if frame < 3 else 0.35
    for x, y in ((3, 2), (2, 3), (4, 2), (5, 3), (2, 4), (3, 5), (5, 4), (4, 5)):
        if frame < 2 or (x + y) % 2 == 0:
            paint(img, x, y, 215, halo)
    for x, y in cores[frame]:
        solid(img, x, y, 255 if frame < 3 else 215)
    return img


def ember(frame):
    """A coal: a rounded lump with a ragged rim, flickering, then crumbling to specks."""
    rng = rng_for("ember")
    cells = set()
    for y in range(8):
        for x in range(8):
            if math.hypot(x - 3.5, y - 3.5) < 1.9 + rng.uniform(-0.45, 0.45):
                cells.add((x, y))
    img = blank(8, 8)
    flicker = rng_for(f"ember:{frame}")
    if frame >= 3:
        keep = {0: 6, 1: 4, 2: 2}[frame - 3]
        cells = set(flicker.sample(sorted(cells), keep))
        for x, y in sorted(cells):
            solid(img, x, y + (frame - 3 if y < 6 else 0), 170)
        return img
    for x, y in sorted(cells):
        edge = any((x + dx, y + dy) not in cells for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        value = 215 if edge else 255
        if edge and flicker.random() < 0.3:
            value = 170
        solid(img, x, y, value)
    solid(img, 3, 3, 255)
    solid(img, 4, 4, 255)
    return img


def ash(index):
    """A flake of ash: small, flat, dull, one edge catching the light."""
    rng = rng_for(f"ash:{index}")
    img = blank(8, 8)
    cells = {(3, 3)}
    size = 3 + index % 3
    while len(cells) < size:
        x, y = rng.choice(sorted(cells))
        dx, dy = rng.choice(((1, 0), (-1, 0), (0, 1), (1, 1)))
        cells.add((min(6, x + dx), min(6, y + dy)))
    for x, y in sorted(cells):
        solid(img, x, y, 125)
    top = min(cells, key=lambda c: (c[1], c[0]))
    solid(img, top[0], top[1], 215)
    return img


def flake(index):
    """A snowflake: six arms on a hex-ish grid, branched, each of the four drawn differently."""
    img = blank(8, 8)
    arms = [(0, -1), (1, -1), (1, 1), (0, 1), (-1, 1), (-1, -1)]
    horizontal = [(1, 0), (-1, 0)]
    shapes = (
        (arms[0:1] + arms[3:4] + [(1, -1), (-1, 1), (1, 1), (-1, -1)], 3, False),
        (arms + horizontal, 2, True),
        ([(0, -1), (0, 1), (1, 0), (-1, 0)], 3, True),
        ([(1, -1), (-1, 1), (1, 1), (-1, -1), (0, -1), (0, 1)], 2, False),
    )
    directions, reach, branch = shapes[index]
    cx, cy = 3, 3
    solid(img, cx, cy, 255)
    for dx, dy in directions:
        for step in range(1, reach + 1):
            x, y = cx + dx * step, cy + dy * step
            solid(img, x, y, 255 if step < reach else 215)
            if branch and step == reach - 1:
                paint(img, max(0, min(7, x - dy)), max(0, min(7, y + dx)), 215, 0.45)
    return img


def drop(frame):
    """A drop of something wet: round, drawn out as it falls, then a splat, then the splat going."""
    img = blank(8, 8)
    shapes = (
        ((2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (4, 4), (3, 2), (3, 5)),
        ((3, 1), (3, 2), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (4, 4), (3, 5)),
        ((1, 5), (2, 5), (3, 5), (4, 5), (5, 5), (6, 5), (2, 4), (3, 4), (4, 4), (0, 3), (7, 4)),
        ((1, 5), (3, 5), (5, 5), (2, 4), (4, 4), (0, 3)),
    )
    for x, y in shapes[frame]:
        solid(img, x, y, 215 if frame < 3 else 170)
    if frame < 2:
        solid(img, 2, 3, 255)
    elif frame == 2:
        solid(img, 3, 4, 255)
    return img


def grit(index):
    """Grit: one to three angular specks, lit from the top left, outlined so they hold on sand."""
    rng = rng_for(f"grit:{index}")
    img = blank(8, 8)
    count = 1 + index % 3
    for n in range(count):
        ox, oy = rng.randint(1, 4), rng.randint(1, 4)
        w, h = rng.randint(2, 3), rng.randint(1, 2)
        for x in range(ox, ox + w):
            for y in range(oy, oy + h):
                lit = x == ox or y == oy
                solid(img, x, y, 255 if lit else 170)
        for x in range(ox - 1, ox + w + 1):
            for y in (oy - 1, oy + h):
                if 0 <= x < 8 and 0 <= y < 8 and img.getpixel((x, y))[3] == 0:
                    solid(img, x, y, 85, 176)
        if n == 0 and count == 1:
            solid(img, ox + w - 1, oy + h - 1, 125)
    return img


def gust(frame):
    """Moving air: a curling line that lengthens, then breaks into dashes as it spends itself."""
    img = blank(8, 8)
    # A hump that curls over at its head: seven pixels at most, laid out along x.
    path = [(0, 5), (1, 4), (2, 4), (3, 3), (4, 3), (5, 3), (6, 4), (6, 5)]
    length = (4, 6, 8, 8)[frame]
    for i, (x, y) in enumerate(path[:length]):
        if frame == 3 and i % 2 == 1:
            continue
        solid(img, x, y, 255 if i >= length - 3 else 215, 255 if frame < 3 else 176)
    return img


def hollow(frame):
    """A hollow: a thin dithered ring closing in, until it is one bright point."""
    img = blank(8, 8)
    if frame == 3:
        solid(img, 3, 3, 255)
        return img
    radius = (3.0, 2.2, 1.3)[frame]
    for y in range(8):
        for x in range(8):
            d = math.hypot(x - 3.5, y - 3.5)
            if abs(d - radius) < 0.55:
                paint(img, x, y, 255 if frame else 215, 0.9)
    return img


def nick(frame):
    """The flash of a blow landing: a line, a crescent opening, slivers coming apart."""
    img = blank(16, 16)

    def crescent(width, gap_from=None, gap_to=None, value=255, alpha=1.0):
        for i in range(-7, 8):
            if gap_from is not None and gap_from <= i <= gap_to:
                continue
            thick = width * (1.0 - (i / 7.5) ** 2)
            cx = 7.5 + i * 0.7
            cy = 7.5 - i * 0.7
            bow = 2.0 * (1.0 - (i / 7.5) ** 2)
            for t in range(int(round(thick)) + 1):
                x = int(round(cx + bow * 0.7 + t * 0.7))
                y = int(round(cy + bow * 0.7 + t * 0.7))
                if 0 <= x < 16 and 0 <= y < 16:
                    paint(img, x, y, value, alpha)

    if frame == 0:
        for i in range(-5, 6):
            solid(img, 7 + i, 8 - i, 255)
    elif frame == 1:
        crescent(2.4)
    elif frame == 2:
        crescent(1.6, -1, 1, 215)
    else:
        crescent(1.0, -3, 3, 170, 0.6)
    return img


def crack(index):
    """A crack decal: a speckled dent with fissures walking out of it, each lit on one side."""
    rng = rng_for(f"crack:{index}")
    img = blank(32, 32)
    cx, cy = 15.5, 15.5
    pit = 2.5 + index * 0.5
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if d < pit + rng.uniform(-0.6, 0.6):
                if rng.random() < 0.75:
                    solid(img, x, y, rng.choice((85, 85, 125)), 176)
    arms = 5 + index + rng.randint(0, 1)
    for arm in range(arms):
        angle = arm * math.tau / arms + rng.uniform(-0.3, 0.3)
        x, y = cx + math.cos(angle) * pit, cy + math.sin(angle) * pit
        length = rng.randint(9, 14)
        for step in range(length):
            angle += rng.uniform(-0.35, 0.35)
            x += math.cos(angle)
            y += math.sin(angle)
            ix, iy = int(round(x)), int(round(y))
            if not (0 <= ix < 32 and 0 <= iy < 32):
                break
            solid(img, ix, iy, 85, 255 if step < length * 0.6 else 176)
            # The lit lip on one side of the fissure: the edge the light catches.
            lx, ly = ix + (1 if math.sin(angle) > 0 else 0), iy + (1 if math.cos(angle) <= 0 else 0)
            if 0 <= lx < 32 and 0 <= ly < 32 and img.getpixel((lx, ly))[3] == 0:
                solid(img, lx, ly, 170, 96)
            if step > 3 and rng.random() < 0.12:
                bx, by = ix, iy
                branch = angle + rng.choice((-1, 1)) * 0.9
                for _ in range(rng.randint(2, 4)):
                    bx += math.cos(branch)
                    by += math.sin(branch)
                    if 0 <= int(round(bx)) < 32 and 0 <= int(round(by)) < 32:
                        solid(img, int(round(bx)), int(round(by)), 85, 176)
    return img


# The particle definition lists frames in this order; MatterSprite in Java holds the same offsets.
# The last three sets are sprites the mod already draws, listed by reference rather than copied.
MATTER = [
    ("spark", [spark(i) for i in range(4)]),
    ("ember", [ember(i) for i in range(6)]),
    ("ash", [ash(i) for i in range(4)]),
    ("flake", [flake(i) for i in range(4)]),
    ("drop", [drop(i) for i in range(4)]),
    ("grit", [grit(i) for i in range(4)]),
    ("gust", [gust(i) for i in range(4)]),
    ("hollow", [hollow(i) for i in range(4)]),
    ("nick", [nick(i) for i in range(4)]),
]
REUSED = [("shard", 4), ("mote", 4), ("wisp", 8)]
DECALS = [crack(i) for i in range(3)]


def write_definition(name, textures):
    with open(os.path.join(DEFINITIONS, name + ".json"), "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"textures": textures}, handle, indent=2)
        handle.write("\n")


def contact_sheet(path, atlas):
    """Everything at 4x on a dark ground, the atlas on top and the matter under it."""
    scale = 4
    sprites = [f for _, frames in MATTER for f in frames] + DECALS
    width = atlas.width * scale
    rows_of_sprites = math.ceil(sum(s.width * scale + 8 for s in sprites) / width) + 1
    sheet = Image.new("RGBA", (width, atlas.height * scale + rows_of_sprites * (32 * scale + 8) + 8),
                      (28, 30, 34, 255))
    sheet.alpha_composite(atlas.resize((atlas.width * scale, atlas.height * scale), Image.NEAREST), (0, 0))
    x, y = 0, atlas.height * scale + 8
    for sprite in sprites:
        big = sprite.resize((sprite.width * scale, sprite.height * scale), Image.NEAREST)
        if x + big.width > width:
            x, y = 0, y + 32 * scale + 8
        sheet.alpha_composite(big, (x, y))
        x += big.width + 8
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sheet", help="also write a contact sheet of everything drawn")
    args = parser.parse_args()
    os.makedirs(EFFECT, exist_ok=True)
    os.makedirs(PARTICLE, exist_ok=True)
    os.makedirs(DEFINITIONS, exist_ok=True)

    atlas = smear_atlas()
    atlas.save(os.path.join(EFFECT, "forge_smear.png"))

    textures = []
    for name, frames in MATTER:
        for i, image in enumerate(frames):
            image.save(os.path.join(PARTICLE, f"{name}_{i}.png"))
            textures.append(f"magical:forge/{name}_{i}")
    for name, count in REUSED:
        textures.extend(f"magical:{name}_{i}" for i in range(count))
    write_definition("forge_matter", textures)

    decals = []
    for i, image in enumerate(DECALS):
        image.save(os.path.join(PARTICLE, f"crack_{i}.png"))
        decals.append(f"magical:forge/crack_{i}")
    write_definition("forge_decal", decals)

    if args.sheet:
        contact_sheet(args.sheet, atlas)
    print("atlas rows:", len(ROWS), "matter frames:", len(textures), "decals:", len(decals))


if __name__ == "__main__":
    main()
