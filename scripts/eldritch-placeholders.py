"""Placeholder creatures for the eldritch kit: cubes in the school teal with bright fronts and a glow
layer that is transparent except where they glow, one file per asset in the contract, so the code can
be seen moving before the real models exist.
Run from the project root: python scripts/eldritch-placeholders.py"""
import json, struct, zlib, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "magical")
MODELS = os.path.join(ROOT, "models", "entity", "eldritch")
TEXTURES = os.path.join(ROOT, "textures", "entity", "eldritch")
INK, TEAL, BRIGHT, DARK = (6, 50, 42), (47, 191, 158), (95, 239, 208), (26, 122, 102)
# The glow layer is blended over the body, not added, so it is clear wherever the creature does not glow.
CLEAR = (0, 0, 0, 0)


def rgba(color):
    return color if len(color) == 4 else color + (255,)


def png(path, size, pixels):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(size)) for y in range(size))
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    with open(path, "wb") as out:
        out.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
                  + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def canvas(size, rgb):
    return [[rgba(rgb) for _ in range(size)] for _ in range(size)]


def box_uv(pixels, u, v, w, h, d, body, front, edge):
    """Paint one Box UV layout: top and bottom (d tall) then the four sides (h tall), the front face lit."""
    def fill(x0, y0, x1, y1, rgb):
        for y in range(y0, y1):
            for x in range(x0, x1):
                pixels[y][x] = rgba(rgb)
    fill(u + d, v, u + d + 2 * w, v + d, body)
    fill(u, v + d, u + 2 * d + 2 * w, v + d + h, body)
    fill(u + d, v + d, u + d + w, v + d + h, front)
    for x in (u, u + d, u + d + w, u + 2 * d + w, u + 2 * d + 2 * w - 1):
        fill(x, v + d, x + 1, v + d + h, edge)
    fill(u + d, v, u + d + 2 * w, v + 1, edge)


def write(name, size, bones, paint):
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry." + name, "texture_width": size, "texture_height": size,
        "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]}, "bones": bones}]}
    with open(os.path.join(MODELS, name + ".geo.json"), "w", encoding="utf-8", newline="\n") as out:
        json.dump(geo, out, indent=2)
        out.write("\n")
    body, glow = canvas(size, INK), canvas(size, CLEAR)
    paint(body, glow)
    png(os.path.join(TEXTURES, name + ".png"), size, body)
    png(os.path.join(TEXTURES, name + "_glow.png"), size, glow)


def tentacle():
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    for i in range(6):
        half = 2.0 - i * 0.25
        bones.append({"name": "seg%d" % i, "parent": "root" if i == 0 else "seg%d" % (i - 1), "pivot": [0, i * 4, 0],
                      "cubes": [{"origin": [-half, i * 4, -half], "size": [2 * half, 4, 2 * half], "uv": [0, i * 8]}]})
    def paint(body, glow):
        for i in range(6):
            box_uv(body, 0, i * 8, 4, 4, 4, TEAL, BRIGHT, DARK)
            box_uv(glow, 0, i * 8, 4, 4, 4, CLEAR, BRIGHT if i >= 3 else CLEAR, CLEAR)
    write("tentacle", 64, bones, paint)


def eye():
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "body", "parent": "root", "pivot": [0, 8, 0], "cubes": [{"origin": [-6, 2, -6], "size": [12, 12, 12], "uv": [0, 0]}]},
             {"name": "pupil", "parent": "body", "pivot": [0, 8, -6], "cubes": [{"origin": [-2, 6, -7], "size": [4, 4, 1], "uv": [0, 24]}]},
             {"name": "lid_upper", "parent": "body", "pivot": [0, 14, -6], "rotation": [-100, 0, 0], "cubes": [{"origin": [-6, 8, -7], "size": [12, 6, 1], "uv": [0, 30]}]},
             {"name": "lid_lower", "parent": "body", "pivot": [0, 2, -6], "rotation": [100, 0, 0], "cubes": [{"origin": [-6, 2, -7], "size": [12, 6, 1], "uv": [0, 38]}]}]
    def paint(body, glow):
        box_uv(body, 0, 0, 12, 12, 12, TEAL, BRIGHT, DARK)
        box_uv(body, 0, 24, 4, 4, 1, INK, INK, INK)
        box_uv(body, 0, 30, 12, 6, 1, TEAL, TEAL, DARK)
        box_uv(body, 0, 38, 12, 6, 1, TEAL, TEAL, DARK)
        box_uv(glow, 0, 0, 12, 12, 12, CLEAR, TEAL, CLEAR)
    write("eye", 64, bones, paint)


def maw():
    bones = [{"name": "root", "pivot": [0, 0, 0]},
             {"name": "jaw_lower", "parent": "root", "pivot": [0, 3, 6],
              "cubes": [{"origin": [-10, 0, -18], "size": [20, 3, 24], "uv": [0, 0]},
                        {"origin": [-8, 3, -16], "size": [2, 3, 2], "uv": [0, 60]}, {"origin": [6, 3, -16], "size": [2, 3, 2], "uv": [0, 60]}]},
             {"name": "jaw_upper", "parent": "root", "pivot": [0, 3, 6],
              "cubes": [{"origin": [-10, 3, -18], "size": [20, 3, 24], "uv": [0, 28]},
                        {"origin": [-2, 0, -16], "size": [4, 3, 2], "uv": [0, 60]}]}]
    def paint(body, glow):
        box_uv(body, 0, 0, 20, 3, 24, TEAL, BRIGHT, DARK)
        box_uv(body, 0, 28, 20, 3, 24, TEAL, BRIGHT, DARK)
        box_uv(body, 0, 60, 4, 3, 2, BRIGHT, BRIGHT, INK)
        box_uv(glow, 0, 60, 4, 3, 2, BRIGHT, BRIGHT, CLEAR)
    write("maw", 128, bones, paint)


os.makedirs(MODELS, exist_ok=True)
os.makedirs(TEXTURES, exist_ok=True)
tentacle()
eye()
maw()
print("wrote placeholders to", MODELS, "and", TEXTURES)
