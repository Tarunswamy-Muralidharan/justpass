from PIL import Image, ImageDraw, ImageFont
import math

OUT_DIR = r"C:\Users\tmswa\Desktop"

NAVY = (13, 27, 42, 255)      # #0D1B2A
CAP_LIGHT = (236, 239, 241, 255)  # #ECEFF1
CAP_SHADOW = (176, 190, 197, 255)  # #B0BEC5
GOLD = (255, 213, 79, 255)    # #FFD54F

def make_icon(size=512):
    """Play Store icon — full square, centered cap with trimmed tassel."""
    img = Image.new("RGBA", (size, size), NAVY)
    d = ImageDraw.Draw(img)
    # 100-unit viewport with generous padding; cap sized for square frame
    s = size / 100.0

    def pt(x, y):
        return (x * s, y * s)

    def quad(p0, p1, p2, steps=24):
        pts = []
        for i in range(steps + 1):
            t = i / steps
            x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
            y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
            pts.append((x, y))
        return pts

    # Cap centered at (50, 42), half-width 28, half-height 10
    d.polygon([pt(50, 32), pt(78, 42), pt(50, 52), pt(22, 42)], fill=CAP_LIGHT)
    d.polygon([pt(50, 52), pt(78, 42), pt(78, 46), pt(50, 56), pt(22, 46), pt(22, 42)], fill=CAP_SHADOW)

    # Tassel: short + elegant, hangs below cap
    cord_w = max(1, int(round(2.2 * s)))
    cord = [pt(50, 42), pt(50, 56)] + quad(pt(50, 56), pt(52, 64), pt(58, 68))
    d.line(cord, fill=GOLD, width=cord_w, joint="curve")

    # 3 short tassel threads hanging from end of cord
    thread_w = max(1, int(round(2.0 * s)))
    end = pt(58, 68)
    for dx, dy in [(-2, 8), (0, 9), (2, 8)]:
        d.line([end, (end[0] + dx * s, end[1] + dy * s)], fill=GOLD, width=thread_w)

    # Gold button at cord start
    cx, cy = pt(50, 42)
    r = 2.8 * s
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=GOLD)

    return img


def make_feature_graphic():
    W, H = 1024, 500
    img = Image.new("RGB", (W, H), NAVY[:3])
    d = ImageDraw.Draw(img)

    icon_size = 300
    img.paste(make_icon(icon_size), (70, (H - icon_size) // 2), make_icon(icon_size))

    try:
        title_font = ImageFont.truetype("arialbd.ttf", 130)
        sub_font = ImageFont.truetype("arial.ttf", 36)
    except Exception:
        title_font = ImageFont.load_default()
        sub_font = ImageFont.load_default()

    d.text((410, 170), "JustPass", fill=(255, 255, 255), font=title_font)
    d.text((414, 320), "Attendance, made simple.", fill=(255, 213, 79), font=sub_font)

    return img


if __name__ == "__main__":
    icon = make_icon(512)
    icon.save(f"{OUT_DIR}\\play_icon_512.png")
    print("wrote play_icon_512.png")

    fg = make_feature_graphic()
    fg.save(f"{OUT_DIR}\\play_feature_graphic.png")
    print("wrote play_feature_graphic.png")
