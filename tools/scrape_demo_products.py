import argparse
import csv
import json
import os
from decimal import Decimal, ROUND_HALF_UP
from urllib.request import Request, urlopen


SOURCE_LABEL = "DummyJSON 公开练习商品接口"

CATEGORY_MAP = {
    "beauty": (42, "个护健康", 3, "家用电器", 59, "采集品牌"),
    "fragrances": (42, "个护健康", 3, "家用电器", 59, "采集品牌"),
    "skin-care": (42, "个护健康", 3, "家用电器", 59, "采集品牌"),
    "smartphones": (19, "手机通讯", 2, "手机数码", 3, "华为"),
    "laptops": (54, "笔记本", 52, "电脑办公", 6, "小米"),
    "tablets": (53, "平板电脑", 52, "电脑办公", 51, "苹果"),
    "mobile-accessories": (30, "手机配件", 2, "手机数码", 21, "OPPO"),
    "mens-shirts": (8, "T恤", 1, "服装", 50, "海澜之家"),
    "mens-shoes": (29, "男鞋", 1, "服装", 58, "NIKE"),
    "womens-dresses": (11, "衬衫", 1, "服装", 50, "海澜之家"),
    "womens-shoes": (29, "男鞋", 1, "服装", 58, "NIKE"),
    "home-decoration": (47, "客厅家具", 4, "家具家装", 59, "采集品牌"),
    "furniture": (47, "客厅家具", 4, "家具家装", 59, "采集品牌"),
    "kitchen-accessories": (40, "厨房小电", 3, "家用电器", 1, "万和"),
    "groceries": (40, "厨房小电", 3, "家用电器", 1, "万和"),
    "vehicle": (48, "全新整车", 5, "汽车用品", 59, "采集品牌"),
    "motorcycle": (48, "全新整车", 5, "汽车用品", 59, "采集品牌"),
}

DEFAULT_CATEGORY = (19, "手机通讯", 2, "手机数码", 59, "采集品牌")


def parse_args():
    parser = argparse.ArgumentParser(description="Scrape public demo products and export mall-swarm product SQL.")
    parser.add_argument("--source-url", default="https://dummyjson.com/products?limit=30", help="Public product API URL")
    parser.add_argument("--source-file", default="", help="Local JSON file for offline conversion tests")
    parser.add_argument("--output-dir", default="data-acquisition/products", help="Directory for raw JSON and cleaned CSV")
    parser.add_argument("--sql", default="document/sql/crawler_products_20260709.sql", help="Generated SQL file")
    parser.add_argument("--limit", type=int, default=30, help="Max products to normalize")
    parser.add_argument("--start-product-id", type=int, default=9001)
    parser.add_argument("--start-home-id", type=int, default=8001)
    return parser.parse_args()


