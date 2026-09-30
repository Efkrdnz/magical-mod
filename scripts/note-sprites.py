"""Draws the music-note particle sprites for the Authority of Sound.

    python scripts/note-sprites.py

Pale greyscale pixel art, tinted per particle in game like the rest of the mod's sprites: the body is
white so the tint is its colour, and the rim round it is a mid grey so a tinted note keeps a darker
edge of its own hue and still reads over daylight sand. Sixteen pixels square, the size a sigil is,
because a note head and its stem do not survive being drawn in eight.

Writes textures/particle/note_<n>.png and particles/note.json. The Riff renderer draws the same files
on its flying notes, so the glyph a note is shot as and the glyph it sheds are one drawing.
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical")
TEXTURES = os.path.join(ROOT, "textures", "particle")
DEFINITIONS = os.path.join(ROOT, "particles")

BODY = 255
RIM = 118
RIM_ALPHA = 235

# '#' is the note, anything else is empty; the rim is added round every stroke.
NOTES = {
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
    "eighth": [
        "................",
        ".........##.....",
        ".........###....",
        ".........####...",
        ".........##.##..",
        ".........##..##.",
        ".........##...#.",
        ".........##...#.",
        ".........##..#..",
        ".........##.....",
        ".....######.....",
        "...########.....",
        "..#########.....",
        "..########......",
        "...######.......",
        "................",
    ],
    "beamed": [
        "................",
        ".....#########..",
        ".....#########..",
        ".....##.....##..",
        ".....##.....##..",
        ".....##.....##..",
        ".....##.....##..",
        ".....##.....##..",
        ".....##.....##..",
        ".....##.....##..",
        "..#####..#####..",
        ".######.######..",
        ".#####..#####...",
        "..###....###....",
        "................",
        "................",
    ],
    "sixteenth": [
        "................",
        ".........###....",
        ".........####...",
        ".........##.##..",
        ".........###.##.",
        ".........####.#.",
        ".........##.##..",
        ".........##..##.",
        ".........##...#.",
        ".........##.....",
        ".....######.....",
        "...########.....",
        "..#########.....",
        "..########......",
        "...######.......",
        "................",
    ],
    "half": [
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
        "...###....#.....",
        "..##....###.....",
        "..#######.......",
        "...####.........",
        "................",
    ],
    "sharp": [
        "................",
        "................",
        ".....##..##.....",
        ".....##..##.....",
        "..###########...",
        "..###########...",
        ".....##..##.....",
        ".....##..##.....",
        ".....##..##.....",
        "...###########..",
        "...###########..",
        ".....##..##.....",
        ".....##..##.....",
        "................",
        "................",
        "................",
    ],
}

ORDER = ["quarter", "eighth", "beamed", "sixteenth", "half", "sharp"]


def draw(rows):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    size = len(rows)
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            if char == "#":
                image.putpixel((x, y), (BODY, BODY, BODY, 255))
    for y in range(size):
        for x in range(size):
            if rows[y][x] == "#":
                continue
            touches = any(
                0 <= x + dx < size and 0 <= y + dy < size and rows[y + dy][x + dx] == "#"
                for dx in (-1, 0, 1) for dy in (-1, 0, 1)
            )
            if touches:
                image.putpixel((x, y), (RIM, RIM, RIM, RIM_ALPHA))
    return image


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    names = []
    for index, name in enumerate(ORDER):
        rows = NOTES[name]
        assert len(rows) == 16 and all(len(row) == 16 for row in rows), name
        path = os.path.join(TEXTURES, f"note_{index}.png")
        draw(rows).save(path)
        names.append(f"magical:note_{index}")
        print("wrote", path)
    with open(os.path.join(DEFINITIONS, "note.json"), "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"textures": names}, handle, indent=2)
        handle.write("\n")


if __name__ == "__main__":
    main()
