"""Generate the FrameAI SDR creative LUTs and the two sample photos.

These are original looks for normal sRGB JPEGs. They are not log-to-Rec.709 transforms.
"""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LUT_DIR = Path(__file__).resolve().parent
ASSET_DIR = ROOT / "app" / "src" / "main" / "assets" / "luts"
SAMPLE_DIR = LUT_DIR / "samples"


def clamp(value: float) -> float:
    return 0.0 if value < 0.0 else 1.0 if value > 1.0 else value


def soft_contrast(value: float, amount: float) -> float:
    return clamp(0.5 + (value - 0.5) * (1.0 + amount))


def warm_film(red: float, green: float, blue: float) -> tuple[float, float, float]:
    red = soft_contrast(red, 0.06)
    green = soft_contrast(green, 0.04)
    blue = soft_contrast(blue, 0.08)
    luma = (red + green + blue) / 3.0
    shadow = max(0.0, 1.0 - luma) ** 1.1
    red = red * 1.045 + 0.028 * shadow
    green = green * 1.005 + 0.006 * shadow
    blue = blue * 0.90
    return clamp(red), clamp(green), clamp(blue)


def cool_cinema(red: float, green: float, blue: float) -> tuple[float, float, float]:
    red = soft_contrast(red, 0.10)
    green = soft_contrast(green, 0.06)
    blue = soft_contrast(blue, 0.05)
    luma = (red + green + blue) / 3.0
    shadow = max(0.0, 1.0 - luma)
    red = red * 0.94 - 0.02 * shadow
    green = green * 0.99 + 0.01 * shadow
    blue = blue * 1.06 + 0.03 * shadow
    return clamp(red), clamp(green), clamp(blue)


def identity(_red: float, _green: float, _blue: float, *, size: int, red_index: int, green_index: int, blue_index: int) -> tuple[float, float, float]:
    last = size - 1
    return red_index / last, green_index / last, blue_index / last


def write_cube(path: Path, title: str, size: int, grade) -> None:
    lines = [
        "# FrameAI HackUTA 2026 SDR creative LUT. Not a camera log transform.",
        f'TITLE "{title}"',
        f"LUT_3D_SIZE {size}",
        "DOMAIN_MIN 0.0 0.0 0.0",
        "DOMAIN_MAX 1.0 1.0 1.0",
        "",
    ]
    last = size - 1
    for blue_index in range(size):
        for green_index in range(size):
            for red_index in range(size):
                if title == "identity_4":
                    red, green, blue = identity(
                        0.0,
                        0.0,
                        0.0,
                        size=size,
                        red_index=red_index,
                        green_index=green_index,
                        blue_index=blue_index,
                    )
                else:
                    red, green, blue = grade(red_index / last, green_index / last, blue_index / last)
                lines.append(f"{red:.6f} {green:.6f} {blue:.6f}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def write_ppm(path: Path, width: int, height: int, pixels: list[tuple[int, int, int]]) -> None:
    body = ["P3", f"{width} {height}", "255"]
    for red, green, blue in pixels:
        body.append(f"{red} {green} {blue}")
    path.write_text("\n".join(body) + "\n", encoding="ascii", newline="\n")


def portrait_pixels() -> list[tuple[int, int, int]]:
    pixels = []
    for y in range(90):
        for x in range(60):
            if 34 <= x <= 46 and 24 <= y <= 36:
                pixels.append((210, 160, 130))
            else:
                pixels.append((40, 70, 110))
    return pixels


def landscape_pixels() -> list[tuple[int, int, int]]:
    pixels = []
    for y in range(60):
        for _x in range(90):
            if y < 30:
                pixels.append((120, 170, 220))
            else:
                pixels.append((70, 120, 60))
    return pixels


def self_check() -> None:
    warm_gray = warm_film(0.5, 0.5, 0.5)
    cool_gray = cool_cinema(0.5, 0.5, 0.5)
    if not warm_gray[0] > warm_gray[2] + 0.04:
        raise SystemExit(f"warm gray is not warm enough: {warm_gray}")
    if not cool_gray[2] > cool_gray[0] + 0.02:
        raise SystemExit(f"cool gray is not cool enough: {cool_gray}")
    skin = warm_film(0.78, 0.55, 0.42)
    if not (skin[0] > skin[1] > skin[2]):
        raise SystemExit(f"warm skin collapsed: {skin}")
    if max(abs(skin[0] - 0.78), abs(skin[1] - 0.55), abs(skin[2] - 0.42)) > 0.2:
        raise SystemExit(f"warm skin moved too far: {skin}")
    black = warm_film(0.0, 0.0, 0.0)
    white = warm_film(1.0, 1.0, 1.0)
    if max(black) > 0.08 or min(white) < 0.8:
        raise SystemExit(f"warm endpoints drifted: black={black} white={white}")


def main() -> None:
    self_check()
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)
    # warm_film and cool_cinema are the downloaded Cinematic_04 and Cinematic_08 files.
    # This script only refreshes the identity lattice and the sample photos.
    files = {
        "identity_4.cube": (4, None),
    }
    for name, (size, grade) in files.items():
        title = name.replace(".cube", "")
        for directory in (LUT_DIR, ASSET_DIR):
            write_cube(directory / name, title, size, grade)
    write_ppm(SAMPLE_DIR / "portrait_skin.ppm", 60, 90, portrait_pixels())
    write_ppm(SAMPLE_DIR / "landscape_noface.ppm", 90, 60, landscape_pixels())
    (SAMPLE_DIR / "portrait_skin.json").write_text(
        json.dumps({"width": 60, "height": 90, "sampleX": 40, "sampleY": 30, "sample": "skin"}) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    (SAMPLE_DIR / "landscape_noface.json").write_text(
        json.dumps({"width": 90, "height": 60, "sample": "noface"}) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    print("wrote LUTs and sample photos")


if __name__ == "__main__":
    main()
