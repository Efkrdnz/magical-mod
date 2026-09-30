"""Draws the decorative blocks and writes everything a block needs to exist in game.

Eight kinds in sixteen dye colours, the same list as DecorKind and DecorKind.COLOURS; and the
masonry, five cuts in six greys and four conditions, the same lists as Masonry. Every
sprite is 16x16 pixel art drawn from a shade ramp of its colour, so a kind reads as one material in
every colour and the colours read as one family across kinds. Run from the repo root:

    python scripts/decor-blocks.py [--sheet out.png]

and it rewrites, for every block:
    assets/magical/textures/block/decor/<id>.png  (masonry under textures/block/masonry/)
    assets/magical/blockstates/<id>.json
    assets/magical/models/block/<id>.json
    assets/magical/items/<id>.json
    data/magical/loot_table/blocks/<id>.json
plus the mining and wool tags under data/minecraft/tags/block/ and the names in en_us.json.
--sheet also writes a contact sheet of every sprite at four times size, for looking at, and the
masonry's beside it with -masonry before the extension.
"""
import json
import math
import os
import random
import sys
import zlib

from PIL import Image

RES = os.path.join("src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "magical")
DATA = os.path.join(RES, "data")
LANG = os.path.join(ASSETS, "lang", "en_us.json")

# MagicalDecor.COLOURS, in order, with the base each ramp is built from.
COLOURS = [
    ("white", (233, 236, 236)),
    ("light_gray", (157, 161, 164)),
    ("gray", (92, 97, 104)),
    ("black", (48, 46, 56)),
    ("brown", (125, 84, 52)),
    ("red", (176, 46, 42)),
    ("orange", (232, 122, 40)),
    ("yellow", (240, 199, 52)),
    ("lime", (122, 194, 46)),
    ("green", (80, 112, 40)),
    ("cyan", (30, 150, 160)),
    ("light_blue", (84, 170, 222)),
    ("blue", (56, 76, 176)),
    ("purple", (124, 58, 180)),
    ("magenta", (190, 76, 186)),
    ("pink", (236, 146, 176)),
]

# DecorKind, in order: (suffix, name, tool tag or None).
KINDS = [
    ("runestone_bricks", "Runestone Bricks", "pickaxe"),
    ("runestone_tiles", "Runestone Tiles", "pickaxe"),
    ("sigil_stone", "Sigil Stone", "pickaxe"),
    ("glimmer_lamp", "Glimmer Lamp", None),
    ("crystal", "Crystal", "pickaxe"),
    ("woven_cloth", "Woven Cloth", None),
    ("painted_planks", "Painted Planks", "axe"),
    ("arcane_plating", "Arcane Plating", "pickaxe"),
]
NEEDS_STONE_TOOL = {"arcane_plating"}
WOOL = {"woven_cloth"}
TRANSLUCENT = {"crystal"}


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(c, f):
    """One step of a colour's ramp: under 1 darkens toward a cool shadow, over 1 lifts toward a warm light."""
    r, g, b = c
    if f <= 1.0:
        # Shadows keep a little blue so a dark step reads as shade, not as dirt.
        cool = (1.0 - f) * 0.18
        return (clamp(r * f), clamp(g * f), clamp(b * min(1.0, f + cool)))
    t = min(1.0, (f - 1.0) * 1.15)
    return (clamp(r + (255 - r) * t + 4 * t), clamp(g + (255 - g) * t + 2 * t), clamp(b + (255 - b) * t))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def rng_for(name):
    return random.Random(zlib.crc32(name.encode("utf-8")))


def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 255))


def put(img, x, y, rgb, a=255):
    img.putpixel((x, y), (rgb[0], rgb[1], rgb[2], a))


# ------------------------------------------------------------------------------------ the kinds

GLYPHS = [["#.#", ".#."], ["##.", ".##"], ["#.#", "###"], [".#.", "#.#"], ["###", "#.."], ["#..", "###"]]


