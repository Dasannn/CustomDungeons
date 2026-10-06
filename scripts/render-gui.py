#!/usr/bin/env python3
"""Render local menu snapshots with Mojang textures; never redistribute the assets."""
import argparse
import hashlib
import math
from functools import lru_cache
import json
import os
from pathlib import Path
import textwrap
import urllib.parse
import urllib.request
import zipfile

from PIL import Image, ImageDraw, ImageFont

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
HOSTS = {"piston-meta.mojang.com", "piston-data.mojang.com", "launcher.mojang.com", "launchermeta.mojang.com"}


def open_official(url):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in HOSTS:
        raise ValueError(f"URL ajena a Mojang: {url}")
    response = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "CustomDungeons-GuiSnapshots/1"}), timeout=60)
    if urllib.parse.urlparse(response.url).hostname not in HOSTS:
        response.close()
        raise ValueError("Redirección ajena a Mojang")
    return response


def fetch_json(url):
    with open_official(url) as response:
        return json.loads(response.read(8 * 1024 * 1024))


def sha1(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha1").hexdigest()


def textures(cache, version):
    cache.mkdir(parents=True, exist_ok=True)
    metadata = cache / f"{version}.json"
    if metadata.exists():
        info = json.loads(metadata.read_text(encoding="utf-8"))
    else:
        manifest = fetch_json(MANIFEST)
        entry = next((v for v in manifest["versions"] if v["id"] == version), None)
        if entry is None:
            raise ValueError(f"Mojang no publica la versión exacta {version}; no se sustituye por otra versión.")
        info = fetch_json(entry["url"])
        metadata.write_text(json.dumps(info), encoding="utf-8")
    if info["id"] != version:
        raise ValueError("La caché no corresponde a la versión solicitada")
    download = info["downloads"]["client"]
    jar = cache / f"minecraft-{version}-client.jar"
    if not jar.exists() or jar.stat().st_size != download["size"] or sha1(jar) != download["sha1"]:
        temporary = jar.with_suffix(f".{os.getpid()}.part")
        try:
            with open_official(download["url"]) as response, temporary.open("wb") as target:
                size = 0
                while chunk := response.read(1024 * 1024):
                    size += len(chunk)
                    if size > download["size"]:
                        raise ValueError("Client jar supera el tamaño del manifiesto")
                    target.write(chunk)
            if size != download["size"] or sha1(temporary) != download["sha1"]:
                raise ValueError("Client jar no coincide con tamaño/SHA-1 oficial")
            temporary.replace(jar)
        finally:
            temporary.unlink(missing_ok=True)
    root = cache / f"textures-{version}"
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            parts = Path(entry.filename).parts
            if len(parts) != 5 or parts[:3] != ("assets", "minecraft", "textures") or parts[3] not in {"item", "block"} or not parts[4].endswith(".png"):
                continue
            if entry.file_size > 4 * 1024 * 1024:
                raise ValueError("Textura demasiado grande")
            target = root / parts[3] / parts[4]
            if not target.exists():
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(entry))
    return root


def texture_path(root, material):
    name = material.lower()
    # Minecraft uses a few model/texture names that differ from Bukkit materials.
    aliases = {"clock": "clock_00", "compass": "compass_16", "recovery_compass": "recovery_compass_16",
               "enchanted_golden_apple": "golden_apple", "tipped_arrow": "tipped_arrow_base"}
    name = aliases.get(name, name)
    candidates = [root / "item" / f"{name}.png"]
    if name.endswith("_stained_glass_pane"):
        name = name.removesuffix("_pane")
    candidates.extend(root / "block" / f"{name}{suffix}.png" for suffix in ("_front", "_top", "", "_side"))
    return next((path for path in candidates if path.exists()), None)


