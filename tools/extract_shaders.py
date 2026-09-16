#!/usr/bin/env python3
"""Pulls the GLSL out of Shaders.java so a real compiler can check it.

A shader syntax error shows up on a device as a black screen with nothing in the
log that points at the line, so the test suite compiles every variant with
glslangValidator instead.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java",
                   "com", "poodicraft", "shopkeeper", "gl", "Shaders.java")

VERSION = "#version 300 es\n"

# name in Shaders.java -> (output basename, stage extension, extra #defines)
VARIANTS = [
    ("SCENE_VERTEX", "scene_static", "vert", ""),
    ("SCENE_VERTEX", "scene_skinned", "vert", "#define SKINNED 1\n"),
    ("SCENE_FRAGMENT", "scene", "frag", ""),
    ("DEPTH_VERTEX", "depth_static", "vert", ""),
    ("DEPTH_VERTEX", "depth_skinned", "vert", "#define SKINNED 1\n"),
    ("DEPTH_FRAGMENT", "depth", "frag", ""),
    ("FULLSCREEN_VERTEX", "fullscreen", "vert", ""),
    ("BRIGHT_PASS_FRAGMENT", "bright", "frag", ""),
    ("BLUR_FRAGMENT", "blur", "frag", ""),
    ("COMPOSITE_FRAGMENT", "composite", "frag", ""),
]


def extract(text, name):
    lines = text.split("\n")
    try:
        start = next(i for i, line in enumerate(lines)
                     if ("String " + name + " =") in line)
    except StopIteration:
        raise SystemExit("shader constant not found: " + name)
    end = start
    while not lines[end].rstrip().endswith('";'):
        end += 1
        if end >= len(lines):
            raise SystemExit("unterminated shader constant: " + name)
    body = "\n".join(lines[start:end + 1])
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', body)
    glsl = "".join(parts)
    return glsl.replace("\\n", "\n").replace('\\"', '"')


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "build", "shaders")
    os.makedirs(out_dir, exist_ok=True)
    text = open(SRC).read()
    written = []
    for constant, basename, stage, defines in VARIANTS:
        glsl = extract(text, constant)
        if not glsl.strip():
            raise SystemExit("extracted nothing for " + constant)
        path = os.path.join(out_dir, basename + "." + stage)
        with open(path, "w") as f:
            f.write(VERSION + defines + glsl)
        written.append(path)
    for p in written:
        print("wrote", os.path.basename(p))


if __name__ == "__main__":
    main()