def load_source(args):
    if args.source_file:
        with open(args.source_file, "r", encoding="utf-8") as f:
            return json.load(f)

    request = Request(
        args.source_url,
        headers={
            "User-Agent": "mall-swarm-coursework-crawler/1.0",
            "Accept": "application/json",
        },
    )
    with urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def money(value):
    return Decimal(str(value or 0)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def sql_string(value):
    if value is None:
        return "NULL"
    text = str(value).replace("\\", "\\\\").replace("'", "\\'")
    return f"'{text}'"


def sql_decimal(value):
    return f"{money(value):.2f}"


def clean_text(value, max_length=None):
    text = " ".join(str(value or "").split())
    if max_length and len(text) > max_length:
        return text[: max_length - 1]
    return text


def fit_joined_urls(urls, max_length=255):
    selected = []
    for url in urls:
        candidate = ",".join(selected + [url])
        if len(candidate) > max_length:
            break
        selected.append(url)
    return ",".join(selected)


def category_info(category):
    return CATEGORY_MAP.get(category, DEFAULT_CATEGORY)


def discount_price(price, discount_percentage):
    price_value = money(price)
    discount = Decimal(str(discount_percentage or 0))
    if discount <= 0:
        return price_value
    return (price_value * (Decimal("100") - discount) / Decimal("100")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def normalize_products(payload, limit, start_product_id):
    products = payload.get("products", [])
    rows = []
    for index, product in enumerate(products[:limit]):
        source_id = product.get("id", index + 1)
        category = clean_text(product.get("category", ""))
        category_id, category_name, parent_category_id, parent_category_name, brand_id, fallback_brand = category_info(category)
        title = clean_text(product.get("title"), 190) or f"采集商品{source_id}"
        short_description = clean_text(product.get("description"), 240) or title
        full_description = clean_text(product.get("description"), 500) or title
        brand_name = clean_text(product.get("brand"), 120) or fallback_brand
        image_list = [clean_text(url) for url in product.get("images", []) if clean_text(url)]
        thumbnail = clean_text(product.get("thumbnail")) or (image_list[0] if image_list else "")
        album_pics = fit_joined_urls(image_list[:5]) if image_list else thumbnail[:255]
        price = money(product.get("price"))
        promotion_price = discount_price(price, product.get("discountPercentage"))
        stock = int(product.get("stock") or 100)
        sale = int(max(0, min(9999, round(float(product.get("rating") or 4) * 100))))
        rows.append(
            {
                "local_id": start_product_id + index,
                "source_id": source_id,
                "source": SOURCE_LABEL,
                "product_sn": f"CRAWLED-{source_id}",
                "sort": 1200 - index,
                "name": title,
                "pic": thumbnail,
                "album_pics": album_pics,
                "price": f"{price:.2f}",
                "promotion_price": f"{promotion_price:.2f}",
                "original_price": f"{price:.2f}",
                "stock": stock,
                "sale": sale,
                "category": category,
                "category_id": category_id,
                "category_name": category_name,
                "parent_category_id": parent_category_id,
                "parent_category_name": parent_category_name,
                "brand_id": brand_id,
                "brand_name": brand_name,
                "sub_title": short_description,
                "description": f"采集来源：{SOURCE_LABEL}；原始分类：{category}；{full_description}",
            }
        )
    return rows


def write_raw(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)


def write_csv(path, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        fieldnames = [
            "local_id",
            "source_id",
            "source",
            "product_sn",
            "sort",
            "name",
            "pic",
            "album_pics",
            "price",
            "promotion_price",
            "original_price",
            "stock",
            "sale",
            "category",
            "category_id",
            "category_name",
            "parent_category_id",
            "parent_category_name",
            "brand_id",
            "brand_name",
            "sub_title",
            "description",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def product_values(row):
    detail_html = (
        f"<p>{clean_text(row['description'])}</p>"
        f"<p><img src=\"{clean_text(row['pic'])}\" /></p>"
    )
    values = [
        row["local_id"],
        row["brand_id"],
        row["category_id"],
        0,
        row["parent_category_id"],
        sql_string(row["name"]),
        sql_string(row["pic"]),
        sql_string(row["product_sn"]),
        0,
        1,
        1,
        1,
        1,
        row["sort"],
        row["sale"],
        sql_decimal(row["price"]),
        sql_decimal(row["promotion_price"]),
        0,
        0,
        0,
        sql_string(row["sub_title"]),
        sql_string(row["description"]),
        sql_decimal(row["original_price"]),
        row["stock"],
        5,
        sql_string("件"),
        "0.00",
        0,
        sql_string("1,2,3"),
        sql_string(row["category_name"]),
        sql_string(row["description"]),
        sql_string(row["album_pics"]),
        sql_string(row["name"]),
        sql_string(row["description"]),
        sql_string(detail_html),
        sql_string(detail_html),
        "NULL",
        "NULL",
        0,
        0,
        sql_string(row["brand_name"]),
        sql_string(row["category_name"]),
    ]
    return "(" + ", ".join(str(value) for value in values) + ")"


def write_sql(path, rows, start_home_id):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = [
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "DELETE FROM `sms_home_new_product` WHERE `product_id` IN (SELECT `id` FROM `pms_product` WHERE `product_sn` LIKE 'CRAWLED-%');",
        "DELETE FROM `sms_home_recommend_product` WHERE `product_id` IN (SELECT `id` FROM `pms_product` WHERE `product_sn` LIKE 'CRAWLED-%');",
        "DELETE FROM `pms_product` WHERE `product_sn` LIKE 'CRAWLED-%';",
    ]

    if rows:
        lines.append(
            "INSERT INTO `pms_product` VALUES\n"
            + ",\n".join(product_values(row) for row in rows)
            + ";"
        )
        new_values = []
        recommend_values = []
        for index, row in enumerate(rows):
            home_id = start_home_id + index
            home_sort = 1200 - index
            new_values.append(f"({home_id}, {row['local_id']}, {sql_string(row['name'])}, 1, {home_sort})")
            recommend_values.append(f"({home_id}, {row['local_id']}, {sql_string(row['name'])}, 1, {home_sort})")
        lines.append("INSERT INTO `sms_home_new_product` VALUES\n" + ",\n".join(new_values) + ";")
        lines.append("INSERT INTO `sms_home_recommend_product` VALUES\n" + ",\n".join(recommend_values) + ";")

    lines.extend(
        [
            "COMMIT;",
            "SELECT COUNT(*) AS crawled_product_count FROM `pms_product` WHERE `product_sn` LIKE 'CRAWLED-%';",
        ]
    )
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def main():
    args = parse_args()
    payload = load_source(args)
    rows = normalize_products(payload, args.limit, args.start_product_id)
    write_raw(os.path.join(args.output_dir, "raw_products.json"), {"products": payload.get("products", [])[: args.limit]})
    write_csv(os.path.join(args.output_dir, "cleaned_products.csv"), rows)
    write_sql(args.sql, rows, args.start_home_id)
    print(f"products={len(rows)} output_dir={args.output_dir} sql={args.sql}")


if __name__ == "__main__":
    main()
