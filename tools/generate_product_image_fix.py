import argparse
import os
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


PRODUCTS = [
    (1, "银色星芒刺绣网纱底裤", "外套"),
    (2, "银色星芒刺绣网纱底裤2", "外套"),
    (3, "银色星芒刺绣网纱底裤3", "外套"),
    (4, "银色星芒刺绣网纱底裤4", "外套"),
    (5, "银色星芒刺绣网纱底裤5", "外套"),
    (6, "银色星芒刺绣网纱底裤6", "外套"),
    (7, "女式超柔软拉毛运动开衫", "外套"),
    (8, "女式超柔软拉毛运动开衫1", "外套"),
    (9, "女式超柔软拉毛运动开衫1", "外套"),
    (10, "女式超柔软拉毛运动开衫1", "外套"),
    (11, "女式超柔软拉毛运动开衫1", "外套"),
    (12, "女式超柔软拉毛运动开衫2", "外套"),
    (13, "女式超柔软拉毛运动开衫3", "外套"),
    (14, "女式超柔软拉毛运动开衫3", "外套"),
    (18, "女式超柔软拉毛运动开衫3", "外套"),
    (22, "test", "外套"),
    (23, "毛衫测试", "手机通讯"),
    (24, "xxx", "外套"),
    (26, "华为 HUAWEI P20", "手机通讯"),
    (27, "小米8 全面屏游戏智能手机 6GB+64GB 黑色 全网通4G 双卡双待", "手机通讯"),
    (28, "小米 红米5A 全网通版 3GB+32GB 香槟金 移动联通电信4G手机 双卡双待", "手机通讯"),
    (29, "Apple iPhone 8 Plus 64GB 红色特别版 移动联通电信4G手机", "手机通讯"),
    (30, "HLA海澜之家简约动物印花短袖T恤", "T恤"),
    (31, "HLA海澜之家蓝灰花纹圆领针织布短袖T恤", "T恤"),
    (32, "HLA海澜之家短袖T恤男基础款", "T恤"),
    (33, "小米（MI）小米电视4A", "电视"),
    (34, "小米（MI）小米电视4A 65英寸", "电视"),
    (35, "耐克NIKE 男子 休闲鞋 ROSHE RUN 运动鞋 511881-010黑色41码", "男鞋"),
    (36, "耐克NIKE 男子 气垫 休闲鞋 AIR MAX 90 ESSENTIAL 运动鞋 AJ1285-101白色41码", "男鞋"),
    (37, "Apple iPhone 14 (A2884) 128GB 支持移动联通电信5G 双卡双待手机", "手机通讯"),
    (38, "Apple iPad 10.9英寸平板电脑 2022年款", "平板电脑"),
    (39, "小米 Xiaomi Book Pro 14 2022 锐龙版 2.8K超清大师屏 高端轻薄笔记本电脑", "笔记本"),
    (40, "小米12 Pro 天玑版 天玑9000+处理器 5000万疾速影像 2K超视感屏 120Hz高刷 67W快充", "手机通讯"),
    (41, "Redmi K50 天玑8100 2K柔性直屏 OIS光学防抖 67W快充 5500mAh大电量", "手机通讯"),
    (42, "HUAWEI Mate 50 直屏旗舰 超光变XMAGE影像 北斗卫星消息", "手机通讯"),
    (43, "万和（Vanward)燃气热水器天然气家用四重防冻直流变频节能全新升级增压水伺服恒温高抗风", "厨卫大电"),
    (44, "三星（SAMSUNG）500GB SSD固态硬盘 M.2接口(NVMe协议)", "硬盘"),
    (45, "OPPO Reno8 8GB+128GB 鸢尾紫 新配色上市 80W超级闪充 5000万水光人像三摄", "手机通讯"),
]

PALETTES = {
    "phone": ((36, 99, 235), (14, 165, 233), (239, 246, 255)),
    "clothes": ((219, 39, 119), (244, 114, 182), (253, 242, 248)),
    "shoe": ((15, 118, 110), (45, 212, 191), (240, 253, 250)),
    "screen": ((79, 70, 229), (129, 140, 248), (238, 242, 255)),
    "laptop": ((51, 65, 85), (148, 163, 184), (248, 250, 252)),
    "appliance": ((5, 150, 105), (110, 231, 183), (236, 253, 245)),
    "storage": ((180, 83, 9), (251, 191, 36), (255, 251, 235)),
    "default": ((37, 99, 235), (96, 165, 250), (239, 246, 255)),
}


