"""Draws the mod's own particle sprites: pale greyscale pixel art, tinted per particle in game.

Every sprite is white-to-grey with alpha, because the particle multiplies it by the school colour
it is spawned with - a sprite that carried its own hue would fight the tint. Run from the repo root:

    python scripts/particle-sprites.py

and it rewrites assets/magical/textures/particle/*.png and assets/magical/particles/*.json.
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "magical")
TEXTURES = os.path.join(ROOT, "textures", "particle")
DEFINITIONS = os.path.join(ROOT, "particles")

# A rune is five columns by seven rows of strokes inside an 8x8 cell. Eight of them, none a letter.
RUNES = [
    ["..#..", ".###.", "#.#.#", "..#..", "..#..", ".#.#.", "#...#"],
    ["#...#", ".#.#.", "..#..", "..#..", "..#..", ".#.#.", "#...#"],
    ["####.", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    ["..#..", "..#..", "#####", "..#..", ".#.#.", "#...#", "#...#"],
    [".###.", "#...#", "#.#.#", "#.#.#", "#...#", ".###.", "..#.."],
    ["#.#.#", "#.#.#", ".###.", "..#..", ".###.", "#.#.#", "#.#.#"],
    ["#....", "##...", "#.#..", "#..#.", "#.#..", "##...", "#...."],
    ["..#..", ".#.#.", "#...#", ".#.#.", "..#..", "..#..", ".###."],
]

# Shards: 8x8, '#' the body, '+' the lit edge, '-' the shadowed face.
SHARDS = [
    ["........", "...+....", "..+#-...", "..+#-...", ".+##-...", ".+#--...", "..#-....", "...-...."],
    ["........", "......+.", ".....+#.", "....+#-.", "...+#-..", "..+#-...", ".##-....", "........"],
    ["........", "........", "..+++...", ".+###-..", "..##--..", "...#-...", "........", "........"],
    ["........", "..+.....", "..##....", "..+#-...", "...##-..", "...+#-..", "....#-..", ".....-.."],
]

# Sigils: a 7x7 grid of strokes drawn at 2x into a 16x16 texture, in Sigil declaration order
# (src/main/java/.../magic/visual/sigil/Sigil.java). The first eight are the runes above, one
# column of padding each side, so the rune particle and the rune sigils are one drawing.
SIGILS = [(f"rune_{i}", ["." + row + "." for row in rows]) for i, rows in enumerate(RUNES)] + [
    ("flame", ["...#...", "..###..", ".##.##.", ".#...#.", "#..#..#", "#.###.#", ".#####."]),
    ("drop", ["...#...", "...#...", "..#.#..", ".#...#.", ".#...#.", ".#...#.", "..###.."]),
    ("snowflake", ["#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"]),
    ("leaf", ["...#...", "..#.#..", ".#.#.#.", ".#.#.#.", ".#.#.#.", "..#.#..", "...#..."]),
    ("wave", [".##....", "#..#..#", "....##.", ".......", ".##....", "#..#..#", "....##."]),
    ("sun", ["...#...", ".#...#.", "..###..", "#.###.#", "..###..", ".#...#.", "...#..."]),
    ("moon", ["..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."]),
    ("star", ["...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."]),
    ("sprout", ["##...##", "#.#.#.#", ".##.##.", "...#...", "...#...", "...#...", "..###.."]),
    ("eye", [".......", "..###..", ".#...#.", "#..#..#", ".#...#.", "..###..", "......."]),
    ("skull", [".#####.", "#######", "#..#..#", "#######", ".#####.", ".#.#.#.", "......."]),
    ("bone", [".......", ".......", "##...##", ".#####.", "##...##", ".......", "......."]),
    ("fang", ["#######", "#.#.#.#", ".......", "#.....#", "##...##", ".#...#.", "......."]),
    ("heart", [".##.##.", "#..#..#", "#.....#", ".#...#.", "..#.#..", "...#...", "......."]),
    ("paw", [".#.#.#.", ".......", "#.....#", "..###..", ".#####.", ".#####.", "..###.."]),
    ("feather", [".....##", "....###", "...###.", "..###..", ".###...", ".#.....", "#......"]),
    ("key", [".......", ".##....", "#..#...", "#..####", ".##.#.#", ".......", "......."]),
    ("hourglass", ["#######", ".#...#.", "..#.#..", "...#...", "..###..", ".#####.", "#######"]),
    ("crown", [".......", "#..#..#", "##.#.##", "#######", "#.#.#.#", "#######", "......."]),
    ("shield", ["#######", "#..#..#", "#..#..#", "#..#..#", ".#.#.#.", "..#.#..", "...#..."]),
    ("coin", ["..###..", ".#...#.", "#.###.#", "#.#.#.#", "#.###.#", ".#...#.", "..###.."]),
    ("sword", ["......#", ".....#.", "....#..", ".#.#...", "..#....", ".#.#...", "#......"]),
    ("hammer", [".###...", "#####..", ".####..", "...#...", "....#..", ".....#.", "......#"]),
    ("flask", ["..###..", "..#.#..", "..#.#..", ".#...#.", "#.....#", "#.###.#", ".#####."]),
    ("anchor", ["...#...", "..###..", "...#...", "...#...", "#..#..#", ".#.#.#.", "..###.."]),
    ("gear", ["...#...", ".#####.", ".#...#.", "###.###", ".#...#.", ".#####.", "...#..."]),
    ("link", [".###...", "#...#..", "#..###.", ".###..#", "...#..#", "...####", "......."]),
    ("plus", ["...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..."]),
    ("arrow", ["...#...", "..###..", ".#.#.#.", "#..#..#", "...#...", "...#...", "...#..."]),
    ("chevron", ["...#...", "..#.#..", ".#...#.", "...#...", "..#.#..", ".#...#.", "......."]),
    ("diamond", ["...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#..."]),
    ("triangle", ["...#...", "...#...", "..#.#..", "..#.#..", ".#...#.", ".#...#.", "#######"]),
    ("ring", ["..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.."]),
    ("spiral", ["#######", "......#", ".####.#", ".#..#.#", ".#.##.#", ".#....#", ".######"]),
    ("thorn", ["#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"]),
]

SIGIL_SIDE = 16

WHITE = (255, 255, 255)


def blank():
    return Image.new("RGBA", (8, 8), (0, 0, 0, 0))


def save(image, name):
    image.save(os.path.join(TEXTURES, name + ".png"))


def rune(index):
    image = blank()
    rows = RUNES[index]
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell == "#":
                image.putpixel((x + 1, y), WHITE + (255,))
    # a one-pixel glow under the strokes so the rune reads at a distance without a halo sprite
    glow = blank()
    for y in range(8):
        for x in range(8):
            if image.getpixel((x, y))[3]:
                continue
            near = sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                       if 0 <= x + dx < 8 and 0 <= y + dy < 8 and image.getpixel((x + dx, y + dy))[3])
            if near:
                glow.putpixel((x, y), (200, 200, 200, min(110, 40 * near)))
    glow.alpha_composite(image)
    return glow


def shard(index):
    image = blank()
    shade = {"#": (222, 222, 222, 255), "+": (255, 255, 255, 255), "-": (150, 150, 150, 255)}
    for y, row in enumerate(SHARDS[index]):
        for x, cell in enumerate(row):
            if cell in shade:
                image.putpixel((x, y), shade[cell])
    return image


def mote(frame):
    """Four frames of a twinkle: a point that opens into a four-pointed star and closes again."""
    image = blank()
    reach = [1, 2, 3, 2][frame]
    core = [200, 255, 255, 220][frame]
    for y in range(8):
        for x in range(8):
            dx, dy = x - 3.5, y - 3.5
            r = math.hypot(dx, dy)
            a = max(0.0, 1.0 - r / 2.2) * core
            # the arms of the star, one pixel wide along the two axes
            if abs(dx) < 0.6 and abs(dy) <= reach + 0.5 or abs(dy) < 0.6 and abs(dx) <= reach + 0.5:
                a = max(a, core * (1.0 - max(abs(dx), abs(dy)) / (reach + 1.5)))
            if a > 8:
                image.putpixel((x, y), WHITE + (int(min(255, a)),))
    return image


def wisp(frame):
    """Eight frames of a soft puff thinning out: the radius holds while the edge frays."""
    image = blank()
    thin = frame / 7.0
    for y in range(8):
        for x in range(8):
            dx, dy = x - 3.5, y - 3.5
            r = math.hypot(dx, dy) / 3.9
            if r >= 1.0:
                continue
            # a lumpy edge: the fray bites more where a fixed hash says so
            bite = ((x * 7 + y * 13 + frame * 5) % 9) / 9.0
            a = (1.0 - r) ** 0.8 * (1.0 - thin * (0.55 + 0.45 * bite))
            v = int(235 - 40 * r)
            if a > 0.03:
                image.putpixel((x, y), (v, v, v, int(255 * min(1.0, a))))
    return image


def sigil_layers(rows):
    """The core (strokes at 2x, one texel in from the edge) and the glow (the texels touching a
    stroke: opaque edge-on, softer where only a corner touches). Pale grey, the ink is the hue."""
    core = Image.new("RGBA", (SIGIL_SIDE, SIGIL_SIDE), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, cell in enumerate(row):
            if cell == "#":
                for dy in (0, 1):
                    for dx in (0, 1):
                        core.putpixel((1 + 2 * x + dx, 1 + 2 * y + dy), WHITE + (255,))
    glow = Image.new("RGBA", (SIGIL_SIDE, SIGIL_SIDE), (0, 0, 0, 0))

    def lit(x, y):
        return 0 <= x < SIGIL_SIDE and 0 <= y < SIGIL_SIDE and core.getpixel((x, y))[3] > 0

    for y in range(SIGIL_SIDE):
        for x in range(SIGIL_SIDE):
            if lit(x, y):
                continue
            edge = any(lit(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            corner = any(lit(x + dx, y + dy) for dx, dy in ((1, 1), (1, -1), (-1, 1), (-1, -1)))
            if edge:
                glow.putpixel((x, y), (255, 255, 255, 255))
            elif corner:
                glow.putpixel((x, y), (235, 235, 235, 170))
    return core, glow


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(DEFINITIONS, exist_ok=True)
    sets = {
        "rune": [rune(i) for i in range(len(RUNES))],
        "shard": [shard(i) for i in range(len(SHARDS))],
        "mote": [mote(i) for i in range(4)],
        "wisp": [wisp(i) for i in range(8)],
    }
    for name, frames in sets.items():
        names = []
        for i, image in enumerate(frames):
            save(image, f"{name}_{i}")
            names.append(f"magical:{name}_{i}")
        with open(os.path.join(DEFINITIONS, name + ".json"), "w", encoding="utf-8", newline="\n") as handle:
            json.dump({"textures": names}, handle, indent=2)
            handle.write("\n")
    print("sprites:", {name: len(frames) for name, frames in sets.items()})
    # the sigil particle reads core i at i and its glow at len(SIGILS) + i
    cores = []
    glows = []
    for name, rows in SIGILS:
        core, glow = sigil_layers(rows)
        save(core, f"sigil_{name}")
        save(glow, f"sigil_{name}_glow")
        cores.append(f"magical:sigil_{name}")
        glows.append(f"magical:sigil_{name}_glow")
    with open(os.path.join(DEFINITIONS, "sigil.json"), "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"textures": cores + glows}, handle, indent=2)
        handle.write("\n")
    print("sigils:", len(SIGILS))


if __name__ == "__main__":
    main()
