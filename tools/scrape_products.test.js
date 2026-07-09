const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const script = path.join(__dirname, "scrape_demo_products.py");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "mall-scrape-products-"));
const fixture = path.join(tmpDir, "products.json");
const outputDir = path.join(tmpDir, "out");
const sqlPath = path.join(tmpDir, "crawler_products.sql");

fs.writeFileSync(
  fixture,
  JSON.stringify({
    products: [
      {
        id: 101,
        title: "Wireless Bluetooth Headphones",
        description: "Noise cancelling headset for daily use ".repeat(12),
        category: "electronics",
        brand: "SoundMax",
        price: 59.99,
        discountPercentage: 10,
        stock: 25,
        rating: 4.6,
        thumbnail: "https://example.test/headphone.jpg",
        images: [
          "https://example.test/headphone.jpg",
          "https://example.test/headphone-2.jpg",
          "https://example.test/images/very-long-product-gallery-url-that-would-push-the-mall-album-field-over-two-hundred-and-fifty-five-characters-01.jpg",
          "https://example.test/images/very-long-product-gallery-url-that-would-push-the-mall-album-field-over-two-hundred-and-fifty-five-characters-02.jpg",
        ],
      },
      {
        id: 102,
        title: "Cotton Casual Shirt",
        description: "Comfortable cotton shirt",
        category: "mens-shirts",
        brand: "UrbanLine",
        price: 29.5,
        discountPercentage: 0,
        stock: 80,
        rating: 4.1,
        thumbnail: "https://example.test/shirt.jpg",
        images: ["https://example.test/shirt.jpg"],
      },
      {
        id: 103,
        title: "Rose Perfume",
        description: "Floral fragrance",
        category: "fragrances",
        brand: "FlowerLab",
        price: 69.9,
        discountPercentage: 5,
        stock: 12,
        rating: 4.4,
        thumbnail: "https://example.test/perfume.jpg",
        images: ["https://example.test/perfume.jpg"],
      },
    ],
  }),
  "utf8"
);

const result = spawnSync(
  "python",
  [
    script,
    "--source-file",
    fixture,
    "--output-dir",
    outputDir,
    "--sql",
    sqlPath,
    "--start-product-id",
    "9001",
    "--start-home-id",
    "8001",
    "--limit",
    "3",
  ],
  { cwd: root, encoding: "utf8" }
);

function parseCsvLine(line) {
  const values = [];
  let current = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    const next = line[i + 1];
    if (ch === '"' && inQuotes && next === '"') {
      current += '"';
      i += 1;
    } else if (ch === '"') {
      inQuotes = !inQuotes;
    } else if (ch === "," && !inQuotes) {
      values.push(current);
      current = "";
    } else {
      current += ch;
    }
  }
  values.push(current);
  return values;
}

assert.strictEqual(result.status, 0, result.stderr || result.stdout);
assert(result.stdout.includes("products=3"), "script should report normalized product count");

const csv = fs.readFileSync(path.join(outputDir, "cleaned_products.csv"), "utf8");
assert(csv.includes("9001"), "csv should include generated local product id");
assert(csv.includes("Wireless Bluetooth Headphones"), "csv should include product title");
assert(csv.includes("手机数码"), "electronics should map to local mall category");
assert(csv.includes("服装"), "mens-shirts should map to local mall category");
assert(csv.includes("个护健康"), "fragrances should map to personal care category");

const csvLines = csv.trim().split(/\r?\n/);
const headers = parseCsvLine(csvLines[0]);
const records = csvLines.slice(1).map((line) => {
  const values = parseCsvLine(line);
  return Object.fromEntries(headers.map((header, index) => [header, values[index] || ""]));
});

const albumLengths = records.map((record) => record.album_pics.length);
assert(albumLengths.every((length) => length <= 255), "album_pics should fit mall varchar(255)");

const subTitleLengths = records.map((record) => record.sub_title.length);
assert(subTitleLengths.every((length) => length <= 255), "sub_title should fit mall varchar(255)");

const raw = JSON.parse(fs.readFileSync(path.join(outputDir, "raw_products.json"), "utf8"));
assert.strictEqual(raw.products.length, 3, "raw products should be persisted");

const sql = fs.readFileSync(sqlPath, "utf8");
assert(sql.includes("DELETE FROM `pms_product` WHERE `product_sn` LIKE 'CRAWLED-%';"), "sql should remove previous crawler products");
assert(sql.includes("INSERT INTO `pms_product`"), "sql should insert mall products");
assert(sql.includes("CRAWLED-101"), "sql should use stable crawler product sn");
assert(sql.includes("https://example.test/headphone.jpg"), "sql should keep image url");
assert(sql.includes("INSERT INTO `sms_home_new_product`"), "sql should add home new products");
assert(sql.includes("INSERT INTO `sms_home_recommend_product`"), "sql should add home recommended products");
assert(sql.includes("采集来源：DummyJSON 公开练习商品接口"), "sql should record data source in description");
assert(sql.includes("(8001, 9001, 'Wireless Bluetooth Headphones', 1, 1200)"), "home sort should place crawled products first");

console.log("scrape products checks passed");
