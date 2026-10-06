"""Renames the mod from MinecraftYGO (mod id minecraftygo) to Just Another Dueling Mod (mod id jadm).

Rewrites file contents and moves files, so it can be run again on any branch that still uses the old names
(e.g. after merging main into it). Running it twice changes nothing the second time.

    python3 tools/rename/rename_mod.py          # rename in the working tree
    python3 tools/rename/rename_mod.py --check  # list what is left, exit 1 if anything is

Card scripts (engine/src/main/resources/<mod id>/scripts) come from ProjectIgnis and only move, their contents stay.
Names that belong to others stay too: YGOPRODeck, YGOPro, YGOMCModels and the GitHub repository URL, and so does
the changelog line that says what the mod was renamed from.
"""
import argparse
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = re.compile(r"^engine/src/main/resources/(minecraftygo|jadm)/scripts/")
REPO_URL = "github.com/Zancrow321/MinecraftYGO"
REPO_URL_MARK = "\0REPO_URL\0"
KEEP_LINE = "- Renamed from MinecraftYGO"  # the changelog line that names the old names on purpose

# Applied in order. Each pattern no longer matches its own output, which keeps the script idempotent.
RULES = [
    (re.compile(r"MinecraftYgo"), "Jadm"),  # main class
    (re.compile(r"MinecraftYGO"), "Just Another Dueling Mod"),  # display name
    (re.compile(r"minecraftygo"), "jadm"),  # mod id, packages, resource folders, config folder
    (re.compile(r"\bYgo(?=[A-Z])"), "Jadm"),  # class prefixes: YgoItems, YgoData, ...
    (re.compile(r"\bYGO Duelist\b"), "Duelist"),  # Figura starter avatar
    (re.compile(r"\bygo(?!pro)"), "jadm"),  # /ygo command, Figura API and events, pings, ygo-duelist, ygo.playtest
    (re.compile(r"onlyYgoItems"), "onlyModItems"),  # [shop.players] setting
    (re.compile(r"Yu-Gi-Oh! duels in Minecraft"), "Trading card duels in Minecraft"),  # descriptions
    (re.compile(r"One Yu-Gi-Oh! card\."), "One trading card."),
]

CHANGELOG_ENTRY = """### New name
- The mod is now called **Just Another Dueling Mod**, mod id `jadm`. The command is `/jadm`, Figura avatar scripts
  use the `jadm` global and `jadm.<event>` events, and the starter avatar is `jadm-duelist`.
- Renamed from MinecraftYGO (mod id `minecraftygo`, command `/ygo`, config folder `config/minecraftygo`).
- Worlds from 0.1.0 lose the mod's items, blocks and saved data (decks, binders, progress); the config starts fresh.

"""


def rename(text: str) -> str:
    return "".join(line if line.startswith(KEEP_LINE) else rename_line(line) for line in text.splitlines(True))


def rename_line(text: str) -> str:
    text = text.replace(REPO_URL, REPO_URL_MARK)
    for pattern, replacement in RULES:
        text = pattern.sub(replacement, text)
    return text.replace(REPO_URL_MARK, REPO_URL)


def tracked_files() -> list[str]:
    out = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True).stdout
    return [p for p in out.decode().split("\0") if p and p != "tools/rename/rename_mod.py"]


def is_text(data: bytes) -> bool:
    if b"\0" in data:
        return False
    try:
        data.decode("utf-8")
        return True
    except UnicodeDecodeError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="only report files that still use the old names")
    args = parser.parse_args()

    edits, moves = [], []
    for path in tracked_files():
        file = ROOT / path
        if not file.is_file():
            continue
        if not SCRIPTS.match(path):
            data = file.read_bytes()
            if is_text(data):
                text = data.decode("utf-8")
                new = rename(text)
                if new != text:
                    edits.append(path)
                    if not args.check:
                        file.write_bytes(new.encode("utf-8"))
        target = rename(path)
        if target != path:
            moves.append((path, target))

    if args.check:
        for path in edits:
            print("old name in", path)
        for path, _ in moves:
            print("old name in path", path)
        return 1 if edits or moves else 0

    # git mv keeps history; one call per target folder keeps it fast for the thousands of card scripts.
    by_folder: dict[Path, list[str]] = {}
    for path, target in moves:
        if Path(path).name == Path(target).name:
            by_folder.setdefault(Path(target).parent, []).append(path)
        else:
            (ROOT / target).parent.mkdir(parents=True, exist_ok=True)
            subprocess.run(["git", "mv", path, target], cwd=ROOT, check=True)
    for folder, paths in by_folder.items():
        (ROOT / folder).mkdir(parents=True, exist_ok=True)
        for i in range(0, len(paths), 500):
            subprocess.run(["git", "mv", *paths[i:i + 500], str(folder) + "/"], cwd=ROOT, check=True)
    changelog = ROOT / "CHANGELOG.md"
    text = changelog.read_text(encoding="utf-8")
    if "### New name" not in text and "## Unreleased\n\n" in text:
        changelog.write_text(text.replace("## Unreleased\n\n", "## Unreleased\n\n" + CHANGELOG_ENTRY, 1), encoding="utf-8")

    for path, _ in moves:  # drop the folders the moves emptied
        folder = (ROOT / path).parent
        while folder != ROOT and folder.is_dir() and not any(folder.iterdir()):
            folder.rmdir()
            folder = folder.parent
    subprocess.run(["git", "add", "-u"], cwd=ROOT, check=True)
    print(f"rewrote {len(edits)} files, moved {len(moves)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
