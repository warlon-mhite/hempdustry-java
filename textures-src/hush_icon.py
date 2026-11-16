#!/usr/bin/env python3
"""Draws the Hush status effect's icon: a sculk sensor's two tendrils, curled and quiet.

    python3 textures-src/hush_icon.py common/src/main/resources/assets/hempdustry/textures

18 x 18, as vanilla's effect icons are, outlined like Averted Gaze's. The colours are vanilla sculk's own:
the tendrils' dark teal at rest, their cyan only at the very tips, the sensor's navy body under them.
"""
import os
import sys

from PIL import Image

ART = [
    "..................",
    "..................",
    "...oo........oo...",
    "..occo......occo..",
    "..oclo......olco..",
    "...otto....otto...",
    "....otto..otto....",
    ".....otooooto.....",
    ".....ottttttto....",
    "....oNNNNNNNNNo...",
    "...oNnnnnnnnnnNo..",
    "...oNnnnnnnnnnNo..",
    "...oNnnnnnnnnnNo..",
    "...oNnnnnnnnnnNo..",
    "....oNNNNNNNNNo...",
    ".....ooooooooo....",
    "..................",
    "..................",
]
COLOURS = {
    "o": (0x05, 0x14, 0x1A, 255),   # outline, darker than sculk's darkest
    "t": (0x08, 0x6E, 0x68, 255),   # tendril at rest
    "c": (0x1A, 0xB4, 0xBE, 255),   # tendril tip, dimmed cyan
    "l": (0x29, 0xDF, 0xEB, 255),   # the tip's one lit pixel
    "N": (0x03, 0x41, 0x50, 255),   # sensor body, deep teal
    "n": (0x11, 0x1B, 0x21, 255),   # sensor top, navy
}


def main(textures):
    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    px = image.load()
    for y, row in enumerate(ART):
        assert len(row) == 18, (y, len(row))
        for x, ch in enumerate(row):
            if ch in COLOURS:
                px[x, y] = COLOURS[ch]
    out = os.path.join(textures, "mob_effect")
    os.makedirs(out, exist_ok=True)
    image.save(os.path.join(out, "hush.png"))


if __name__ == "__main__":
    main(sys.argv[1])