def bricks(c, rnd, runes_on=True):
    img = canvas()
    runes = {(rnd.randrange(4), rnd.randrange(2)), (rnd.randrange(4), rnd.randrange(2))}
    for y in range(16):
        row, ly = y // 4, y % 4
        offset = 4 if row % 2 else 0
        for x in range(16):
            lx = (x + offset) % 8
            if ly == 3 or lx == 7:
                put(img, x, y, shade(c, 0.52))
                continue
            f = rnd.choice((0.93, 1.0, 1.0, 1.0, 1.06))
            if ly == 0:
                f = 1.14
            elif lx == 0:
                f = max(f, 1.07)
            elif ly == 2 or lx == 6:
                f = min(f, 0.9)
            put(img, x, y, shade(c, f))
    if not runes_on:
        return img
    # A rune cut into two of the bricks: a dark stroke with the lit edge under it.
    for index, (brick, row) in enumerate(sorted(runes)):
        glyph = GLYPHS[rnd.randrange(len(GLYPHS))]
        y0 = row * 8 + (4 if index % 2 else 0)
        x0 = ((brick * 8 + 2) - (4 if (y0 // 4) % 2 else 0)) % 16
        for gy, line in enumerate(glyph):
            for gx, ch in enumerate(line):
                if ch == "#":
                    put(img, (x0 + gx) % 16, y0 + gy, shade(c, 0.6))
    return img


def tiles(c, rnd):
    img = canvas()
    for y in range(16):
        for x in range(16):
            lx, ly = x % 8, y % 8
            tile = (x // 8 + y // 8) % 2
            if lx == 7 or ly == 7:
                f = 0.5
            elif lx == 0 and ly == 0:
                f = 1.28
            elif lx == 0 or ly == 0:
                f = 1.16
            elif lx == 6 or ly == 6:
                f = 0.8
            else:
                f = rnd.choice((0.97, 1.0, 1.0, 1.03)) * (0.94 if tile else 1.0)
                if lx + ly == 4 or lx + ly == 5:
                    f *= 1.05  # a faint polish sheen across each tile
            put(img, x, y, shade(c, f))
    return img


def sigil(c, rnd):
    img = canvas()
    carved = set()
    for y in range(2, 14):
        for x in range(2, 14):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            diamond = abs(abs(dx) + abs(dy) - 3.0) < 0.6
            if 4.1 <= d < 5.1 or diamond or (abs(dx) < 1 and abs(dy) < 1):
                carved.add((x, y))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                f = 0.55
            elif x == 1 or y == 1:
                f = 1.2
            elif x == 14 or y == 14:
                f = 0.78
            elif (x, y) in carved:
                f = 0.55
            elif (x - 1, y - 1) in carved:
                f = 1.2  # the lit lip under-right of each cut
            else:
                f = rnd.choice((0.84, 0.88, 0.88, 0.92))
            put(img, x, y, shade(c, f))
    return img


def glimmer(c, rnd):
    img = canvas()
    frame = mix(c, (58, 58, 66), 0.6)
    glow = shade(c, 1.05)
    white = mix(shade(c, 1.3), (255, 255, 250), 0.3)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, shade(frame, 0.55))
            elif x in (1, 14) or y in (1, 14):
                put(img, x, y, shade(frame, 1.1 if (x == 1 or y == 1) else 0.8))
            elif x in (7, 8) or y in (7, 8):
                put(img, x, y, shade(frame, 0.95 if x in (7, 8) and y in (7, 8) else 0.85))
            else:
                # Four panes, each brightest at its middle.
                px = 4.0 if x < 7 else 11.0
                py = 4.0 if y < 7 else 11.0
                d = math.hypot(x - px, y - py) / 2.6
                put(img, x, y, mix(white, glow, min(1.0, d)))
    for x, y in ((3, 3), (10, 10), (12, 4), (4, 11)):
        put(img, x, y, (255, 255, 248))
    return img


def crystal(c, rnd):
    img = canvas()
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                put(img, x, y, shade(c, 0.78), 215)
                continue
            band = ((x + 2 * y) // 5) % 3
            f = (0.86, 1.0, 1.16)[band]
            a = (150, 165, 180)[band]
            if (x - y) % 11 == 0 or (x - y) % 11 == 1:
                f, a = 1.42, 215  # a lit facet edge
            put(img, x, y, shade(c, f), a)
    for x, y in ((4, 3), (11, 9), (6, 12)):
        put(img, x, y, (255, 255, 255), 235)
    return img


def cloth(c, rnd):
    img = canvas()
    for y in range(16):
        for x in range(16):
            across = (x // 2 + y // 2) % 2 == 0
            if across:
                f = 1.08 if y % 2 == 0 else 0.86
            else:
                f = 1.04 if x % 2 == 0 else 0.84
            f *= rnd.choice((0.97, 1.0, 1.0, 1.03))
            if y in (13, 14):
                f *= 0.72  # an embroidered band, so a wall of cloth reads as hangings
            elif y == 12 or y == 15:
                f *= 1.12
            put(img, x, y, shade(c, f))
    return img


def planks(c, rnd):
    img = canvas()
    seams = [None, 5, 11, 2]
    for plank in range(4):
        tone = rnd.choice((0.95, 1.0, 1.03))
        grain = {}
        for ly in (1, 2):
            x = rnd.randrange(16)
            for _ in range(3):
                length = rnd.randrange(2, 6)
                for i in range(length):
                    grain[((x + i) % 16, ly)] = 0.86
                x = (x + length + rnd.randrange(2, 6)) % 16
        for ly in range(4):
            y = plank * 4 + ly
            for x in range(16):
                if ly == 3:
                    f = 0.6
                elif seams[plank] is not None and x == seams[plank]:
                    f = 0.62
                elif ly == 0:
                    f = 1.1 * tone
                else:
                    f = grain.get((x, ly), 1.0) * tone
                put(img, x, y, shade(c, f))
        if seams[plank] is not None:
            for nx in (seams[plank] - 2, seams[plank] + 2):
                put(img, nx % 16, plank * 4 + 1, shade(c, 0.5))
    return img


def plating(c, rnd):
    img = canvas()
    metal = mix(c, (154, 160, 166), 0.25)
    rivets = [(2, 2), (5, 2), (10, 2), (13, 2), (2, 13), (5, 13), (10, 13), (13, 13)]
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                f = 1.3
            elif x == 15 or y == 15:
                f = 0.52
            elif x == 7 or y == 7:
                f = 0.6
            elif x == 8 or y == 8:
                f = 1.18
            else:
                # Brushed: a slow vertical swell with a little grain.
                f = 1.0 + 0.07 * math.sin((y % 8) * 0.9) + rnd.choice((-0.03, 0.0, 0.0, 0.03))
            put(img, x, y, shade(metal, f))
    for x, y in rivets:
        put(img, x, y, shade(metal, 1.45))
        put(img, x + 1, y + 1, shade(metal, 0.5))
    return img


DRAW = {
    "runestone_bricks": bricks,
    "runestone_tiles": tiles,
    "sigil_stone": sigil,
    "glimmer_lamp": glimmer,
    "crystal": crystal,
    "woven_cloth": cloth,
    "painted_planks": planks,
    "arcane_plating": plating,
}


# ------------------------------------------------------------------------------------ the masonry

# Masonry.Shade, darkest first. Near-neutral, each leaning a touch warm or cool so a run of them
# reads as six stones rather than six settings of one grey.
SHADES = [
    ("onyx", (36, 35, 40)),
    ("charcoal", (60, 60, 66)),
    ("slate", (88, 93, 101)),
    ("ash", (124, 121, 116)),
    ("dove", (166, 167, 168)),
    ("chalk", (214, 211, 203)),
]
# Masonry.Cut: (suffix, path pattern).
CUTS = [
    ("bricks", "%s_bricks"),
    ("tiles", "%s_tiles"),
    ("cobblestone", "%s_cobblestone"),
    ("ashlar", "%s_ashlar"),
    ("polished", "polished_%s"),
]
# Masonry.Condition: (name, path prefix).
CONDITIONS = [("plain", ""), ("cracked", "cracked_"), ("mossy", "mossy_"), ("muddy", "muddy_")]

MOSS = [(48, 72, 28), (66, 98, 36), (88, 124, 44), (114, 148, 56)]
MUD = [(66, 47, 32), (88, 63, 41), (108, 79, 51), (130, 97, 63)]


def luminance(rgb):
    return 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]


def cobble(c, rnd):
    """Rounded stones: each pixel belongs to its nearest seed, measured round the tile so it repeats."""
    img = canvas()
    seeds = [(rnd.uniform(0, 16), rnd.uniform(0, 16)) for _ in range(8)]
    tones = [rnd.choice((0.86, 0.93, 1.0, 1.0, 1.07)) for _ in seeds]

    def owner(x, y):
        best, index = 1e9, 0
        for i, (sx, sy) in enumerate(seeds):
            dx = min(abs(x + 0.5 - sx), 16 - abs(x + 0.5 - sx))
            dy = min(abs(y + 0.5 - sy), 16 - abs(y + 0.5 - sy))
            d = dx * dx + dy * dy * 1.2
            if d < best:
                best, index = d, i
        return index

    grid = [[owner(x, y) for x in range(16)] for y in range(16)]
    for y in range(16):
        for x in range(16):
            here = grid[y][x]
            if grid[y][(x + 1) % 16] != here or grid[(y + 1) % 16][x] != here:
                f = 0.5
            elif grid[y][(x - 1) % 16] != here or grid[(y - 1) % 16][x] != here:
                f = 1.16
            else:
                f = tones[here] * rnd.choice((0.95, 1.0, 1.0, 1.04))
            put(img, x, y, shade(c, f))
    return img


def ashlar(c, rnd):
    """Two courses of big dressed blocks, the joints offset, a few chisel marks on each face."""
    img = canvas()
    seams = [(0, 10), (5, 13)]
    tones = {}
    for y in range(16):
        course = y // 8
        cuts = seams[course]
        for x in range(16):
            block = sum(1 for cut in cuts if x >= cut) % len(cuts)
            tone = tones.setdefault((course, block), rnd.choice((0.9, 0.96, 1.0, 1.04)))
            if y % 8 == 7 or (x + 1) % 16 in cuts:
                f = 0.52
            elif y % 8 == 0 or x in cuts:
                f = 1.15
            elif y % 8 == 6 or (x + 2) % 16 in cuts:
                f = 0.86
            else:
                f = tone * rnd.choice((0.95, 1.0, 1.0, 1.0, 1.04))
            put(img, x, y, shade(c, f))
    for _ in range(4):
        x, y = rnd.randrange(2, 13), rnd.choice((rnd.randrange(2, 5), rnd.randrange(10, 13)))
        put(img, x, y, shade(c, 0.78))
        put(img, x + 1, y + 1, shade(c, 0.84))
    return img


def polished(c, rnd):
    """A single dressed face: a thin bevel, fine speckle, the faintest sweep of polish."""
    img = canvas()
    for y in range(16):
        for x in range(16):
            if x == 0 or y == 0:
                f = 1.12
            elif x == 15 or y == 15:
                f = 0.7
            else:
                roll = rnd.random()
                f = 0.92 if roll < 0.08 else 1.07 if roll < 0.13 else 1.0
                f *= 1.0 + 0.05 * ((x + y) / 30.0 - 0.5)
            put(img, x, y, shade(c, f))
    return img


def recesses(img, c):
    """The joints and cuts: every pixel darker than the stone's own shadow step."""
    floor = luminance(shade(c, 0.66))
    return {(x, y) for y in range(16) for x in range(16) if luminance(img.getpixel((x, y))[:3]) <= floor}


def crack(img, c, rnd):
    """Two or three cracks wandering across the faces, each dark with a lit lip under it."""
    for _ in range(rnd.choice((2, 3))):
        x, y = rnd.randrange(16), rnd.randrange(16)
        dx, dy = rnd.choice(((1, 1), (-1, 1), (1, 0), (1, -1)))
        path = []
        for _ in range(rnd.randrange(6, 11)):
            path.append((x % 16, y % 16))
            if rnd.random() < 0.3:
                x, y = x + rnd.choice((0, dy)), y + rnd.choice((0, dx))
            else:
                x, y = x + dx, y + dy
        cracked = set(path)
        for px, py in path:
            put(img, px, py, shade(c, 0.4))
            below = (px, (py + 1) % 16)
            if below not in cracked:
                put(img, below[0], below[1], shade(c, 1.1))
    return img


def moss(img, c, rnd):
    """Moss that settled where water sits: blooms on the upper faces, and down into the joints."""
    joints = recesses(img, c)
    blooms = [(rnd.uniform(0, 16), rnd.choice((rnd.uniform(0, 7), rnd.uniform(0, 16))), rnd.uniform(1.6, 3.2))
              for _ in range(5)]
    for y in range(16):
        for x in range(16):
            depth = 0.0
            for bx, by, r in blooms:
                dx = min(abs(x - bx), 16 - abs(x - bx))
                d = math.hypot(dx, y - by) - r + rnd.uniform(-0.6, 0.6)
                depth = max(depth, -d)
            joint = (x, y) in joints and y < 10 and rnd.random() < 0.5
            if depth > 0 or joint:
                index = 0 if depth < 0.6 and not joint else rnd.choice((1, 2, 2, 3))
                put(img, x, y, MOSS[index])
    return img


def mud(img, c, rnd):
    """Caked mud: clots anywhere on the face, packed joints, a drip or two below each clot.

    Never a band rising from the bottom edge: a block is a tile, and a band is a brown stripe on
    every course of a wall stacked from it. Patches measured round the tile repeat seamlessly.
    """
    joints = recesses(img, c)
    clots = [(rnd.uniform(0, 16), rnd.uniform(0, 16), rnd.uniform(1.2, 2.4)) for _ in range(5)]
    caked = set()
    for y in range(16):
        for x in range(16):
            for bx, by, r in clots:
                dx = min(abs(x - bx), 16 - abs(x - bx))
                dy = min(abs(y - by), 16 - abs(y - by))
                if math.hypot(dx, dy * 1.3) < r + rnd.uniform(-0.5, 0.5):
                    caked.add((x, y))
                    break
    for x, y in caked:
        top = (x, (y - 1) % 16) not in caked
        put(img, x, y, MUD[2] if top else rnd.choice((MUD[0], MUD[1], MUD[1], MUD[2])))
    for x, y in joints:
        if (x, y) not in caked and rnd.random() < 0.38:
            put(img, x, y, MUD[1])
    for bx, by, r in clots[:2]:
        x = int(bx) % 16
        for step in range(1, rnd.randrange(2, 5)):
            put(img, x, int(by + r + step) % 16, MUD[0])
    for _ in range(3):
        put(img, rnd.randrange(16), rnd.randrange(16), MUD[3])
    return img


CUT_DRAW = {
    "bricks": lambda c, rnd: bricks(c, rnd, runes_on=False),
    "tiles": tiles,
    "cobblestone": cobble,
    "ashlar": ashlar,
    "polished": polished,
}
CONDITION_DRAW = {"plain": lambda img, c, rnd: img, "cracked": crack, "mossy": moss, "muddy": mud}


def masonry_blocks():
    """Masonry.all(): cut by cut, condition by condition, each run dark to light."""
    for cut, pattern in CUTS:
        for condition, prefix in CONDITIONS:
            for shade_name, base in SHADES:
                yield cut, condition, shade_name, base, prefix + pattern % shade_name


# ------------------------------------------------------------------------------------ the files

def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(value, out, indent=2)
        out.write("\n")


def title(snake):
    return " ".join(word.capitalize() for word in snake.split("_"))


def write_block(block_id, folder, translucent):
    """The blockstate, model, item definition and loot table every block here needs, all alike."""
    full = "magical:" + block_id
    write_json(os.path.join(ASSETS, "blockstates", block_id + ".json"),
               {"variants": {"": {"model": "magical:block/" + block_id}}})
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": "magical:block/" + folder + "/" + block_id}}
    if translucent:
        model["render_type"] = "minecraft:translucent"
    write_json(os.path.join(ASSETS, "models", "block", block_id + ".json"), model)
    write_json(os.path.join(ASSETS, "items", block_id + ".json"),
               {"model": {"type": "minecraft:model", "model": "magical:block/" + block_id}})
    write_json(os.path.join(DATA, "magical", "loot_table", "blocks", block_id + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": full}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "magical:blocks/" + block_id,
    })


def contact_sheet(sprites, columns, path):
    rows = (len(sprites) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * 68 - 4, rows * 68 - 4), (30, 30, 34, 255))
    for i, img in enumerate(sprites):
        big = img.resize((64, 64), Image.NEAREST)
        sheet.alpha_composite(big, ((i % columns) * 68, (i // columns) * 68))
    sheet.save(path)


def main():
    sheet_path = sys.argv[sys.argv.index("--sheet") + 1] if "--sheet" in sys.argv else None
    textures = os.path.join(ASSETS, "textures", "block", "decor")
    os.makedirs(textures, exist_ok=True)
    tags = {"mineable/pickaxe": [], "mineable/axe": [], "needs_stone_tool": [], "wool": [], "impermeable": []}
    names = {}
    sprites = []
    for suffix, kind_name, tool in KINDS:
        for colour, base in COLOURS:
            block_id = colour + "_" + suffix
            full = "magical:" + block_id
            img = DRAW[suffix](base, rng_for(block_id))
            img.save(os.path.join(textures, block_id + ".png"))
            sprites.append(img)
            write_block(block_id, "decor", suffix in TRANSLUCENT)

            if tool:
                tags["mineable/" + tool].append(full)
            if suffix in NEEDS_STONE_TOOL:
                tags["needs_stone_tool"].append(full)
            if suffix in WOOL:
                tags["wool"].append(full)
            if suffix in TRANSLUCENT:
                tags["impermeable"].append(full)
            names["block.magical." + block_id] = title(colour) + " " + kind_name

    masonry_textures = os.path.join(ASSETS, "textures", "block", "masonry")
    os.makedirs(masonry_textures, exist_ok=True)
    masonry = []
    for cut, condition, shade_name, base, block_id in masonry_blocks():
        rnd = rng_for(block_id)
        img = CONDITION_DRAW[condition](CUT_DRAW[cut](base, rnd), base, rnd)
        img.save(os.path.join(masonry_textures, block_id + ".png"))
        masonry.append(img)
        write_block(block_id, "masonry", False)
        tags["mineable/pickaxe"].append("magical:" + block_id)
        names["block.magical." + block_id] = title(block_id)

    for tag, values in tags.items():
        write_json(os.path.join(DATA, "minecraft", "tags", "block", tag + ".json"), {"replace": False, "values": values})

    names["itemGroup.magical.decor"] = "Magical Decor"
    names["itemGroup.magical.masonry"] = "Magical Masonry"
    write_names(names)

    if sheet_path:
        contact_sheet(sprites, 16, sheet_path)
        root, ext = os.path.splitext(sheet_path)
        contact_sheet(masonry, len(SHADES) * 2, root + "-masonry" + ext)
    print(f"{len(sprites)} decor and {len(masonry)} masonry blocks written")


def write_names(names):
    """Rewrites our keys in place in en_us.json: drops any old line for them, inserts after the armoury's."""
    with open(LANG, encoding="utf-8", newline="") as source:
        lines = source.read().split("\n")
    keys = {'"' + key + '"' for key in names}
    kept = [line for line in lines if line.strip().split(":", 1)[0] not in keys]
    anchor = next(i for i, line in enumerate(kept) if line.strip().startswith('"itemGroup.magical.armoury"'))
    ours = ["  " + json.dumps(key) + ": " + json.dumps(value) + "," for key, value in names.items()]
    kept[anchor + 1:anchor + 1] = ours
    with open(LANG, "w", encoding="utf-8", newline="") as out:
        out.write("\n".join(kept))
    json.loads("\n".join(kept))  # a broken lang file is a mod that will not load; fail here instead


if __name__ == "__main__":
    main()
