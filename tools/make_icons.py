#!/usr/bin/env python3
"""Generates the launcher icons as PNGs, with no image-library dependency.

Draws a little shop front - striped awning, door, window and a coin - by
supersampling a vector-ish description and writing the result with zlib.
"""
import math
import os
import struct
import zlib

SS = 4  # supersampling factor


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c0, c1, t):
    return tuple(lerp(c0[i], c1[i], t) for i in range(3))


def rounded_rect_sdf(x, y, cx, cy, hw, hh, r):
    """Signed distance to a rounded rectangle; negative inside."""
    dx = abs(x - cx) - (hw - r)
    dy = abs(y - cy) - (hh - r)
    ax, ay = max(dx, 0.0), max(dy, 0.0)
    return math.sqrt(ax * ax + ay * ay) + min(max(dx, dy), 0.0) - r


def shade(u, v):
    """Colour for a point in the 0..1 icon square, or None for transparent."""
    # Rounded-square plate.
    if rounded_rect_sdf(u, v, 0.5, 0.5, 0.5, 0.5, 0.235) > 0:
        return None

    # Background: warm sky fading to a deeper teal.
    bg = mix((0.16, 0.42, 0.40), (0.09, 0.24, 0.30), v)

    col = bg

    # Floor strip.
    if v > 0.80:
        col = mix((0.86, 0.80, 0.68), (0.72, 0.65, 0.53), (v - 0.80) / 0.20)

    # Shop facade.
    if 0.14 < u < 0.86 and 0.34 < v < 0.84:
        col = (0.95, 0.92, 0.85)
        # Door.
        if 0.36 < u < 0.64 and 0.50 < v < 0.84:
            col = (0.29, 0.53, 0.44)
            if 0.40 < u < 0.60 and 0.54 < v < 0.72:
                col = (0.62, 0.84, 0.86)
            if 0.585 < u < 0.615 and 0.66 < v < 0.70:
                col = (0.95, 0.80, 0.35)
        # Side windows.
        elif (0.18 < u < 0.33 or 0.67 < u < 0.82) and 0.46 < v < 0.66:
            col = (0.62, 0.84, 0.86)
            if abs(u - (0.255 if u < 0.5 else 0.745)) < 0.012:
                col = (0.95, 0.92, 0.85)
            if abs(v - 0.56) < 0.012:
                col = (0.95, 0.92, 0.85)

    # Striped awning.
    if 0.10 < u < 0.90 and 0.24 < v <= 0.36:
        t = (v - 0.24) / 0.12
        # Scalloped lower edge.
        scallop = 0.36 - 0.022 * abs(math.sin((u - 0.10) * math.pi * 8))
        if v <= scallop:
            stripe = int(((u - 0.10) / 0.10)) % 2
            base = (0.85, 0.30, 0.25) if stripe == 0 else (0.97, 0.94, 0.88)
            col = mix(base, tuple(c * 0.82 for c in base), t)

    # Sign board above the awning.
    if 0.22 < u < 0.78 and 0.14 < v < 0.24:
        col = (0.20, 0.24, 0.30)
        if 0.26 < u < 0.74 and 0.165 < v < 0.215:
            col = (0.93, 0.76, 0.32)

    # Coin, bottom right.
    d = math.hypot(u - 0.755, v - 0.755) 
    if d < 0.105:
        col = (0.95, 0.74, 0.25)
        if d < 0.082:
            col = (0.99, 0.86, 0.42)
        if abs(u - 0.755) < 0.014 and abs(v - 0.755) < 0.048:
            col = (0.72, 0.52, 0.14)

    return col


def render(size):
    pixels = bytearray()
    inv = 1.0 / (size * SS)
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r = g = b = a = 0.0
            for sy in range(SS):
                for sx in range(SS):
                    u = (px * SS + sx + 0.5) * inv
                    v = (py * SS + sy + 0.5) * inv
                    c = shade(u, v)
                    if c is not None:
                        r += c[0]; g += c[1]; b += c[2]; a += 1.0
            n = SS * SS
            if a > 0:
                r, g, b = r / a, g / a, b / a
            alpha = a / n
            row += bytes((int(r * 255 + 0.5), int(g * 255 + 0.5),
                          int(b * 255 + 0.5), int(alpha * 255 + 0.5)))
        pixels += b"\x00" + row
    return bytes(pixels)


def write_png(path, size, raw):
    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", header)
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.join(here, "..", "app", "src", "main", "res")
    targets = [("mipmap-mdpi", 48), ("mipmap-hdpi", 72), ("mipmap-xhdpi", 96),
               ("mipmap-xxhdpi", 144), ("mipmap-xxxhdpi", 192)]
    for folder, size in targets:
        out_dir = os.path.join(res, folder)
        os.makedirs(out_dir, exist_ok=True)
        raw = render(size)
        write_png(os.path.join(out_dir, "ic_launcher.png"), size, raw)
        print("wrote", folder, size)


if __name__ == "__main__":
    main()
