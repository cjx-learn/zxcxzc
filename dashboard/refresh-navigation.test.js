const fs = require("fs");
const path = require("path");
const assert = require("assert");

const html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");

assert(
  html.includes("await searchProducts(false);"),
  "refreshAll should refresh product search data without opening the product view"
);

assert(
  !html.includes("if (exactMatch || (openFirst && rows.length === 1))"),
  "searchProducts(false) must not auto-open exact product matches"
);

assert(
  html.includes("if (openFirst && (exactMatch || rows.length === 1))"),
  "searchProducts(true) should still auto-open exact or single product matches"
);

console.log("dashboard refresh navigation checks passed");
