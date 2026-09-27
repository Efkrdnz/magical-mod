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


if __name__ == "__main__":
    main()
