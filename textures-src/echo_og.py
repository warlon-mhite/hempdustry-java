#!/usr/bin/env python3
"""Draws Echo OG from Purple Kush: the same plant, buds, seeds and spliff, recoloured to the deep dark's sculk.

    python3 textures-src/echo_og.py common/src/main/resources/assets/hempdustry/textures
    python3 textures-src/mildew.py common/src/main/resources/assets/hempdustry

Echo OG is Purple Kush's body (two tall), so its art is Purple Kush's pixels with colour moves and nothing
redrawn. Every colour is vanilla sculk's own (sculk, sculk_vein, the sensor's tendrils, the catalyst's bloom):

* the green leaves turn sculk's navy and deep teal (#0D1217, #111B21, #052A32, #034150), each pixel keeping
  its place in Purple Kush's light-to-dark ramp, with sculk's lighter teal spots (#05625D, #009295) scattered
  over them by a hash of the pixel, as they are on a sculk block;
* the violet bud bodies turn the dark teal of a sensor's tendrils at rest (#086E68, #009295);
* the orange hairs turn the cyan a sensor's tendrils glow when they hear something (#1AB4BE, #29DFEB,
  #16DEEC): the pistils that glow on a ripe plant.

The plant is drawn untinted, its colour baked in as sculk's is.

A ripe plant's pistils glow: each ripe stage also gets `<stage>_glow.png`, its cyan pixels alone, drawn by
`block/crop_glow` (a second cross a hair in front of the plant, at full light) so they show in the dark
whatever the light level, as a sculk sensor's tendrils do.

Stressed (by light, from age 3), the plant fades to a dead grey-blue, sculk drained of its colour, and its
pistils go out: stressed art has no glow.

Re-run after any change to Purple Kush's art; never edit the output by hand.
"""
import colorsys
import hashlib
import os
import sys

from PIL import Image

STAGES = ["indica_crop_stage%d" % i for i in range(12)]
# Stressed from age 3, as a light-weak plant is: Purple Kush's stressed art starts at 3 too.
STRESSED = ["indica_crop_stage%d_stressed" % i for i in range(3, 12)]
# The ripe plant's two halves: its root at age 7, its tip at age 7.
RIPE = ("indica_crop_stage7", "indica_crop_stage11")

NAVY = [(0x0D, 0x12, 0x17), (0x11, 0x1B, 0x21), (0x05, 0x2A, 0x32), (0x03, 0x41, 0x50), (0x07, 0x48, 0x57)]
SPOTS = [(0x05, 0x62, 0x5D), (0x00, 0x92, 0x95)]
TENDRIL = [(0x05, 0x2A, 0x32), (0x06, 0x5A, 0x55), (0x08, 0x6E, 0x68), (0x00, 0x92, 0x95), (0x1A, 0xB4, 0xBE)]
CYAN = [(0x1A, 0xB4, 0xBE), (0x29, 0xDF, 0xEB), (0x16, 0xDE, 0xEC)]
# The hairs, dark to bright: their shaded side the tendrils' teal, only the lit tips cyan, so a ripe cola is
# a teal head with points of light, never a solid glow.
HAIR = [(0x08, 0x6E, 0x68), (0x00, 0x92, 0x95), (0x1A, 0xB4, 0xBE), (0x29, 0xDF, 0xEB), (0x16, 0xDE, 0xEC)]
# Drained sculk for the stressed plant: sculk's own greys (the shrieker's and the catalyst's #40576C,
# #3E7B8A) faded.
DEAD = [(0x2A, 0x33, 0x3A), (0x3A, 0x46, 0x4E), (0x4A, 0x57, 0x60), (0x5A, 0x69, 0x72), (0x6B, 0x7B, 0x84)]
DEAD_PISTIL = (0x6E, 0x86, 0x8C)
SEED = [(0x05, 0x2A, 0x32), (0x03, 0x41, 0x50), (0x08, 0x6E, 0x68), (0x00, 0x92, 0x95)]
SPOT_ODDS = 0.06


def _hsv(rgb):
    return colorsys.rgb_to_hsv(*(c / 255 for c in rgb))


def _ramp(ramp, t):
    """The colour a share {@code t} (0 dark .. 1 light) of the way along a list of colours."""
    t = max(0.0, min(1.0, t))
    return ramp[min(len(ramp) - 1, int(t * len(ramp)))]


