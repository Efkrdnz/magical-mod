"""Draws the decorative blocks and writes everything a block needs to exist in game.

Eight kinds in sixteen dye colours, the same list as DecorKind and MagicalDecor.COLOURS. Every
sprite is 16x16 pixel art drawn from a shade ramp of its colour, so a kind reads as one material in
every colour and the colours read as one family across kinds. Run from the repo root:

    python scripts/decor-blocks.py [--sheet out.png]

and it rewrites, for every block:
    assets/magical/textures/block/decor/<id>.png
    assets/magical/blockstates/<id>.json
    assets/magical/models/block/<id>.json
    assets/magical/items/<id>.json
    data/magical/loot_table/blocks/<id>.json
plus the mining and wool tags under data/minecraft/tags/block/ and the names in en_us.json.
--sheet also writes a contact sheet of every sprite at four times size, for looking at.
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


def bricks(c, rnd):
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


# ------------------------------------------------------------------------------------ the files

def write_json(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as out:
        json.dump(value, out, indent=2)
        out.write("\n")


def title(snake):
    return " ".join(word.capitalize() for word in snake.split("_"))


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

            write_json(os.path.join(ASSETS, "blockstates", block_id + ".json"),
                       {"variants": {"": {"model": "magical:block/" + block_id}}})
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": "magical:block/decor/" + block_id}}
            if suffix in TRANSLUCENT:
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

            if tool:
                tags["mineable/" + tool].append(full)
            if suffix in NEEDS_STONE_TOOL:
                tags["needs_stone_tool"].append(full)
            if suffix in WOOL:
                tags["wool"].append(full)
            if suffix in TRANSLUCENT:
                tags["impermeable"].append(full)
            names["block.magical." + block_id] = title(colour) + " " + kind_name

    for tag, values in tags.items():
        write_json(os.path.join(DATA, "minecraft", "tags", "block", tag + ".json"), {"replace": False, "values": values})

    names["itemGroup.magical.decor"] = "Magical Decor"
    write_names(names)

    if sheet_path:
        sheet = Image.new("RGBA", (16 * 16 * 4 + 15 * 4, len(KINDS) * 16 * 4 + (len(KINDS) - 1) * 4), (30, 30, 34, 255))
        for i, img in enumerate(sprites):
            col, row = i % 16, i // 16
            big = img.resize((64, 64), Image.NEAREST)
            sheet.alpha_composite(big, (col * 68, row * 68))
        sheet.save(sheet_path)
    print(f"{len(sprites)} decor blocks written")


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
