"""Asset contracts: preserve the supplied source and prevent mask/tint regressions."""
import hashlib
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class NamiIconTest(unittest.TestCase):
    def test_source_preserved(self):
        self.assertEqual(hashlib.sha256((ROOT / "Nami_icon_blue.png").read_bytes()).hexdigest(),
                         "a309fa355927764c86448ce59aa55a8fd1f87cb9b6bd6216616a00897d8b1450")

    def test_adaptive_safe_circle_and_density(self):
        for density, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
            with self.subTest(density=density):
                size = round(108 * scale)
                with Image.open(RES / f"mipmap-{density}/ic_launcher_foreground.png") as image:
                    self.assertEqual(image.size, (size, size))
                    alpha = image.convert("RGBA").getchannel("A")
                    self.assertGreater(alpha.getextrema()[1], 0)
                    for y in range(size):
                        for x in range(size):
                            if alpha.getpixel((x, y)) > 32:
                                self.assertLessEqual((x + .5 - size / 2) ** 2 + (y + .5 - size / 2) ** 2,
                                                     (33 * scale) ** 2)
                with Image.open(RES / f"mipmap-{density}/ic_launcher.png") as legacy:
                    self.assertEqual(legacy.size, (round(48 * scale),) * 2)
                    alpha = legacy.convert("RGBA").getchannel("A")
                    self.assertGreater(alpha.getpixel((0, legacy.height // 2)), 200)
                    self.assertGreater(alpha.getpixel((legacy.width - 1, legacy.height // 2)), 200)
                    self.assertGreater(legacy.getpixel((0, legacy.height // 2))[2], 200)
                    self.assertGreater(legacy.getpixel((legacy.width - 1, legacy.height // 2))[2], 200)

    def test_full_bleed_background_has_no_matte_or_symbol(self):
        paths = list(RES.glob("mipmap-*/ic_launcher_background.png"))
        self.assertEqual(len(paths), 5)
        for path in paths:
            with self.subTest(path=str(path)), Image.open(path) as image:
                image = image.convert("RGBA")
                self.assertEqual(image.getchannel("A").getextrema(), (255, 255))
                self.assertGreaterEqual(image.getchannel("B").getextrema()[0], 200)
                self.assertLess(image.getchannel("R").getextrema()[1], 100)
                green_low, green_high = image.getchannel("G").getextrema()
                self.assertGreater(green_high - green_low, 100)

    def test_normal_and_themed_symbol_geometry_matches(self):
        for folder in RES.glob("mipmap-*"):
            foreground = folder / "ic_launcher_foreground.png"
            if not foreground.exists():
                continue
            with self.subTest(folder=folder.name), Image.open(foreground) as image, \
                    Image.open(folder / "ic_launcher_monochrome.png") as mono:
                image = image.convert("RGBA")
                alpha = image.getchannel("A")
                self.assertEqual(alpha.tobytes(), mono.convert("RGBA").getchannel("A").tobytes())
                bounds = alpha.point(lambda value: 255 if value > 32 else 0).getbbox()
                self.assertGreater(bounds[2] - bounds[0], image.width * .44)
                self.assertLess(bounds[2] - bounds[0], image.width * .49)
                for red, green, blue, opacity in image.get_flattened_data():
                    if opacity > 128:
                        self.assertGreater(min(red, green, blue), 195)

    def test_all_launcher_variants_use_full_bleed_gradient(self):
        paths = [RES / "mipmap-anydpi-v26/ic_launcher.xml", RES / "mipmap-anydpi-v26/ic_launcher_round.xml",
                 RES / "mipmap-anydpi/ic_launcher_static.xml", RES / "mipmap-anydpi/ic_launcher_static_round.xml",
                 RES / "drawable/ic_launcher.xml"]
        for path in paths:
            with self.subTest(path=path.name):
                root = ET.parse(path).getroot()
                self.assertEqual(root.find("background").get(ANDROID + "drawable"),
                                 "@mipmap/ic_launcher_background")
                self.assertIsNone(root.find(".//inset"))

    def test_monochrome_is_three_white_strokes(self):
        for path in [RES / "drawable/nami_launcher_monochrome.png",
                     RES / "drawable-xxxhdpi/ic_stat_name.png"]:
            with self.subTest(path=path.name), Image.open(path) as image:
                image = image.convert("RGBA")
                pixels = image.load()
                occupied = set()
                for y in range(image.height):
                    for x in range(image.width):
                        r, g, b, a = pixels[x, y]
                        if a:
                            self.assertEqual((r, g, b), (255, 255, 255))
                        if a > 128:
                            occupied.add((x, y))
                components = 0
                while occupied:
                    components += 1
                    pending = [occupied.pop()]
                    while pending:
                        x, y = pending.pop()
                        for neighbor in [(x-1, y), (x+1, y), (x, y-1), (x, y+1)]:
                            if neighbor in occupied:
                                occupied.remove(neighbor)
                                pending.append(neighbor)
                self.assertEqual(components, 3)
                self.assertEqual(pixels[0, 0][3], 0)

    def test_same_full_color_symbol_everywhere(self):
        expected = (RES / "drawable/icon.png").read_bytes()
        for path in [ROOT / "app/src/main/ic_launcher-playstore.png",
                     ROOT / "assets/ic_launcher-playstore.png", ROOT / "fastlane/metadata/android/en-US/images/icon.png"]:
            self.assertEqual(path.read_bytes(), expected, str(path))
        aliases = {item.get("name"): item.text for item in
                   ET.parse(RES / "values/nami_icon_aliases.xml").getroot().findall("item")}
        self.assertEqual(aliases["vivi_music_small_icon"], "@drawable/icon")
        self.assertEqual(aliases["vivimusicnotification"], "@drawable/ic_stat_name")
        self.assertFalse((RES / "drawable/vivi_music_small_icon.png").exists())
        self.assertFalse((RES / "drawable/vivimusicnotification.png").exists())

    def test_round_and_adaptive_use_same_layers(self):
        for folder, names in [("mipmap-anydpi-v26", ["ic_launcher", "ic_launcher_round"]),
                              ("mipmap-anydpi", ["ic_launcher_static", "ic_launcher_static_round"])]:
            definitions = [ET.parse(RES / folder / (name + ".xml")).getroot() for name in names]
            for node in ["background", "foreground", "monochrome"]:
                self.assertEqual(definitions[0].find(node).get(ANDROID + "drawable"),
                                 definitions[1].find(node).get(ANDROID + "drawable"))

    def test_launcher_aliases_preserved(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        application = manifest.find("application")
        self.assertEqual(application.get(ANDROID + "icon"), "@mipmap/ic_launcher")
        aliases = {alias.get(ANDROID + "name"): alias for alias in application.findall("activity-alias")}
        for name in [".WelcomeActivity", ".MainActivityAlias", ".MainActivityStatic"]:
            self.assertEqual(aliases[name].get(ANDROID + "targetActivity"), ".MainActivity")


if __name__ == "__main__":
    unittest.main(verbosity=2)
