"""Draws the mod's logo (shown in the mod list and used as the Modrinth/CurseForge icon): the card back, fanned out
three times over a holo-blue glow.

Run from the repository root: python3 tools/textures/make_logo.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SIZE = 512
BACK = Path("neoforge/src/main/resources/assets/minecraftygo/textures/field/card_back.png")
OUT = Path("neoforge/src/main/resources/minecraftygo_logo.png")


def main():
    logo = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # Rounded dark plate with a soft cyan glow in the middle, like the projected duel field.
    plate = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(plate).rounded_rectangle((8, 8, SIZE - 8, SIZE - 8), radius=72, fill=(14, 22, 44, 255))
    glow = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((96, 120, SIZE - 96, SIZE - 72), fill=(56, 200, 255, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(60))
    plate.alpha_composite(glow)
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle((8, 8, SIZE - 8, SIZE - 8), radius=72, fill=255)
    logo.paste(plate, (0, 0), mask)

    # Three cards fanned out, scaled up pixel-crisp from the in-game card back.
    back = Image.open(BACK).convert("RGBA")
    card = back.resize((back.width * 3, back.height * 3), Image.NEAREST)
    for angle, dx in ((18, -78), (-18, 78), (0, 0)):
        rotated = card.rotate(angle, resample=Image.BICUBIC, expand=True)
        shadow = Image.new("RGBA", rotated.size, (0, 0, 0, 0))
        shadow.putalpha(rotated.getchannel("A").point(lambda a: a // 2))
        shadow = shadow.filter(ImageFilter.GaussianBlur(8))
        x = SIZE // 2 - rotated.width // 2 + dx
        y = SIZE // 2 - rotated.height // 2 + 10 + int(abs(math.sin(math.radians(angle))) * 60)
        logo.alpha_composite(shadow, (x + 6, y + 10))
        logo.alpha_composite(rotated, (x, y))
    logo.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
