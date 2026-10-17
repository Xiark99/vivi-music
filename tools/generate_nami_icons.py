"""Package the supplied Nami artwork for Android without redrawing its symbol.

Run with Python + Pillow from any directory. Source: Nami_icon_blue.png.
The black presentation matte is removed; original blue/white pixels are retained.
"""
from pathlib import Path
from io import BytesIO
from tempfile import NamedTemporaryFile
import hashlib
import json
from PIL import Image, ImageChops, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = ROOT / "Nami_icon_blue.png"
LANCZOS = Image.Resampling.LANCZOS
BACKGROUND = (4, 24, 64, 255)


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

    for density, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        folder = RES / ("mipmap-" + density)
        size = round(108 * scale)
        # A 54dp card lies within Android's central 66dp safe circle on a 108dp layer.
        save(layer(card, size, 54 / 108), folder / "ic_launcher_foreground.png")
        save(layer(glyph, size, 54 / 108), folder / "ic_launcher_monochrome.png")
        save(Image.new("RGBA", (size, size), BACKGROUND), folder / "ic_launcher_background.png")
        save(layer(card, round(48 * scale), 0.75, BACKGROUND), folder / "ic_launcher.png")
        save(layer(glyph, round(24 * scale), 20 / 24), RES / ("drawable-" + density) / "ic_stat_name.png")

    save(layer(card, 432, 54 / 108), RES / "drawable/nami_launcher_foreground.png")
    save(layer(glyph, 432, 54 / 108), RES / "drawable/nami_launcher_monochrome.png")
    save(layer(card, 432, 54 / 108), RES / "drawable/ic_launcher_foreground.webp")
    save(layer(glyph, 432, 54 / 108), RES / "drawable/ic_launcher_monochrome.webp")
    save(fitted(card, 512), RES / "drawable/icon.png")
    # Legacy resource names are aliases, so every UI decodes the same artwork.
    for name in ["vivi_music_small_icon.png", "vivimusicnotification.png"]:
        (RES / "drawable" / name).unlink(missing_ok=True)
    for path in [ROOT / "app/src/main/ic_launcher-playstore.png", ROOT / "assets/ic_launcher-playstore.png",
                 ROOT / "fastlane/metadata/android/en-US/images/icon.png"]:
        save(fitted(card, 512), path)

    report_folder = ROOT / "app/build/reports"
    report_folder.mkdir(parents=True, exist_ok=True)
    foreground = layer(card, 432, 54 / 108)
    composite = Image.new("RGBA", (432, 432), BACKGROUND)
    composite.alpha_composite(foreground)
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
    mono = layer(glyph, 432, 54 / 108).crop((72, 72, 360, 360))
    mono_background = Image.new("RGBA", (288, 288), BACKGROUND)
    mono_background.alpha_composite(mono)
    preview.paste(mono_background, (288 * 3, 0))
    save(preview, report_folder / "nami-icon-preview.png")
    metadata = {
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "source_size": rgb.size, "card_bounds": bounds, "stroke_bounds": stroke_bounds,
        "adaptive_layer_dp": 108, "card_dp": 54, "safe_circle_diameter_dp": 66,
        "background": "#041840", "preview": "square / circle / rounded-square / monochrome",
    }
    (report_folder / "nami-icon-generation.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    print(json.dumps(metadata))


if __name__ == "__main__":
    generate()
