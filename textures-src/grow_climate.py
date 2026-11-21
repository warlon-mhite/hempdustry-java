"""PLACEHOLDER art for the grow climate's blocks: the Grow Fan (and, below it, what the later blocks need).

    python3 textures-src/grow_climate.py common/src/main/resources/assets/hempdustry

Writes textures/block/grow_fan_*.png (and the animation's .mcmeta). The colours are the mod's own: the
Grow Lamp's copper ramp (textures-src/grow_lamp.mctex) for every housing, the Hydro Tray's greys and its
airline's light metal (textures-src/hydro_tray.mctex) for the blades and the grille. Nothing new.

The fan is a box fan seen through its front: a copper ring round a dark shroud, four light-metal blades on
a copper hub. The blades are drawn, not painted: each pixel of the 10 x 10 inside asks its angle and radius
from the hub, so turning the fan is turning the angle. Running, the strip is four frames a quarter of a
blade's turn apart, one tick each: four blades meet themselves after a quarter turn, so the loop is seamless,
and a faint trail behind each blade reads as speed. Re-running writes the same bytes.
"""
import math
import os
import sys

from PIL import Image

# The Grow Lamp's copper, deep shadow to highlight.
COPPER = ['6D3421', '8A4129', 'A4553A', 'C0684C', 'D8845E', 'E9A07A']
# The Hydro Tray's plastic greys, darkest first, and its airline's light metal and catch-light.
GREY = ['17171A', '242428', '2D2D32', '343438', '3F3E42', '494848', '555556', '5E5E60']
METAL = 'A9B4BC'
SHINE = 'D2DBE0'


def rgb(hex_colour):
    return tuple(int(hex_colour[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def casing():
    """A copper sheet: lit from the top left, a folded seam across the middle, a rivet in each corner."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = COPPER[2]
            if x == 0 or y == 0:
                c = COPPER[4]
            elif x == 15 or y == 15:
                c = COPPER[1]
            elif y == 7:
                c = COPPER[1]
            elif y == 8:
                c = COPPER[3]
            img.putpixel((x, y), rgb(c))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), rgb(COPPER[5]))
        img.putpixel((x + 1, y + 1) if x < 8 else (x - 1, y + 1), rgb(COPPER[0]))
    return img


def ring(img):
    """The front's copper frame round the 12 x 12 face (u 2..13): the rest of the 16 x 16 is never shown."""
    for y in range(16):
        for x in range(16):
            if not (2 <= x <= 13 and 2 <= y <= 13):
                img.putpixel((x, y), rgb(COPPER[2]))
            elif y == 2 or x == 2:
                img.putpixel((x, y), rgb(COPPER[4] if (x, y) != (13, 2) else COPPER[3]))
            elif y == 13 or x == 13:
                img.putpixel((x, y), rgb(COPPER[1]))


def angle_to(a, b):
    """The angle from b to a, in (-pi, pi]."""
    d = (a - b) % (2 * math.pi)
    return d - 2 * math.pi if d > math.pi else d


def blades(turn, trail):
    """The fan's face with its blades turned by {turn} radians; {trail} draws the motion behind each blade."""
    img = Image.new('RGBA', (16, 16))
    ring(img)
    for py in range(10):
        for px in range(10):
            dx, dy = px + 0.5 - 5.0, py + 0.5 - 5.0
            r = math.hypot(dx, dy)
            theta = math.atan2(dy, dx)
            c = GREY[1]
            if r > 5.2:
                c = GREY[0]
            elif r < 1.3:
                c = COPPER[4] if dx + dy < 0 else COPPER[2]
            else:
                width = 0.30 + 0.22 * (r - 1.3) / 3.9
                for k in range(4):
                    off = angle_to(theta, turn + k * math.pi / 2)
                    if abs(off) <= width:
                        c = SHINE if off < -width / 3 else METAL if off < width / 2 else GREY[6]
                        break
                    if trail and width < off <= width + 0.45:
                        c = GREY[4] if off <= width + 0.22 else GREY[3]
                        break
            img.putpixel((px + 3, py + 3), rgb(c))
    return img


def back():
    """The fan's back: a grille of bars over the motor, its copper can in the middle."""
    img = Image.new('RGBA', (16, 16))
    ring(img)
    for py in range(10):
        for px in range(10):
            dx, dy = px + 0.5 - 5.0, py + 0.5 - 5.0
            r = math.hypot(dx, dy)
            if r < 2.2:
                c = COPPER[3] if dx + dy < 0 else COPPER[1]
            elif py % 2 == 0:
                c = GREY[5]
            else:
                c = GREY[1]
            img.putpixel((px + 3, py + 3), rgb(c))
    return img


def main(assets):
    out = os.path.join(assets, 'textures', 'block')
    casing().save(os.path.join(out, 'grow_fan_casing.png'))
    blades(0.0, False).save(os.path.join(out, 'grow_fan_front.png'))
    back().save(os.path.join(out, 'grow_fan_back.png'))
    frames = 4
    strip = Image.new('RGBA', (16, 16 * frames))
    for f in range(frames):
        strip.paste(blades(-f * (math.pi / 2) / frames, True), (0, 16 * f))
    strip.save(os.path.join(out, 'grow_fan_front_on.png'))
    with open(os.path.join(out, 'grow_fan_front_on.png.mcmeta'), 'w') as meta:
        meta.write('{\n  "animation": {\n    "frametime": 1\n  }\n}\n')


if __name__ == '__main__':
    main(sys.argv[1])
