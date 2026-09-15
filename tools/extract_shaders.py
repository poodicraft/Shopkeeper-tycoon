#!/usr/bin/env python3
"""Pulls the GLSL sources out of Renderer3D.java so they can be run through a
real compiler (glslangValidator) as part of checking a build."""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java",
                   "com", "poodicraft", "shopkeeper", "gl", "Renderer3D.java")


def extract(text, name):
    lines = text.split("\n")
    start = next(i for i, line in enumerate(lines) if ("String " + name + " =") in line)
    # The declaration runs until the line that closes the statement with ";
    end = start
    while not lines[end].rstrip().endswith('";'):
        end += 1
        if end >= len(lines):
            raise ValueError("unterminated shader constant: " + name)
    body = "\n".join(lines[start:end + 1])
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', body)
    glsl = "".join(parts)
    return glsl.replace("\\n", "\n").replace('\\"', '"')


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "build", "shaders")
    os.makedirs(out_dir, exist_ok=True)
    text = open(SRC).read()
    for name, ext in (("VERTEX_SRC", "vert"), ("FRAGMENT_SRC", "frag")):
        glsl = extract(text, name)
        path = os.path.join(out_dir, "shader." + ext)
        with open(path, "w") as f:
            # GLES 2.0 shaders have no #version line; add one so the validator
            # picks the right dialect.
            f.write("#version 100\n" + glsl)
        print("wrote", path)


if __name__ == "__main__":
    main()
