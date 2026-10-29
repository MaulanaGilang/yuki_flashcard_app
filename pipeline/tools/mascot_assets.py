"""Turn the generated Yuki renders (white background) into app assets.

Inputs:  design/mascot/raw/{wave,celebrate,worried,sleepy,portrait}.png
Outputs:
  android/core/designsystem/src/main/res/drawable-nodpi/yuki_<mood>.webp   full body, transparent
  android/app/src/main/res/drawable-nodpi/yuki_bust_<mood>.webp            head + shoulders, for widgets
  android/app/src/main/res/mipmap-*/ic_launcher_foreground.png             adaptive-icon foreground
  design/logo/yuki-logo-512.png                                            store / README icon

The background is removed by flood-filling near-white pixels from the image border, so whites
inside the outlines (shirt, flashcard) stay opaque. Edge pixels get partial alpha for smooth
anti-aliasing. Runs locally on CPU; no paid service.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "design/mascot/raw"
DS_RES = ROOT / "android/core/designsystem/src/main/res"
APP_RES = ROOT / "android/app/src/main/res"
LOGO_DIR = ROOT / "design/logo"
MOODS = ["wave", "celebrate", "worried", "sleepy"]
PERSIMMON = (214, 58, 34, 255)
KEY = (255, 0, 255)


def cutout(path: Path) -> Image.Image:
    rgb = Image.open(path).convert("RGB")
    w, h = rgb.size
    filled = rgb.copy()
    seeds = [(x, 0) for x in range(0, w, 16)] + [(x, h - 1) for x in range(0, w, 16)] + \
            [(0, y) for y in range(0, h, 16)] + [(w - 1, y) for y in range(0, h, 16)]
    for s in seeds:
        r, g, b = filled.getpixel(s)
        if min(r, g, b) > 225 and (r, g, b) != KEY:
            ImageDraw.floodfill(filled, s, KEY, thresh=40)
    bg = Image.new("L", (w, h), 0)
    bg.putdata([255 if p == KEY else 0 for p in filled.getdata()])
    # Soft edge: pixels just inside the boundary fade by how light they are.
    edge = ImageChops.subtract(bg.filter(ImageFilter.MaxFilter(5)), bg)
    alpha = []
    for is_bg, is_edge, (r, g, b) in zip(bg.getdata(), edge.getdata(), rgb.getdata()):
        if is_bg:
            alpha.append(0)
        elif is_edge:
            light = (r + g + b) / 3
            alpha.append(int(max(0, min(255, (255 - light) * 4))))
        else:
            alpha.append(255)
    a = Image.new("L", (w, h))
    a.putdata(alpha)
    out = rgb.convert("RGBA")
    out.putalpha(a.filter(ImageFilter.GaussianBlur(0.6)))
    return out


def trim(img: Image.Image, pad: float = 0.04) -> Image.Image:
    box = img.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox()
    x0, y0, x1, y1 = box
    p = int(max(x1 - x0, y1 - y0) * pad)
    return img.crop((max(0, x0 - p), max(0, y0 - p), min(img.width, x1 + p), min(img.height, y1 + p)))


def fit(img: Image.Image, height: int) -> Image.Image:
    return img.resize((round(img.width * height / img.height), height), Image.LANCZOS)


def save_webp(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, "WEBP", quality=88, method=6)
    print(f"  {path.relative_to(ROOT)}  {img.size[0]}x{img.size[1]}  {path.stat().st_size // 1024} KB")


def bust(full: Image.Image) -> Image.Image:
    """Head and shoulders: the top ~52 % of the trimmed body, centred square."""
    h = int(full.height * 0.52)
    top = full.crop((0, 0, full.width, h))
    side = max(top.width, top.height)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(top, ((side - top.width) // 2, side - top.height), top)
    return sq.resize((256, 256), Image.LANCZOS)


def icon_foreground(portrait: Image.Image) -> Image.Image:
    """432 px adaptive-icon foreground: the face fits the 66/108 safe circle."""
    canvas = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    face = trim(portrait, pad=0.0)
    # Shoulders run off the bottom edge, so no flat cut shows inside a round mask;
    # the top of the head stays inside the 66/108 safe circle (top at y ≈ 84).
    target = 336
    scale = target / face.height
    face = face.resize((round(face.width * scale), target), Image.LANCZOS)
    canvas.paste(face, ((432 - face.width) // 2, 432 - target), face)
    return canvas


def main() -> None:
    for mood in MOODS:
        src = RAW / f"{mood}.png"
        if not src.exists():
            print("missing", src.name)
            continue
        full = trim(cutout(src))
        save_webp(fit(full, 640), DS_RES / f"drawable-nodpi/yuki_{mood}.webp")
        save_webp(bust(full), APP_RES / f"drawable-nodpi/yuki_bust_{mood}.webp")

    portrait = RAW / "portrait.png"
    if portrait.exists():
        fg = icon_foreground(cutout(portrait))
        for folder, size in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
            p = APP_RES / f"mipmap-{folder}/ic_launcher_foreground.png"
            p.parent.mkdir(parents=True, exist_ok=True)
            fg.resize((size, size), Image.LANCZOS).save(p)
        logo = Image.new("RGBA", (432, 432), PERSIMMON)
        logo.alpha_composite(fg)
        mask = Image.new("L", (432, 432), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, 431, 431), radius=96, fill=255)
        rounded = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
        rounded.paste(logo, (0, 0), mask)
        LOGO_DIR.mkdir(parents=True, exist_ok=True)
        rounded.resize((512, 512), Image.LANCZOS).save(LOGO_DIR / "yuki-logo-512.png")
        print("  icon foreground + design/logo/yuki-logo-512.png")


if __name__ == "__main__":
    main()
