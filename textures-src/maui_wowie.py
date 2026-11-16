#!/usr/bin/env python3
"""Draws Maui Wowie from Lemon Haze: the same plant, buds, seeds and spliff, recoloured to the warm sea.

    python3 textures-src/maui_wowie.py common/src/main/resources/assets/hempdustry/textures
    python3 textures-src/mildew.py common/src/main/resources/assets/hempdustry

Maui Wowie is Lemon Haze's body (three tall), so its art is Lemon Haze's pixels with colour moves and nothing
redrawn. Every colour is one of vanilla's ocean blocks':

* the green fans turn prismarine's pale aqua-green (#41816A .. #95CFC7), each pixel keeping its place in Lemon
  Haze's light-to-dark ramp;
* the orange hairs turn coral: fire coral's reds (#791A26, #C62A37, #E23F36) with Lemon Haze's own lit orange
  (#EE9829) at the tips, so a ripe cola reads warm against the pale fans;
* the pale trichome pixels turn prismarine bricks' lightest (#95CFC7) and white stays white.

The plant is drawn untinted, its colour baked in, as the land's grass colour means nothing under the sea.

Stressed (by light, as every strain), the plant bleaches the way a reef does: dead coral's greys
(#746A66 .. #A4A09C), its hairs gone grey with it.

The wild flower is a ripe top of the plant on one tile (Lemon Haze's ripe top, recoloured), as Wild Cherry
Wine wears its crop's art. The seeds and the bud's shade take dark prismarine; the spliff's tip the coral, its
paper a pale sea-grey.

Re-run after any change to Lemon Haze's art; never edit the output by hand.
"""
import colorsys
import os
import sys

from PIL import Image

STAGES = (["sativa_crop_stage%d" % i for i in range(4)]
          + ["sativa_crop_stage%d_%s" % (i, part) for i in (4, 5) for part in ("bottom", "top")]
          + ["sativa_crop_stage%d_%s" % (i, part) for i in (6, 7) for part in ("bottom", "middle", "top")])
# Lemon Haze's stressed art starts at stage 3, as every plant's light stress may.
STRESSED = [name + "_stressed" for name in STAGES if not name.startswith(("sativa_crop_stage0", "sativa_crop_stage1",
                                                                           "sativa_crop_stage2"))]

PRISMARINE = [(0x41, 0x81, 0x6A), (0x46, 0x89, 0x74), (0x52, 0x99, 0x83), (0x5E, 0xA4, 0x96), (0x6E, 0xB9, 0xAE),
              (0x79, 0xB7, 0xAB), (0x82, 0xC5, 0xBC), (0x95, 0xCF, 0xC7)]
CORAL = [(0x79, 0x1A, 0x26), (0xC6, 0x2A, 0x37), (0xE2, 0x3F, 0x36), (0xEE, 0x98, 0x29)]
FROST = (0x95, 0xCF, 0xC7)
DEAD = [(0x74, 0x6A, 0x66), (0x7B, 0x72, 0x6E), (0x86, 0x7B, 0x77), (0x8A, 0x80, 0x7B), (0x99, 0x93, 0x8D),
        (0xA4, 0xA0, 0x9C)]
DEAD_HAIR = (0x86, 0x7B, 0x77)
DARK_PRISMARINE = [(0x27, 0x3E, 0x36), (0x31, 0x50, 0x41), (0x34, 0x56, 0x48), (0x38, 0x62, 0x51), (0x3F, 0x6D, 0x5C),
                   (0x3B, 0x82, 0x68)]
PAPER_HUE = 170 / 360


def _hsv(rgb):
    return colorsys.rgb_to_hsv(*(c / 255 for c in rgb))


def _ramp(ramp, t):
    """The colour a share {@code t} (0 dark .. 1 light) of the way along a list of colours."""
    t = max(0.0, min(1.0, t))
    return ramp[min(len(ramp) - 1, int(t * len(ramp)))]


