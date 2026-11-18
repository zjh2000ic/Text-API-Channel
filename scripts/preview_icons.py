"""拼一张预览图：legacy 方图 / 圆形 / adaptive（圆形遮罩与圆角遮罩各一份）。"""
import os

from PIL import Image, ImageDraw

RES = r"app\src\main\res"
SIZE = 192
OUT = r"dist\icon-preview.png"


def load(name):
    return Image.open(os.path.join(RES, "mipmap-xxxhdpi", name)).convert("RGBA")


def masked(img, kind, scale=4):
    n = SIZE * scale
    mask = Image.new("L", (n, n), 0)
    draw = ImageDraw.Draw(mask)
    if kind == "circle":
        draw.ellipse([0, 0, n - 1, n - 1], fill=255)
    elif kind == "squircle":
        draw.rounded_rectangle([0, 0, n - 1, n - 1], radius=n * 0.28, fill=255)
    else:
        draw.rectangle([0, 0, n - 1, n - 1], fill=255)
    out = img.copy()
    out.putalpha(mask.resize((SIZE, SIZE), Image.LANCZOS))
    return out


def adaptive(kind):
    fg = load("ic_launcher_foreground.png").resize((SIZE, SIZE), Image.LANCZOS)
    bg = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    # WebView/启动器实际只显示 108dp 里中间约 72dp，这里按 0.72 裁一块看看
    inner = int(SIZE * 0.72)
    offset = (SIZE - inner) // 2
    bg.alpha_composite(fg.crop((offset, offset, offset + inner, offset + inner)), (offset, offset))
    return masked(bg, kind)


tiles = [
    ("legacy", load("ic_launcher.png")),
    ("round", load("ic_launcher_round.png")),
    ("adaptive-circle", adaptive("circle")),
    ("adaptive-squircle", adaptive("squircle")),
]

pad = 16
canvas = Image.new("RGBA", (len(tiles) * (SIZE + pad) + pad, SIZE + pad * 2), (245, 245, 247, 255))
for i, (_, img) in enumerate(tiles):
    canvas.alpha_composite(img, (pad + i * (SIZE + pad), pad))
canvas.save(OUT)
print("wrote", OUT, canvas.size)
