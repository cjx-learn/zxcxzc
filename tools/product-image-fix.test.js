const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const script = path.join(__dirname, "generate_product_image_fix.py");
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "mall-product-image-fix-"));
const imageDir = path.join(tmpDir, "images");
const sqlPath = path.join(tmpDir, "fix.sql");

const result = spawnSync(
  "python",
  [
    script,
    "--output-dir",
    imageDir,
    "--sql",
    sqlPath,
    "--base-url",
    "http://example.test/minio/mall/20260707/fix",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(result.status, 0, result.stderr || result.stdout);

const images = fs.readdirSync(imageDir).filter((name) => name.endsWith(".png"));
assert.strictEqual(images.length, 38, "should generate images for old products only");
assert(images.includes("product-1.png"), "should generate image for old product 1");
assert(images.includes("product-45.png"), "should generate image for old product 45");
assert(!images.includes("product-46.png"), "should not overwrite newly uploaded product 46");

const firstImage = fs.readFileSync(path.join(imageDir, "product-1.png"));
assert.strictEqual(firstImage.toString("ascii", 1, 4), "PNG", "product image should be png");
assert.strictEqual(firstImage.readUInt32BE(16), 800, "image width should be stable");
assert.strictEqual(firstImage.readUInt32BE(20), 800, "image height should be stable");

const sql = fs.readFileSync(sqlPath, "utf8");
assert(sql.includes("pms_product_image_backup_20260707"), "sql should create a backup table");
assert(sql.includes("INSERT IGNORE INTO pms_product_image_backup_20260707"), "sql should backup before update");
assert(sql.includes("product-1.png"), "sql should update product 1");
assert(sql.includes("product-45.png"), "sql should update product 45");
assert(!sql.includes("product-46.png"), "sql should not update product 46");
assert(!sql.includes("/minio/mall/l/i030.png"), "sql should not keep placeholder image paths");
assert(!sql.includes("/minio/mall/l/i031.jpg"), "sql should not keep qr-code image paths");

console.log("product image fix generator checks passed");