def _roll(*key):
    return int.from_bytes(hashlib.sha1("/".join(map(str, key)).encode()).digest()[:4], "big") / 2 ** 32


def kind(rgb):
    """What a Purple Kush pixel is: an outline, a bud, a hair, a leaf, or anything else."""
    h, s, v = _hsv(rgb)
    deg = h * 360
    if v < 0.14:
        return "outline"
    if s > 0.12 and (deg >= 250 or deg < 8):
        return "bud"
    if s > 0.35 and 8 <= deg < 62:
        return "hair"
    if s > 0.10 and 62 <= deg < 210:
        return "leaf"
    return "other"


def plant(rgb, key, stressed=False, hair_from=0.55):
    h, s, v = _hsv(rgb)
    what = kind(rgb)
    if what == "outline":
        return rgb  # outlines stay as drawn
    if stressed:
        if what == "hair":
            return DEAD_PISTIL
        return _ramp(DEAD, (v - 0.25) * 2.0)
    if what == "bud":
        return _ramp(TENDRIL[:4], (v - 0.20) * 1.5)
    if what == "hair":
        return _ramp(HAIR, (v - hair_from) * 2.4)
    if what == "leaf":
        if _roll(key) < SPOT_ODDS:
            return SPOTS[0] if v < 0.45 else SPOTS[1]
        # Purple Kush's leaf ramp runs v 0.30 .. 0.60.
        return _ramp(NAVY, (v - 0.28) * 3.2)
    if s <= 0.12 and v > 0.45:
        return _ramp(DEAD, (v - 0.25) * 2.0)
    return rgb


def seed(rgb, key):
    """Purple Kush's seeds turned sculk's dark teal: still a seed, never a glow."""
    h, s, v = _hsv(rgb)
    if v < 0.14:
        return rgb
    return _ramp(SEED, (v - 0.25) * 1.8)


def ember(rgb, key):
    """A spliff: its lit tip sculk's cyan, as each strain's tip wears its own colour, and its paper -- which every
    strain tints its own way -- a pale grey-teal, so it still reads as paper."""
    h, s, v = _hsv(rgb)
    deg = h * 360
    if s > 0.35 and (deg >= 300 or deg < 60):
        return _ramp(CYAN, (v - 0.45) * 2.2)
    if s <= 0.35 and v >= 0.14:
        r, g, b = colorsys.hsv_to_rgb(188 / 360, 0.16, min(1.0, v * 0.95 + 0.04))
        return round(r * 255), round(g * 255), round(b * 255)
    return rgb


def recolour(src, dst, how):
    image = Image.open(src).convert("RGBA")
    px = image.load()
    name = os.path.basename(dst)
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if a:
                px[x, y] = how((r, g, b), (name.split("_stressed")[0], x, y)) + (a,)
    image.save(dst)


def glow(src, dst):
    """The ripe stage's lit pistils alone (its cyan pixels), at full alpha: what the glow cross draws."""
    image = Image.open(src).convert("RGBA")
    px = image.load()
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    dst_px = out.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            lit = plant((r, g, b), None) if a and kind((r, g, b)) == "hair" else None
            if lit in CYAN:
                dst_px[x, y] = lit + (255,)
    out.save(dst)


def main(textures):
    block = os.path.join(textures, "block")
    item = os.path.join(textures, "item")
    rename = lambda name: name.replace("indica_crop", "echo_og_crop")
    for name in STAGES:
        recolour(os.path.join(block, name + ".png"), os.path.join(block, rename(name) + ".png"), plant)
    for name in STRESSED:
        recolour(os.path.join(block, name + ".png"), os.path.join(block, rename(name) + ".png"),
                 lambda rgb, key: plant(rgb, key, stressed=True))
    for name in RIPE:
        glow(os.path.join(block, name + ".png"), os.path.join(block, rename(name) + "_glow.png"))
    # The bud item's hairs are Purple Kush's darker rust, so its ramp starts lower: a few cyan points on it.
    recolour(os.path.join(item, "indica_buds.png"), os.path.join(item, "echo_og_buds.png"),
             lambda rgb, key: plant(rgb, key, hair_from=0.38))
    recolour(os.path.join(item, "indica_seeds.png"), os.path.join(item, "echo_og_seeds.png"), seed)
    recolour(os.path.join(item, "indica_spliff.png"), os.path.join(item, "echo_og_spliff.png"), ember)


if __name__ == "__main__":
    main(sys.argv[1])
