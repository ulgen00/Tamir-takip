#!/usr/bin/env python3
"""Cep Atölyesi uygulama simgelerini üretir (kahverengi zemin, krem telefon + anahtar).
Kullanım: python3 tools/make_icons.py   (android/ klasöründen)"""
import math, os
from PIL import Image, ImageDraw

BG, FG, ACC = (122, 74, 42), (243, 235, 221), (217, 168, 119)
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
S = 4  # yumuşak kenarlar için büyük çizip küçült

def glyph(d, cx, cy, u):
    """u: birim boyut. Telefon gövdesi + ekran + çapraz anahtar."""
    w, h = 9 * u, 16 * u
    x0, y0 = cx - w / 2 - 1.2 * u, cy - h / 2
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=1.8 * u, fill=FG)
    d.rounded_rectangle([x0 + 1 * u, y0 + 2 * u, x0 + w - 1 * u, y0 + h - 2.6 * u], radius=0.5 * u, fill=BG)
    d.ellipse([x0 + w / 2 - .7 * u, y0 + h - 1.9 * u, x0 + w / 2 + .7 * u, y0 + h - .5 * u], fill=BG)
    # anahtar: sap + ağız, sağ altta telefonun üstünden çapraz
    ax, ay, L, a = cx + 3.6 * u, cy + 3.2 * u, 10 * u, math.radians(-50)
    dx, dy = math.cos(a) * L / 2, math.sin(a) * L / 2
    d.line([ax - dx, ay - dy, ax + dx, ay + dy], fill=ACC, width=int(2.2 * u))
    hx, hy, r = ax + dx, ay + dy, 2.4 * u
    d.ellipse([hx - r, hy - r, hx + r, hy + r], fill=ACC)
    jx, jy = hx + math.cos(a) * r * .55, hy + math.sin(a) * r * .55
    d.ellipse([jx - r * .55, jy - r * .55, jx + r * .55, jy + r * .55], fill=BG)
    ex, ey, r2 = ax - dx, ay - dy, 1.5 * u
    d.ellipse([ex - r2, ey - r2, ex + r2, ey + r2], fill=ACC)

def legacy(px):
    im = Image.new("RGBA", (px * S, px * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, px * S - 1, px * S - 1], radius=px * S * .22, fill=BG)
    glyph(d, px * S / 2, px * S / 2, px * S / 26)
    return im.resize((px, px), Image.LANCZOS)

def foreground(px):  # uyarlanabilir simge ön yüzü: 108dp tuval, içerik ortadaki 66dp'de
    im = Image.new("RGBA", (px * S, px * S), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    glyph(d, px * S / 2, px * S / 2, px * S / 40)
    return im.resize((px, px), Image.LANCZOS)

for name, dp in {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}.items():
    folder = os.path.join(RES, "mipmap-" + name); os.makedirs(folder, exist_ok=True)
    legacy(int(48 * dp)).save(os.path.join(folder, "ic_launcher.png"))
    foreground(int(108 * dp)).save(os.path.join(folder, "ic_launcher_foreground.png"))
print("ok")
