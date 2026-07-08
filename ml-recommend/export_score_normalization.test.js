const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "mall-score-export-"));
const recommendations = path.join(tmp, "recommendations.csv");
const output = path.join(tmp, "recommend.sql");

fs.writeFileSync(
  recommendations,
  [
    "user_id,external_item_id,category_id,score,rank_no,recommend_type,reason",
    "1,A,10,1.0,1,model_test,top",
    "1,B,10,1.0,2,model_test,second",
    "1,C,10,1.0,3,model_test,third",
  ].join("\n"),
  "utf8"
);

const result = spawnSync(
  "python",
  [
    path.join(__dirname, "export_recommend_sql.py"),
    "--recommendations",
    recommendations,
    "--output",
    output,
    "--recommend-type",
    "model_test",
    "--mall-product-ids",
    "26,37,44",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(result.status, 0, result.stderr || result.stdout);
const sql = fs.readFileSync(output, "utf8");
assert(sql.includes("0.980000, 1"), "top rank should receive a high display score");
assert(sql.includes("0.950000, 2"), "second rank should receive a lower display score");
assert(sql.includes("0.920000, 3"), "third rank should receive a lower display score");

console.log("export score normalization checks passed");