@lru_cache(maxsize=8)
def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"):
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def legend_lines(data, width=108):
    lines = []
    for slot in data["slots"]:
        if slot["material"] == "AIR":
            continue
        name = slot["name"] or slot["material"]
        first = slot["lore"][0] if slot["lore"] else ""
        label = f'{slot["slot"]}: {name} — ({"acción" if slot["action"] else "info"}) — {first}'
        lines.extend(textwrap.wrap(label, width=width, subsequent_indent="    ", break_long_words=True) or [""])
    return lines


def render(data, root, destination):
    rows = data["rows"]
    if rows not in range(3, 7) or len(data["slots"]) != rows * 9:
        raise ValueError("Snapshot con rejilla inválida")
    if sorted(s["slot"] for s in data["slots"]) != list(range(rows * 9)):
        raise ValueError("Slots duplicados o fuera de rango")
    # Render at GUI resolution, then nearest-neighbour ×4. Text belongs outside cells.
    cell, margin, top = 20, 8, 22
    chest_width, chest_height = 9 * cell + margin * 2, rows * cell + top + margin
    lines = legend_lines(data)
    width = max(chest_width, 680, math.ceil(max((font(10).getlength(line) for line in lines), default=0)) + 16)
    image = Image.new("RGB", (width, chest_height + 18 + len(lines) * 13), "#282828")
    draw = ImageDraw.Draw(image)
    x0 = (width - chest_width) // 2
    draw.rounded_rectangle((x0, 0, x0 + chest_width - 1, chest_height - 1), radius=3, fill="#c6c6c6", outline="#373737", width=2)
    draw.text((x0 + margin, 5), data["title"], font=font(10), fill=data["color"])
    missing = set()
    for slot in data["slots"]:
        x = x0 + margin + slot["slot"] % 9 * cell
        y = top + slot["slot"] // 9 * cell
        draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill="#8b8b8b")
        draw.line((x, y + cell - 1, x, y, x + cell - 1, y), fill="#373737")
        draw.line((x + 1, y + cell - 1, x + cell - 1, y + cell - 1, x + cell - 1, y + 1), fill="#ffffff")
        material = slot["material"]
        if material == "AIR":
            continue
        path = texture_path(root, material)
        if path:
            with Image.open(path) as source:
                icon = source.convert("RGBA")
                # Animated textures are vertical strips: preview their first square frame.
                icon = icon.crop((0, 0, icon.width, min(icon.width, icon.height)))
                icon = icon.resize((16, 16), Image.Resampling.NEAREST)
                image.paste(icon, (x + 2, y + 2), icon)
        else:
            missing.add(material)
            draw.rectangle((x + 3, y + 3, x + 16, y + 16), fill="#555555", outline="#dddddd")
            initials = "".join(part[0] for part in material.split("_"))[:3]
            draw.text((x + 3, y + 5), initials, font=font(7), fill="#ffffff")
    for i, line in enumerate(lines):
        draw.text((8, chest_height + 10 + i * 13), line, font=font(10), fill="#eeeeee")
    image = image.resize((image.width * 4, image.height * 4), Image.Resampling.NEAREST)
    temporary = destination.with_name(f"{destination.name}.{os.getpid()}.part")
    try:
        image.save(temporary, format="PNG")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return missing


def main():
    parser = argparse.ArgumentParser(description="Renderiza los JSON de guiSnapshots con texturas locales de Mojang.")
    parser.add_argument("--input", type=Path, default=Path("build/gui-snapshots"))
    parser.add_argument("--cache-dir", type=Path, default=Path.home() / ".cache/customdungeons")
    parser.add_argument("--version", default="26.3")
    args = parser.parse_args()
    paths = sorted(args.input.glob("*.json"))
    if not paths:
        parser.error("No hay JSON; ejecuta ./gradlew guiSnapshots --no-daemon primero.")
    try:
        root = textures(args.cache_dir, args.version)
        missing = set()
        for path in paths:
            missing.update(render(json.loads(path.read_text(encoding="utf-8")), root, path.with_suffix(".png")))
        print(f"{len(paths)} PNG generados en {args.input.resolve()}")
        if missing:
            print("Sin textura directa (iniciales): " + ", ".join(sorted(missing)))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
