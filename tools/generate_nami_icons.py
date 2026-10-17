"""Package the supplied Nami artwork for Android without redrawing its symbol.

Run with Python + Pillow from any directory. Source: Nami_icon_blue.png.
Separate the blue background from the original strokes for full-bleed adaptive icons.
"""
from pathlib import Path
from io import BytesIO
from tempfile import NamedTemporaryFile
import hashlib
import json
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "Nami_icon_blue.png"
LANCZOS = Image.Resampling.LANCZOS


def fitted(image, size):
    result = Image.new("RGBA", (size, size))
    contained = ImageOps.contain(image, (size, size), LANCZOS)
    result.alpha_composite(contained, ((size - contained.width) // 2, (size - contained.height) // 2))
    return result


def layer(image, size, fraction, background=(0, 0, 0, 0)):
    result = Image.new("RGBA", (size, size), background)
    contained = fitted(image, round(size * fraction))
    result.alpha_composite(contained, ((size - contained.width) // 2, (size - contained.height) // 2))
    return result


def edge_extend(image, size):
    """Pad overscan with edge colors, without introducing a second background."""
    image = image.convert("RGBA")
    result = Image.new("RGBA", (size, size))
    x, y = (size - image.width) // 2, (size - image.height) // 2
    result.paste(image, (x, y))
    if x:
        result.paste(image.crop((0, 0, 1, image.height)).resize((x, image.height)), (0, y))
    right = size - x - image.width
    if right:
        result.paste(image.crop((image.width - 1, 0, image.width, image.height)).resize((right, image.height)),
                     (x + image.width, y))
    if y:
        result.paste(result.crop((0, y, size, y + 1)).resize((size, y)), (0, 0))
    bottom = size - y - image.height
    if bottom:
        result.paste(result.crop((0, y + image.height - 1, size, y + image.height)).resize((size, bottom)),
                     (0, y + image.height))
    return result


def blue_background(rgb, bounds):
    """Keep source blue pixels; interpolate only under the separate white strokes.

    Rounded source corners are extended from their neighboring blue pixels so
    Android can apply its own circle/squircle mask without exposing a matte.
    """
    source = rgb.crop(bounds)
    pixels = source.load()
    red, green, blue = source.split()
    white = ImageChops.darker(ImageChops.darker(red, green), blue)
    cover = white.point(lambda value: 255 if value > 100 else 0).filter(ImageFilter.MaxFilter(9)).load()
    rows = []
    for y in range(source.height):
        valid = [x for x in range(source.width) if pixels[x, y][2] >= 220 and
                 pixels[x, y][2] - pixels[x, y][0] > 18]
        if not valid:
            rows.append(None)
            continue
        left, right = valid[0], valid[-1]
        # Skip the source's anti-aliased matte boundary before extending colors.
        if right - left < 32:
            rows.append(None)
            continue
        left, right = left + 12, right - 12
        row = [pixels[min(right, max(left, x)), y] for x in range(source.width)]
        x = left
        while x <= right:
            if not cover[x, y]:
                x += 1
                continue
            start = x
            while x <= right and cover[x, y]:
                x += 1
            low, high = max(left, start - 1), min(right, x)
            for point in range(start, x):
                weight = (point - low) / max(1, high - low)
                row[point] = tuple(round(row[low][c] * (1 - weight) + row[high][c] * weight) for c in range(3))
        rows.append(row)
    available = [y for y, row in enumerate(rows) if row is not None]
    if not available:
        raise ValueError("Supplied image has no opaque blue background")
    for y, row in enumerate(rows):
        if row is None:
            rows[y] = rows[min(available, key=lambda item: abs(item - y))]
    result = Image.new("RGB", source.size)
    result.putdata([pixel for row in rows for pixel in row])
    return edge_extend(result, max(source.size))


def save(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    encoded = BytesIO()
    if path.suffix == ".webp":
        image.save(encoded, format="WEBP", lossless=True, method=6)
    else:
        image.save(encoded, format="PNG", optimize=True)
    payload = encoded.getvalue()
    if path.exists() and path.read_bytes() == payload:
        return
    # Avoid truncating a resource while an IDE/indexer holds a mapped image.
    with NamedTemporaryFile(dir=path.parent, suffix=".tmp", delete=False) as target:
        temporary = Path(target.name)
        target.write(payload)
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def generate():
    rgb = Image.open(SOURCE).convert("RGB")
    red, green, blue = rgb.split()
    minimum = ImageChops.darker(ImageChops.darker(red, green), blue)
    blue_mask = ImageChops.subtract(blue, red).point(lambda v: 255 if v > 18 else 0)
    white_mask = minimum.point(lambda v: 255 if v > 180 else 0)
    silhouette = ImageChops.lighter(blue_mask, white_mask)
    bounds = silhouette.getbbox()
    if bounds is None:
        raise ValueError("Supplied image has no blue artwork")
    card = rgb.convert("RGBA")
    card.putalpha(silhouette)
    card = card.crop(bounds)

    # Only the white strokes become the themed/notification symbol, not the blue card.
    strokes = minimum.point(lambda v: max(0, min(255, round((v - 150) * 255 / 90))))
    stroke_bounds = strokes.getbbox()
    if stroke_bounds is None:
        raise ValueError("Supplied image has no white symbol")
    glyph = Image.new("RGBA", rgb.size, (255, 255, 255, 0))
    glyph.putalpha(strokes)
    glyph = glyph.crop(stroke_bounds)

    side = max(card.size)
    background = blue_background(rgb, bounds)
    colored_strokes = rgb.convert("RGBA")
    colored_strokes.putalpha(strokes)
    foreground = fitted(colored_strokes.crop(bounds), side)
    mono_strokes = Image.new("RGBA", rgb.size, (255, 255, 255, 0))
    mono_strokes.putalpha(strokes)
    monochrome = fitted(mono_strokes.crop(bounds), side)

    def adaptive_background(size):
        return edge_extend(background.resize((round(size * 72 / 108),) * 2, LANCZOS), size)

    for density, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        folder = RES / ("mipmap-" + density)
        size = round(108 * scale)
        # The source fills the 72dp viewport; only the strokes need the 66dp safe circle.
        save(layer(foreground, size, 72 / 108), folder / "ic_launcher_foreground.png")
        save(layer(monochrome, size, 72 / 108), folder / "ic_launcher_monochrome.png")
        save(adaptive_background(size), folder / "ic_launcher_background.png")
        save(fitted(card, round(48 * scale)), folder / "ic_launcher.png")
        save(layer(glyph, round(24 * scale), 20 / 24), RES / ("drawable-" + density) / "ic_stat_name.png")

    save(layer(foreground, 432, 72 / 108), RES / "drawable/nami_launcher_foreground.png")
    save(layer(monochrome, 432, 72 / 108), RES / "drawable/nami_launcher_monochrome.png")
    save(layer(foreground, 432, 72 / 108), RES / "drawable/ic_launcher_foreground.webp")
    save(layer(monochrome, 432, 72 / 108), RES / "drawable/ic_launcher_monochrome.webp")
    save(fitted(card, 512), RES / "drawable/icon.png")
    # Legacy resource names are aliases, so every UI decodes the same artwork.
    for name in ["vivi_music_small_icon.png", "vivimusicnotification.png", "nami_launcher_background.png"]:
        (RES / "drawable" / name).unlink(missing_ok=True)
    for path in [ROOT / "app/src/main/ic_launcher-playstore.png", ROOT / "assets/ic_launcher-playstore.png",
                 ROOT / "fastlane/metadata/android/en-US/images/icon.png"]:
        save(fitted(card, 512), path)

    report_folder = ROOT / "app/build/reports"
    report_folder.mkdir(parents=True, exist_ok=True)
    composite = adaptive_background(432)
    composite.alpha_composite(layer(foreground, 432, 72 / 108))
    visible = composite.crop((72, 72, 360, 360))
    preview = Image.new("RGBA", (288 * 4, 288), (228, 233, 243, 255))
    for i, radius in enumerate([0, 144, 64]):
        mask = Image.new("L", (288, 288))
        draw = ImageDraw.Draw(mask)
        if radius == 0:
            draw.rectangle((0, 0, 287, 287), fill=255)
        else:
            draw.rounded_rectangle((0, 0, 287, 287), radius=radius, fill=255)
        preview.paste(visible, (288 * i, 0), mask)
    mono = layer(monochrome, 432, 72 / 108).crop((72, 72, 360, 360))
    mono_tint = Image.new("RGBA", (288, 288), (24, 55, 94, 255))
    mono_tint.putalpha(mono.getchannel("A"))
    mono_background = Image.new("RGBA", (288, 288), (210, 228, 255, 255))
    mono_background.alpha_composite(mono_tint)
    preview.paste(mono_background, (288 * 3, 0))
    save(preview, report_folder / "nami-icon-preview.png")
    metadata = {
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "source_size": rgb.size, "card_bounds": bounds, "stroke_bounds": stroke_bounds,
        "adaptive_layer_dp": 108, "source_viewport_dp": 72, "safe_circle_diameter_dp": 66,
        "symbol_width_dp": 72 * (stroke_bounds[2] - stroke_bounds[0]) / side,
        "background": "opaque source blue gradient with edge overscan; no navy matte",
        "preview": "square / circle / rounded-square / example themed monochrome",
    }
    (report_folder / "nami-icon-generation.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps(metadata))


if __name__ == "__main__":
    generate()
