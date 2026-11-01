#!/usr/bin/env python3
"""Draws preview.png (Workshop, 256 px), poster.png (mod list, 512 px) and icon.png (64 px).

Same look as the GamepadTweaks / SplitscreenFixes previews: dark background, ivory and red.
Needs Pillow and Noto Sans Bold (pacman -S python-pillow noto-fonts).
Usage: tools/make_images.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
MOD = ROOT / "Contents/mods/AutoZoomTweaks/42"
FONT = "/usr/share/fonts/noto/NotoSans-Bold.ttf"
BG = (24, 30, 24)
IVORY = (215, 210, 195)
RED = (196, 40, 32)
S = 1024  # drawn at 2x of the poster, scaled down


def frame(d, cx, cy, w, h, corner, width, color):
    """Viewfinder corners of a w x h box centred at cx, cy."""
    x0, y0, x1, y1 = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    for x, y, sx, sy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        d.line([(x, y + sy * corner), (x, y), (x + sx * corner, y)], fill=color, width=width, joint="curve")


def arrow(d, a, b, width, head, color):
    (ax, ay), (bx, by) = a, b
    d.line([a, b], fill=color, width=width)
    vx, vy = bx - ax, by - ay
    n = (vx * vx + vy * vy) ** 0.5
    ux, uy = vx / n, vy / n
    px, py = -uy, ux
    d.polygon([(bx + ux * head * 0.4, by + uy * head * 0.4),
               (bx - ux * head + px * head * 0.7, by - uy * head + py * head * 0.7),
               (bx - ux * head - px * head * 0.7, by - uy * head - py * head * 0.7)], fill=color)


def draw(with_text):
    im = Image.new("RGB", (S, S), BG)
    d = ImageDraw.Draw(im)
    cy = S * (0.36 if with_text else 0.5)
    # The icon (64 px) has no arrows and thicker lines: thin ones blur away.
    scale = 1.0 if with_text else 1.4
    thick = 1.0 if with_text else 1.8
    cx = S / 2
    # Far view (ivory corners), close view (red box), arrows from the close to the far one.
    fw, fh = 620 * scale, 420 * scale
    nw, nh = 230 * scale, 156 * scale
    frame(d, cx, cy, fw, fh, 110 * scale, int(30 * scale * thick), IVORY)
    d.rounded_rectangle([cx - nw / 2, cy - nh / 2, cx + nw / 2, cy + nh / 2], radius=14 * scale,
                        outline=RED, width=int(26 * scale * thick))
    for sx in (-1, 1) if with_text else ():
        for sy in (-1, 1):
            a = (cx + sx * (nw / 2 + 34 * scale), cy + sy * (nh / 2 + 24 * scale))
            b = (cx + sx * (fw / 2 - 70 * scale), cy + sy * (fh / 2 - 48 * scale))
            arrow(d, a, b, int(16 * scale), 46 * scale, IVORY)
    if with_text:
        for text, size, y, color in (("AUTO ZOOM", 136, 0.74, IVORY), ("TWEAKS", 150, 0.89, RED)):
            font = ImageFont.truetype(FONT, size)
            d.text((cx, S * y), text, font=font, fill=color, anchor="mm")
    return im


def main():
    full = draw(True)
    full.resize((256, 256), Image.Resampling.LANCZOS).save(ROOT / "preview.png", optimize=True)
    full.resize((512, 512), Image.Resampling.LANCZOS).save(MOD / "poster.png", optimize=True)
    draw(False).resize((64, 64), Image.Resampling.LANCZOS).save(MOD / "icon.png", optimize=True)
    print("wrote preview.png, poster.png, icon.png")


if __name__ == "__main__":
    main()