def classify(name, category):
    text = f"{name} {category}".lower()
    if any(key in text for key in ["手机", "iphone", "huawei", "redmi", "oppo", "小米8", "mate"]):
        return "phone"
    if any(key in text for key in ["t恤", "开衫", "毛衫", "底裤", "外套", "hla"]):
        return "clothes"
    if any(key in text for key in ["鞋", "nike"]):
        return "shoe"
    if "电视" in text:
        return "screen"
    if any(key in text for key in ["笔记本", "book pro"]):
        return "laptop"
    if any(key in text for key in ["平板", "ipad"]):
        return "screen"
    if any(key in text for key in ["热水器", "厨卫"]):
        return "appliance"
    if any(key in text for key in ["ssd", "硬盘"]):
        return "storage"
    return "default"


def font_path(bold=False):
    names = [
        os.path.join(os.environ.get("WINDIR", "C:\\Windows"), "Fonts", "msyhbd.ttc" if bold else "msyh.ttc"),
        os.path.join(os.environ.get("WINDIR", "C:\\Windows"), "Fonts", "simhei.ttf"),
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for name in names:
        if Path(name).exists():
            return name
    return None


def load_font(size, bold=False):
    path = font_path(bold)
    if path:
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def blend(a, b, ratio):
    return tuple(int(a[i] * (1 - ratio) + b[i] * ratio) for i in range(3))


def rounded(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def wrap_text(draw, text, font, max_width, max_lines):
    lines = []
    current = ""
    for ch in text:
        test = current + ch
        if draw.textlength(test, font=font) <= max_width:
            current = test
        else:
            if current:
                lines.append(current)
            current = ch
            if len(lines) >= max_lines - 1:
                break
    if current and len(lines) < max_lines:
        lines.append(current)
    if len(lines) == max_lines and len("".join(lines)) < len(text):
        lines[-1] = lines[-1].rstrip("，, ") + "..."
    return lines


def draw_phone(draw, primary, secondary):
    rounded(draw, (285, 130, 515, 500), 42, (17, 24, 39), None)
    rounded(draw, (305, 165, 495, 455), 26, blend(primary, secondary, 0.35), None)
    rounded(draw, (360, 142, 440, 154), 8, (31, 41, 55), None)
    draw.ellipse((445, 190, 477, 222), fill=(241, 245, 249))
    draw.ellipse((452, 197, 470, 215), fill=secondary)
    draw.rectangle((340, 355, 460, 370), fill=(255, 255, 255))
    draw.rectangle((360, 385, 440, 400), fill=(255, 255, 255))


def draw_clothes(draw, primary, secondary):
    draw.polygon([(255, 205), (330, 145), (382, 185), (418, 185), (470, 145), (545, 205), (505, 290), (470, 270), (470, 505), (330, 505), (330, 270), (295, 290)], fill=blend(primary, secondary, 0.45))
    draw.arc((345, 150, 455, 245), 0, 180, fill=(255, 255, 255), width=12)
    draw.line((330, 275, 470, 275), fill=(255, 255, 255), width=7)
    draw.line((370, 185, 370, 505), fill=(255, 255, 255), width=6)
    draw.line((430, 185, 430, 505), fill=(255, 255, 255), width=6)


def draw_shoe(draw, primary, secondary):
    draw.polygon([(185, 400), (315, 310), (450, 330), (550, 390), (625, 412), (640, 460), (260, 460), (210, 440)], fill=blend(primary, secondary, 0.38))
    draw.rounded_rectangle((235, 452, 645, 500), radius=22, fill=(248, 250, 252))
    draw.line((310, 350, 435, 375), fill=(255, 255, 255), width=8)
    for x in range(340, 455, 35):
        draw.ellipse((x, 372, x + 14, 386), fill=(255, 255, 255))


def draw_screen(draw, primary, secondary):
    rounded(draw, (150, 140, 650, 465), 24, (15, 23, 42), None)
    rounded(draw, (175, 165, 625, 425), 14, blend(primary, secondary, 0.45), None)
    draw.rectangle((370, 465, 430, 510), fill=(51, 65, 85))
    rounded(draw, (300, 510, 500, 535), 10, (71, 85, 105), None)
    draw.line((235, 310, 565, 235), fill=(255, 255, 255), width=12)


def draw_laptop(draw, primary, secondary):
    rounded(draw, (180, 150, 620, 430), 24, (30, 41, 59), None)
    rounded(draw, (205, 175, 595, 395), 10, blend(primary, secondary, 0.35), None)
    draw.polygon([(140, 430), (660, 430), (715, 505), (85, 505)], fill=(226, 232, 240))
    draw.rectangle((340, 452, 460, 468), fill=(148, 163, 184))
    draw.line((255, 305, 545, 245), fill=(255, 255, 255), width=10)


def draw_appliance(draw, primary, secondary):
    rounded(draw, (245, 120, 555, 520), 38, (255, 255, 255), primary, width=8)
    rounded(draw, (295, 185, 505, 310), 22, blend(primary, secondary, 0.5), None)
    for x in [320, 400, 480]:
        draw.ellipse((x - 18, 365, x + 18, 401), fill=secondary)
    draw.line((320, 455, 480, 455), fill=primary, width=12)


def draw_storage(draw, primary, secondary):
    rounded(draw, (210, 160, 590, 485), 28, (30, 41, 59), None)
    rounded(draw, (245, 195, 555, 450), 18, blend(primary, secondary, 0.35), None)
    for y in [235, 285, 335, 385]:
        draw.line((275, y, 525, y), fill=(255, 255, 255), width=8)
    for x in range(265, 550, 36):
        draw.rectangle((x, 470, x + 14, 505), fill=secondary)


def draw_default(draw, primary, secondary):
    draw.polygon([(260, 215), (400, 135), (540, 215), (400, 295)], fill=secondary)
    draw.polygon([(260, 215), (400, 295), (400, 515), (260, 430)], fill=blend(primary, secondary, 0.3))
    draw.polygon([(540, 215), (400, 295), (400, 515), (540, 430)], fill=primary)
    draw.line((400, 295, 400, 515), fill=(255, 255, 255), width=6)


ICON_DRAWERS = {
    "phone": draw_phone,
    "clothes": draw_clothes,
    "shoe": draw_shoe,
    "screen": draw_screen,
    "laptop": draw_laptop,
    "appliance": draw_appliance,
    "storage": draw_storage,
    "default": draw_default,
}


def make_image(product_id, name, category, output_dir):
    theme = classify(name, category)
    primary, secondary, tint = PALETTES[theme]
    image = Image.new("RGB", (800, 800), tint)
    draw = ImageDraw.Draw(image)

    for y in range(800):
        color = blend(tint, blend(primary, secondary, 0.25), y / 1200)
        draw.line((0, y, 800, y), fill=color)

    rounded(draw, (64, 64, 736, 736), 36, (255, 255, 255), (226, 232, 240), width=2)
    rounded(draw, (96, 96, 704, 570), 34, blend(tint, (255, 255, 255), 0.52), None)
    ICON_DRAWERS[theme](draw, primary, secondary)

    title_font = load_font(34, bold=True)
    meta_font = load_font(24)
    small_font = load_font(20)

    rounded(draw, (96, 580, 704, 724), 26, (248, 250, 252), None)
    lines = wrap_text(draw, name, title_font, 540, 2)
    y = 604
    for line in lines:
        draw.text((128, y), line, fill=(15, 23, 42), font=title_font)
        y += 38
    draw.text((128, 690), f"{category}  |  商品ID {product_id}", fill=(71, 85, 105), font=small_font)

    rounded(draw, (108, 112, 250, 158), 18, primary, None)
    draw.text((128, 122), "Mall 商品", fill=(255, 255, 255), font=meta_font)
    rounded(draw, (596, 112, 690, 158), 18, (255, 255, 255), primary, width=2)
    draw.text((620, 122), f"#{product_id}", fill=primary, font=meta_font)

    image.save(output_dir / f"product-{product_id}.png", optimize=True)


def sql_escape(value):
    return value.replace("\\", "\\\\").replace("'", "''")


def write_sql(sql_path, base_url):
    ids = [product_id for product_id, _, _ in PRODUCTS]
    id_list = ", ".join(str(product_id) for product_id in ids)
    lines = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "CREATE TABLE IF NOT EXISTS pms_product_image_backup_20260707 (",
        "  id BIGINT PRIMARY KEY,",
        "  old_pic VARCHAR(500),",
        "  old_album_pics VARCHAR(1000),",
        "  backup_time DATETIME",
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",
        "INSERT IGNORE INTO pms_product_image_backup_20260707 (id, old_pic, old_album_pics, backup_time)",
        f"SELECT id, pic, album_pics, NOW() FROM pms_product WHERE id IN ({id_list});",
    ]

    for product_id, _, _ in PRODUCTS:
        url = f"{base_url.rstrip('/')}/product-{product_id}.png"
        safe_url = sql_escape(url)
        lines.append(
            "UPDATE pms_product "
            f"SET pic = '{safe_url}', album_pics = '{safe_url}' "
            f"WHERE id = {product_id};"
        )

    safe_base_url = sql_escape(base_url.rstrip("/"))
    lines.extend(
        [
            "COMMIT;",
            "SELECT COUNT(*) AS fixed_product_count FROM pms_product "
            f"WHERE id IN ({id_list}) AND pic LIKE '{safe_base_url}/product-%.png';",
        ]
    )
    sql_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--sql", required=True)
    parser.add_argument("--base-url", default="http://114.55.170.17:8201/minio/mall/20260707/fix")
    args = parser.parse_args()

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    for product_id, name, category in PRODUCTS:
        make_image(product_id, name, category, output_dir)

    write_sql(Path(args.sql), args.base_url)
    print(f"generated {len(PRODUCTS)} images")
    print(Path(args.sql))


if __name__ == "__main__":
    main()
