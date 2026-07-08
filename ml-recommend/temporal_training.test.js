const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "mall-temporal-train-"));
const events = path.join(tmp, "events.csv");
const output = path.join(tmp, "itemcf.csv");

fs.writeFileSync(
  events,
  [
    "user_id,item_id,category_id,event_type,timestamp",
    "1,A,10,cart,100",
    "1,B,10,pay,200",
    "2,A,10,cart,100",
    "2,B,10,pay,110",
    "2,C,20,view,200",
    "2,D,20,view,210",
  ].join("\n"),
  "utf8"
);

const result = spawnSync(
  "python",
  [
    path.join(__dirname, "train_itemcf.py"),
    "--events",
    events,
    "--output",
    output,
    "--topn",
    "2",
    "--train-ratio",
    "0.5",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(result.status, 0, result.stderr || result.stdout);
const csv = fs.readFileSync(output, "utf8");
assert(csv.includes("1,B,10"), "temporal ItemCF should recommend a held-out item that is similar from training users");

console.log("temporal training checks passed");
