"""Generate a 1024x1024 PNG placeholder app icon for ClaudeAdmin.

Run from repo root:
    python3 app/src/main/resources/icons/generate.py
Then rebuild the .icns:
    cd app/src/main/resources/icons
    rm -rf icon.iconset && mkdir icon.iconset
    for sz in 16 32 64 128 256 512 1024; do
        sips -z $sz $sz icon.png --out icon.iconset/icon_${sz}x${sz}.png >/dev/null
    done
    cp icon.iconset/icon_32x32.png   icon.iconset/icon_16x16@2x.png
    cp icon.iconset/icon_64x64.png   icon.iconset/icon_32x32@2x.png
    cp icon.iconset/icon_256x256.png icon.iconset/icon_128x128@2x.png
    cp icon.iconset/icon_512x512.png icon.iconset/icon_256x256@2x.png
    cp icon.iconset/icon_1024x1024.png icon.iconset/icon_512x512@2x.png
    iconutil -c icns icon.iconset -o icon.icns
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SIZE = 1024
OUT = Path(__file__).with_name("icon.png")

BG_TOP = (28, 32, 44)
BG_BOTTOM = (12, 14, 22)
ACCENT = (255, 167, 38)
GLYPH = (240, 240, 240)


def vertical_gradient(w: int, h: int, top, bottom) -> Image.Image:
    base = Image.new("RGB", (w, h), top)
    px = base.load()
    for y in range(h):
        t = y / (h - 1)
        r = int(top[0] * (1 - t) + bottom[0] * t)
        g = int(top[1] * (1 - t) + bottom[1] * t)
        b = int(top[2] * (1 - t) + bottom[2] * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return base


def squircle_mask(w: int, h: int, radius: int) -> Image.Image:
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
    return mask


def find_font(size: int) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/SFNSRounded.ttf",
        "/System/Library/Fonts/SFNS.ttf",
        "/Library/Fonts/Arial Bold.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size=size)
        except OSError:
            continue
    return ImageFont.load_default()


def main() -> None:
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    bg = vertical_gradient(SIZE, SIZE, BG_TOP, BG_BOTTOM)
    bg_rgba = bg.convert("RGBA")
    canvas.paste(bg_rgba, (0, 0), squircle_mask(SIZE, SIZE, radius=int(SIZE * 0.22)))

    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((SIZE * 0.10, SIZE * 0.05, SIZE * 0.95, SIZE * 0.55), fill=(80, 100, 140, 90))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=80))
    canvas.alpha_composite(glow)

    glyph_layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    gld = ImageDraw.Draw(glyph_layer)
    font = find_font(int(SIZE * 0.78))
    text = "C"
    bbox = gld.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = (SIZE - tw) // 2 - bbox[0]
    ty = (SIZE - th) // 2 - bbox[1] - int(SIZE * 0.02)
    gld.text((tx, ty), text, font=font, fill=GLYPH)

    accent_d = ImageDraw.Draw(glyph_layer)
    r = int(SIZE * 0.085)
    cx = int(SIZE * 0.74)
    cy = int(SIZE * 0.50)
    accent_d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=ACCENT)

    canvas.alpha_composite(glyph_layer)

    canvas.save(OUT, format="PNG")
    print(f"wrote {OUT} ({SIZE}x{SIZE})")


if __name__ == "__main__":
    main()
