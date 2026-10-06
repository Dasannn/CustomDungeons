#!/usr/bin/env python3
"""Offline verification of the renderer (no network or Mojang assets)."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location("render_gui", Path(__file__).with_name("render-gui.py"))
renderer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(renderer)


class RendererTest(unittest.TestCase):
    def test_texture_priority_and_glass_pane(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for kind in ("item", "block"):
                (root / kind).mkdir()
            for kind, name in (("item", "stone"), ("block", "stone_front"), ("block", "stone_top"), ("block", "purple_stained_glass"), ("item", "clock_00"), ("item", "golden_apple")):
                Image.new("RGBA", (16, 16), "green").save(root / kind / f"{name}.png")
            self.assertEqual(root / "item/stone.png", renderer.texture_path(root, "STONE"))
            (root / "item/stone.png").unlink()
            self.assertEqual(root / "block/stone_front.png", renderer.texture_path(root, "STONE"))
            self.assertEqual(root / "block/purple_stained_glass.png", renderer.texture_path(root, "PURPLE_STAINED_GLASS_PANE"))
            self.assertIsNone(renderer.texture_path(root, "UNKNOWN"))
            self.assertEqual(root / "item/clock_00.png", renderer.texture_path(root, "CLOCK"))
            self.assertEqual(root / "item/golden_apple.png", renderer.texture_path(root, "ENCHANTED_GOLDEN_APPLE"))

    def test_grid_scale_icon_and_legend(self):
        slots = [dict(slot=i, material="AIR", name="", color="#ffffff", lore=[], action=False) for i in range(27)]
        slots[0].update(material="STONE", name="Prueba", lore=["Primera línea", "Segunda línea"], action=True)
        data = dict(title="Menú de prueba", color="#ffaa00", rows=3, slots=slots)
        self.assertEqual(["0: Prueba — (acción) — Primera línea"], renderer.legend_lines(data))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "item").mkdir()
            Image.new("RGBA", (16, 16), "green").save(root / "item/stone.png")
            output = root / "output.png"
            self.assertEqual(set(), renderer.render(data, root, output))
            with Image.open(output) as image:
                self.assertEqual(680 * 4, image.width)
                self.assertEqual(0, image.height % 4)
                self.assertEqual((0, 128, 0), image.getpixel(((242 + 8 + 10) * 4, (22 + 10) * 4)))
            slots[1]["slot"] = 0
            with self.assertRaises(ValueError):
                renderer.render(data, root, output)

    def test_legend_canvas_fits_wide_characters(self):
        slots = [dict(slot=i, material="AIR", name="", color="#ffffff", lore=[], action=False) for i in range(27)]
        slots[0].update(material="UNKNOWN", name="W" * 108)
        data = dict(title="Título", color="#ffaa00", rows=3, slots=slots)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "wide.png"
            renderer.render(data, Path(directory), output)
            required = max(renderer.font(10).getlength(line) for line in renderer.legend_lines(data)) + 16
            with Image.open(output) as image:
                self.assertGreaterEqual(image.width / 4, required)

    def test_rejects_nonofficial_download_without_network(self):
        for url in ("http://piston-data.mojang.com/file", "https://example.com/client.jar", "file:///tmp/client.jar"):
            with self.assertRaises(ValueError):
                renderer.open_official(url)


if __name__ == "__main__":
    unittest.main()
