#!/usr/bin/env python3
"""Assemble docs/avatars/samples into one contact sheet for review.

A pull request that changes the renderer has to show what it changed, and 60
individual images are not reviewable. This lays the `ours` and `ref-` pairs out
side by side, one column pair per seed, so a regression reads as a visible
difference between neighbours instead of a number in a test log.

The images themselves come from `DiceBearSampleExportTest` (ours) and the
DiceBear HTTP API (reference); see docs/avatars/samples/README.md. This script
only composes what is already committed.

Requires Pillow. Usage:

    python3 tools/dicebear_contact_sheet.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

SAMPLES = Path(__file__).resolve().parent.parent / "docs" / "avatars" / "samples"

STYLES = [
    "identicon",
    "stripes",
    "triangles",
    "weave",
    "rings",
    "shape-grid",
    "line-face",
    "shadows",
    "shapes",
    "lorelei-neutral",
]

# The seed as committed in the filename, and how to spell it in a 108px label.
SEEDS = [("Ada", "Ada"), ("+33612345678", "+33"), ("🤖", "robot")]

CELL = 108
GAP = 4
LABEL_HEIGHT = 14
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_SIZE = 10


def label_font() -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype(FONT_PATH, FONT_SIZE)
    except OSError:
        return ImageFont.load_default()


def main() -> None:
    font = label_font()
    columns = len(SEEDS) * 2
    rows = len(STYLES)
    width = columns * (CELL + GAP) + GAP
    height = rows * (CELL + LABEL_HEIGHT + GAP) + GAP

    sheet = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(sheet)

    for row, style in enumerate(STYLES):
        for column, (seed, short) in enumerate(SEEDS):
            for offset, prefix in enumerate(("", "ref-")):
                path = SAMPLES / f"{prefix}{style}-{seed}.png"
                slot = column * 2 + offset
                x = GAP + slot * (CELL + GAP)
                y = GAP + row * (CELL + LABEL_HEIGHT + GAP)
                with Image.open(path) as image:
                    # Composite over white rather than dropping alpha: several styles are
                    # transparent and would otherwise print as black cells.
                    tile = Image.new("RGBA", (CELL, CELL), "white")
                    tile.alpha_composite(image.convert("RGBA").resize((CELL, CELL)))
                    sheet.paste(tile.convert("RGB"), (x, y))
                who = "ours" if prefix == "" else "ref"
                draw.text((x, y + CELL + 1), f"{style} {short} {who}", fill="black", font=font)

    out = SAMPLES / "contact-sheet.png"
    # A palette keeps the sheet around 85 KB instead of 1.6 MB: the artwork is flat.
    sheet.convert("P", palette=Image.ADAPTIVE, colors=128).save(out, optimize=True)
    print(f"wrote {out} ({out.stat().st_size} bytes, {width}x{height})")


if __name__ == "__main__":
    main()