def kind(rgb):
    """What a Lemon Haze pixel is: an outline, a hair, a frost pixel, a leaf, or anything else."""
    h, s, v = _hsv(rgb)
    deg = h * 360
    if v < 0.14:
        return "outline"
    if rgb == (0xFF, 0xFF, 0xFF):
        return "white"
    if s > 0.45 and (deg < 62 or deg >= 330):
        return "hair"
    if s < 0.30 and v > 0.85:
        return "frost"
    if s > 0.08 and 62 <= deg < 200:
        return "leaf"
    return "other"


def plant(rgb, stressed=False):
    h, s, v = _hsv(rgb)
    what = kind(rgb)
    if what in ("outline", "white"):
        return rgb
    if stressed:
        if what == "hair":
            return DEAD_HAIR
        if what == "frost":
            return DEAD[-1]
        # Lemon Haze's stressed ramp runs yellow-green, v 0.50 .. 0.80.
        return _ramp(DEAD, (v - 0.45) * 2.6)
    if what == "hair":
        return _ramp(CORAL, (v - 0.70) * 3.4)
    if what == "frost":
        return FROST
    if what == "leaf":
        # Lemon Haze's leaf ramp runs v 0.35 .. 0.75, most of it 0.55 .. 0.73.
        return _ramp(PRISMARINE, (v - 0.50) * 3.2)
    return rgb


def bud(rgb):
    """The bud item: its greens to prismarine (a little darker, as a dried bud is), its rusty hairs to coral."""
    h, s, v = _hsv(rgb)
    deg = h * 360
    if v < 0.14:
        return rgb
    if s > 0.25 and (deg < 40 or deg >= 330):
        return _ramp(CORAL[:3], (v - 0.30) * 2.4)
    if s > 0.10 and 62 <= deg < 200:
        return _ramp(DARK_PRISMARINE[2:] + PRISMARINE[:4], (v - 0.22) * 2.2)
    return rgb


def seed(rgb):
    h, s, v = _hsv(rgb)
    if v < 0.10:
        return rgb
    return _ramp(DARK_PRISMARINE, (v - 0.15) * 2.0)


def spliff(rgb):
    """A spliff: its lit tip coral, as each strain's tip wears its own colour, and its paper a pale sea-grey."""
    h, s, v = _hsv(rgb)
    deg = h * 360
    if s > 0.45 and (deg >= 300 or deg < 60):
        return _ramp(CORAL[1:], (v - 0.75) * 4.0)
    if s <= 0.45 and v >= 0.14:
        r, g, b = colorsys.hsv_to_rgb(PAPER_HUE, 0.12, min(1.0, v * 0.95 + 0.04))
        return round(r * 255), round(g * 255), round(b * 255)
    return rgb


def recolour(src, dst, how):
    image = Image.open(src).convert("RGBA")
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = how((r, g, b)) + (a,)
    image.save(dst)


def main(textures):
    block = os.path.join(textures, "block")
    item = os.path.join(textures, "item")
    rename = lambda name: name.replace("sativa_crop", "maui_wowie_crop")
    for name in STAGES:
        recolour(os.path.join(block, name + ".png"), os.path.join(block, rename(name) + ".png"), plant)
    for name in STRESSED:
        recolour(os.path.join(block, name + ".png"), os.path.join(block, rename(name) + ".png"),
                 lambda rgb: plant(rgb, stressed=True))
    recolour(os.path.join(block, "sativa_crop_stage7_top.png"), os.path.join(block, "maui_wowie_flower.png"), plant)
    recolour(os.path.join(item, "sativa_buds.png"), os.path.join(item, "maui_wowie_buds.png"), bud)
    recolour(os.path.join(item, "sativa_seeds.png"), os.path.join(item, "maui_wowie_seeds.png"), seed)
    recolour(os.path.join(item, "sativa_spliff.png"), os.path.join(item, "maui_wowie_spliff.png"), spliff)


if __name__ == "__main__":
    main(sys.argv[1])
