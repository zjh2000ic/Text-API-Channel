"""生成 Android 启动图标。

用法：
    python gen_icons.py [--source <图片>] [resDir]

- 给 --source：从图片里抠出内容（黑底白字图直接可用，按亮度做 alpha），
  再分别合成为 legacy 方图标 / 圆形图标 / adaptive 前景三套 PNG。
- 不给 --source：用内置的品牌色圆角方块 + 白色气泡占位图。

写入 mipmap-*dpi/ic_launcher.png、ic_launcher_round.png、ic_launcher_foreground.png
"""
import os
import sys

from PIL import Image, ImageDraw

BRAND = (57, 100, 254, 255)
WHITE = (255, 255, 255, 255)
BLACK = (0, 0, 0, 255)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

SS = 8  # 超采样倍数，画完再缩小获得平滑边缘


# ---------------------------------------------------------------- 占位图（品牌色气泡）

def draw_bubble(draw, size, scale=1.0):
    """在 size×size 画布上画一个白色的圆角对话气泡（含三个点）。"""
    w = size * scale
    left = w * 0.26
    top = w * 0.29
    right = w * 0.74
    bottom = w * 0.63
    radius = w * 0.11
    draw.rounded_rectangle([left, top, right, bottom], radius=radius, fill=WHITE)
    tail = [
        (w * 0.36, bottom - w * 0.02),
        (w * 0.33, w * 0.75),
        (w * 0.50, bottom - w * 0.02),
    ]
    draw.polygon(tail, fill=WHITE)
    dot_r = w * 0.032
    cy = (top + bottom) / 2
    for cx in (w * 0.38, w * 0.50, w * 0.62):
        draw.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=BRAND)


def placeholder(size, round_icon=False, transparent_bg=False):
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    if transparent_bg:
        draw_bubble(draw, big, scale=0.62)
    elif round_icon:
        draw.ellipse([0, 0, big - 1, big - 1], fill=BRAND)
        draw_bubble(draw, big, scale=0.86)
    else:
        radius = big * 0.22
        draw.rounded_rectangle([0, 0, big - 1, big - 1], radius=radius, fill=BRAND)
        draw_bubble(draw, big, scale=0.86)

    return img.resize((size, size), Image.LANCZOS)


# ---------------------------------------------------------------- 从图片生成

def extract_layer(path):
    """把图片里的内容抠成带 alpha 的一层（黑底→透明），并裁到内容外接框。"""
    src = Image.open(path).convert("RGB")
    # 亮度当 alpha：纯黑背景变全透明，白色字保留；阈值滤掉压缩噪点
    alpha = src.convert("L").point(lambda v: 0 if v < 14 else v)
    layer = src.convert("RGBA")
    layer.putalpha(alpha)
    box = alpha.getbbox()
    if box:
        layer = layer.crop(box)
    return layer


def compose(layer, size, scale, round_icon=False, transparent_bg=False):
    """把内容层按 scale 缩放并居中合成到 size×size 画布上。"""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    target = max(1, int(round(size * scale)))
    content = layer.copy()
    content.thumbnail((target, target), Image.LANCZOS)
    offset = ((size - content.width) // 2, (size - content.height) // 2)

    if transparent_bg:
        canvas.alpha_composite(content, offset)
        return canvas

    # 底：圆形或圆角方块（用 4 倍画布画遮罩，边缘更顺）
    mask_scale = 4
    mask = Image.new("L", (size * mask_scale, size * mask_scale), 0)
    draw = ImageDraw.Draw(mask)
    if round_icon:
        draw.ellipse([0, 0, size * mask_scale - 1, size * mask_scale - 1], fill=255)
    else:
        draw.rounded_rectangle(
            [0, 0, size * mask_scale - 1, size * mask_scale - 1],
            radius=size * mask_scale * 0.22,
            fill=255,
        )
    mask = mask.resize((size, size), Image.LANCZOS)

    background = Image.new("RGBA", (size, size), BLACK)
    background.alpha_composite(content, offset)
    background.putalpha(mask)
    return background


def main():
    args = sys.argv[1:]
    source = None
    if "--source" in args:
        index = args.index("--source")
        source = args[index + 1]
        del args[index:index + 2]
    res_dir = args[0] if args else "app/src/main/res"

    layer = extract_layer(source) if source else None
    print("source:", source, "content:", layer.size if layer else "placeholder")

    for density, factor in DENSITIES.items():
        folder = os.path.join(res_dir, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)

        legacy = round(48 * factor)
        foreground = round(108 * factor)  # adaptive icon 前景画布 108dp

        if layer is None:
            placeholder(legacy).save(os.path.join(folder, "ic_launcher.png"))
            placeholder(legacy, round_icon=True).save(
                os.path.join(folder, "ic_launcher_round.png"))
            placeholder(foreground, transparent_bg=True).save(
                os.path.join(folder, "ic_launcher_foreground.png"))
        else:
            # legacy 方图：内容占 84%，圆角 22%
            compose(layer, legacy, 0.84).save(os.path.join(folder, "ic_launcher.png"))
            compose(layer, legacy, 0.76, round_icon=True).save(
                os.path.join(folder, "ic_launcher_round.png"))
            # adaptive 前景：内容收进中间安全区（约 62%），背景由 colors 里的纯色提供
            compose(layer, foreground, 0.62, transparent_bg=True).save(
                os.path.join(folder, "ic_launcher_foreground.png"))

        print("wrote", folder, legacy, foreground)


if __name__ == "__main__":
    main()
