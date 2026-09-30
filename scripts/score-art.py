"""Draws the Score screen's art: one 128x128 atlas of pale pixel art, tinted in game.

    python scripts/score-art.py [--sheet OUT.png]

Everything is greyscale for the same reason the particle sprites are: the screen tints each piece
with the colour of the track, family or state it stands for, so one drawing serves every colour.
Shapes are written as masks; the script gives every mask the same treatment - a lit upper-left
edge, a shaded lower-right one and a darker rim round the outside - so the whole sheet reads as one
hand. The beads are drawn procedurally at the two cell sizes the grid uses, 8 and 12, because a
drum head and a diamond must be exact circles and diamonds at eight pixels, not rescaled ones.

Where each sprite sits is mirrored in client/screen/sound/ScoreArt.java.
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical")
OUT = os.path.join(ROOT, "textures", "gui", "score", "atlas.png")

LIGHT = 255
BODY = 232
SHADE = 176
RIM = 96
RIM_ALPHA = 225
DARK = 70


def masked(rows, outline=True):
    """A mask drawn with the house treatment. '#' is body, 'x' is a dark detail inside the body."""
    h = len(rows)
    w = len(rows[0])
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and rows[y][x] in "#x"

    for y in range(h):
        for x in range(w):
            c = rows[y][x]
            if c == "#":
                if not solid(x - 1, y) or not solid(x, y - 1):
                    g = LIGHT
                elif not solid(x + 1, y) or not solid(x, y + 1):
                    g = SHADE
                else:
                    g = BODY
                image.putpixel((x, y), (g, g, g, 255))
            elif c == "x":
                image.putpixel((x, y), (DARK, DARK, DARK, 255))
    if outline:
        for y in range(h):
            for x in range(w):
                if solid(x, y):
                    continue
                if any(solid(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    image.putpixel((x, y), (RIM, RIM, RIM, RIM_ALPHA))
    return image


# A formula star thins to a dot at eight pixels, so the star is drawn by hand: a four-point sparkle
# at twelve, and at eight the plus that is all a sparkle can be in that many pixels.
STARS = {
    12: [
        "............",
        ".....##.....",
        ".....##.....",
        "....####....",
        "...######...",
        ".##########.",
        ".##########.",
        "...######...",
        "....####....",
        ".....##.....",
        ".....##.....",
        "............",
    ],
    8: [
        "........",
        "...##...",
        "...##...",
        ".######.",
        ".######.",
        "...##...",
        "...##...",
        "........",
    ],
}


def bead(size, shape):
    """A lit bead: a drum head, a diamond or a four-pointed star, lit from the upper left."""
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2.0
    r = size / 2.0 - 1.0

    def inside(x, y):
        dx, dy = (x - c) / r, (y - c) / r
        if shape == "circle":
            return dx * dx + dy * dy <= 1.0
        if shape == "diamond":
            return abs(dx) + abs(dy) <= 1.05
        raise ValueError(shape)

    if shape == "star":
        cells = [[char == "#" for char in row] for row in STARS[size]]
    else:
        cells = [[inside(x, y) for x in range(size)] for y in range(size)]
    for y in range(size):
        for x in range(size):
            if not cells[y][x]:
                continue
            dx, dy = (x - c) / r, (y - c) / r
            light = 0.55 - 0.45 * (dx + dy) / 1.4
            g = int(max(150, min(255, 190 + 90 * light)))
            edge = any(not (0 <= x + ex < size and 0 <= y + ey < size and cells[y + ey][x + ex])
                       for ex, ey in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if edge and dx + dy > 0.2:
                g = SHADE
            image.putpixel((x, y), (g, g, g, 255))
    # a glint: the brightest pixel up and left of centre
    gx = int(round(c - r * 0.4))
    gy = int(round(c - r * 0.4))
    if cells[gy][gx]:
        image.putpixel((gx, gy), (255, 255, 255, 255))
    return image


def socket(size, beat):
    """An empty cell: a small engraved hollow, a dark floor with a lit lip along its lower edge."""
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inset = 1
    floor = 150 if beat else 110
    for y in range(inset, size - inset):
        for x in range(inset, size - inset):
            corner = (x in (inset, size - inset - 1)) and (y in (inset, size - inset - 1))
            if corner:
                continue
            if y == size - inset - 1 or x == size - inset - 1:
                image.putpixel((x, y), (235, 235, 235, 70 if beat else 50))
            elif y == inset or x == inset:
                image.putpixel((x, y), (20, 20, 20, 150))
            else:
                image.putpixel((x, y), (40, 40, 40, floor))
    return image


GLYPHS16 = {
    "drum": [
        "................",
        "................",
        "................",
        "...##########...",
        "..############..",
        "..#xxxxxxxxxx#..",
        "..############..",
        "..#x##x##x##x#..",
        "..##x##x##x###..",
        "..#x##x##x##x#..",
        "..##x##x##x###..",
        "..############..",
        "...##########...",
        "................",
        "................",
        "................",
    ],
    "bass_clef": [
        "................",
        "................",
        "....#####.......",
        "...##...###.....",
        "..##.....##..##.",
        "..###....##..##.",
        "..###....##.....",
        "...#.....##..##.",
        ".........##..##.",
        "........##......",
        ".......##.......",
        "......##........",
        "....###.........",
        "..###...........",
        "................",
        "................",
    ],
    "treble_clef": [
        ".......##.......",
        "......#.##......",
        "......#.##......",
        "......###.......",
        "......##........",
        ".....###........",
        "....##.#........",
        "...##..####.....",
        "...#..##.#.#....",
        "...#..#..#..#...",
        "...##..#.#.##...",
        "....###..###....",
        ".........#......",
        "....##...#......",
        "....###.##......",
        ".....####.......",
    ],
    "waves": [
        "................",
        "................",
        "................",
        "..##......##....",
        ".#..#....#..#...",
        "#....#..#....#..",
        "......##......#.",
        "................",
        "..##......##....",
        ".#..#....#..#...",
        "#....#..#....#..",
        "......##......#.",
        "................",
        "................",
        "................",
        "................",
    ],
    "keys": [
        "................",
        "................",
        ".##############.",
        ".##xx#xx##xx#x#.",
        ".##xx#xx##xx#x#.",
        ".##xx#xx##xx#x#.",
        ".##xx#xx##xx#x#.",
        ".###x##x###x#x#.",
        ".###x##x###x#x#.",
        ".#############x.",
        ".####x###x###x#.",
        ".####x###x###x#.",
        ".##############.",
        "................",
        "................",
        "................",
    ],
    "bell": [
        "................",
        ".......##.......",
        "......####......",
        ".....######.....",
        ".....######.....",
        "....########....",
        "....########....",
        "....########....",
        "...##########...",
        "..############..",
        "..#xxxxxxxxxx#..",
        "..############..",
        ".......##.......",
        "......####......",
        "................",
        "................",
    ],
    "lyre": [
        "................",
        "..##........##..",
        ".#..#......#..#.",
        ".#..##########.#",
        "..#.#.#.#.#.#.#.",
        "..#.#.#.#.#.#.#.",
        "..#.#.#.#.#.#.#.",
        "..#.#.#.#.#.#.#.",
        "..#.#.#.#.#.#.#.",
        "..##.#.#.#.#.##.",
        "...###########..",
        "......####......",
        "......####......",
        "................",
        "................",
        "................",
    ],
    "quarter": [
        "................",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".........##.....",
        ".....######.....",
        "...########.....",
        "..#########.....",
        "..########......",
        "...######.......",
        "................",
    ],
    "bolt_note": [
        "................",
        "........#####...",
        "........######..",
        "........##.###..",
        "........##..##..",
        "........##......",
        "....######......",
        "..##########....",
        ".#########......",
        "..######........",
        ".....##.........",
        "....##..........",
        "...##...........",
        "..##............",
        "................",
        "................",
    ],
}

ICONS12 = {
    "import": [
        "............",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "...######...",
        "....####....",
        ".....##.....",
        "............",
        ".#........#.",
        ".#........#.",
        ".##########.",
        "............",
    ],
    "export": [
        "............",
        ".....##.....",
        "....####....",
        "...######...",
        ".....##.....",
        ".....##.....",
        ".....##.....",
        "............",
        ".#........#.",
        ".#........#.",
        ".##########.",
        "............",
    ],
    "sheet": [
        "............",
        "..#######...",
        "..########..",
        "..#########.",
        "..#xxxxxxx#.",
        "..#########.",
        "..#xxxxxxx#.",
        "..#########.",
        "..#xxxxxxx#.",
        "..#########.",
        "..#########.",
        "............",
    ],
    "play": [
        "............",
        "...#........",
        "...###......",
        "...#####....",
        "...#######..",
        "...########.",
        "...#######..",
        "...#####....",
        "...###......",
        "...#........",
        "............",
        "............",
    ],
    "stop": [
        "............",
        "............",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "..########..",
        "............",
        "............",
    ],
    "seal": [
        "............",
        "....####....",
        "..##xx#x##..",
        ".##x#x#xx##.",
        ".#xxxxxxxx#.",
        ".##x#x#xx##.",
        ".#xxxxxxxx#.",
        ".##x#x#xx##.",
        "..##xxxx##..",
        "....####....",
        "...##..##...",
        "..##....##..",
    ],
    "metronome": [
        ".....#......",
        ".....##.....",
        "....#.##....",
        "....#..#....",
        "...#..#.#...",
        "...#.#..#...",
        "..#.#....#..",
        "..##.....#..",
        ".#........#.",
        ".##########.",
        ".##########.",
        "............",
    ],
    "sharp": [
        "............",
        "...#...#....",
        "...#...#.##.",
        "...#.###.#..",
        "..####.#....",
        "...#...#....",
        "...#.###.#..",
        "..####.#....",
        "...#...#....",
        "...#...#....",
        "............",
        "............",
    ],
    "prev": [
        "............",
        "............",
        "......##....",
        ".....###....",
        "....####....",
        "...#####....",
        "....####....",
        ".....###....",
        "......##....",
        "............",
        "............",
        "............",
    ],
    "next": [
        "............",
        "............",
        "....##......",
        "....###.....",
        "....####....",
        "....#####...",
        "....####....",
        "....###.....",
        "....##......",
        "............",
        "............",
        "............",
    ],
    "rest": [
        "............",
        ".....##.....",
        "......##....",
        ".....##.....",
        "....##......",
        ".....##.....",
        "......##....",
        ".....#......",
        "....####....",
        ".....###....",
        "......#.....",
        "............",
    ],
}


def flourish():
    """A divider: a line fading out both ways from a small diamond with two dots either side."""
    w, h = 128, 9
    image = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    mid = w // 2
    for x in range(4, w - 4):
        d = abs(x - mid) / (mid - 4)
        alpha = int(200 * (1.0 - d) ** 1.5)
        if abs(x - mid) > 7 and alpha > 8:
            image.putpixel((x, 4), (230, 230, 230, alpha))
    for dy in range(-3, 4):
        span = 3 - abs(dy)
        for dx in range(-span, span + 1):
            g = 255 if dx + dy <= 0 else 190
            image.putpixel((mid + dx, 4 + dy), (g, g, g, 255))
    for side in (-1, 1):
        for step in (7, 10):
            image.putpixel((mid + side * step, 4), (235, 235, 235, 255))
        image.putpixel((mid + side * 5, 3), (200, 200, 200, 200))
        image.putpixel((mid + side * 5, 5), (200, 200, 200, 200))
    return image


# where every sprite goes: name -> (x, y). Mirrored in ScoreArt.java.
LAYOUT = {
    "bead_circle_12": (0, 0), "bead_diamond_12": (12, 0), "bead_star_12": (24, 0),
    "socket_12": (36, 0), "socket_beat_12": (48, 0),
    "bead_circle_8": (0, 12), "bead_diamond_8": (8, 12), "bead_star_8": (16, 12),
    "socket_8": (24, 12), "socket_beat_8": (32, 12),
    "drum": (0, 24), "bass_clef": (16, 24), "treble_clef": (32, 24), "waves": (48, 24),
    "keys": (64, 24), "bell": (80, 24), "lyre": (96, 24), "quarter": (112, 24),
    "bolt_note": (0, 40),
    "play": (16, 40), "stop": (28, 40), "seal": (40, 40), "metronome": (52, 40), "sharp": (64, 40),
    "prev": (76, 40), "next": (88, 40), "rest": (100, 40),
    "flourish": (0, 56),
    "import": (0, 68), "export": (12, 68), "sheet": (24, 68),
}


def sprites():
    out = {}
    for size in (12, 8):
        for shape in ("circle", "diamond", "star"):
            out[f"bead_{shape}_{size}"] = bead(size, shape)
        out[f"socket_{size}"] = socket(size, False)
        out[f"socket_beat_{size}"] = socket(size, True)
    for name, rows in GLYPHS16.items():
        out[name] = masked(rows)
    for name, rows in ICONS12.items():
        out[name] = masked(rows)
    out["flourish"] = flourish()
    return out


def main():
    atlas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    drawn = sprites()
    for name, (x, y) in LAYOUT.items():
        atlas.paste(drawn[name], (x, y))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    atlas.save(OUT)
    print("wrote", OUT)
    if "--sheet" in sys.argv:
        target = sys.argv[sys.argv.index("--sheet") + 1]
        tint = [(0x4F, 0xC8, 0xFF), (0xA4, 0x6B, 0xFF), (0xFF, 0x9A, 0x3C), (0x6F, 0xE0, 0xC0)]
        sheet = Image.new("RGB", (128 * 4 * 4, 128 * 4), (18, 22, 30))
        for i, colour in enumerate(tint):
            tinted = Image.new("RGBA", atlas.size)
            src = atlas.load()
            dst = tinted.load()
            for y in range(128):
                for x in range(128):
                    r, g, b, a = src[x, y]
                    dst[x, y] = (r * colour[0] // 255, g * colour[1] // 255, b * colour[2] // 255, a)
            big = tinted.resize((512, 512), Image.NEAREST)
            sheet.paste(big, (i * 512, 0), big)
        sheet.save(target)
        print("wrote", target)


if __name__ == "__main__":
    main()
