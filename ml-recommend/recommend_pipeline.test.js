const assert = require("assert");
const { spawnSync } = require("child_process");
const path = require("path");

const root = path.resolve(__dirname, "..");

const result = spawnSync(
  "python",
  [
    path.join(__dirname, "run_recommend_pipeline.py"),
    "--events",
    "ml-recommend/data/sampled_events.csv",
    "--output-dir",
    "ml-recommend/output",
    "--train-ratio",
    "0.8",
    "--dry-run",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(result.status, 0, result.stderr || result.stdout);
const output = result.stdout;

for (const expected of [
  "train_itemcf.py",
  "train_ncf.py",
  "train_deepfm.py",
  "evaluate_recommendations.py",
  "export_recommend_sql.py",
  "--train-ratio 0.8",
  "model_itemcf",
  "model_ncf",
  "model_deepfm",
]) {
  assert(output.includes(expected), `dry run should include ${expected}`);
}

console.log("recommend pipeline checks passed");

const mappedUsersResult = spawnSync(
  "python",
  [
    path.join(__dirname, "run_recommend_pipeline.py"),
    "--events",
    "ml-recommend/data/sampled_events.csv",
    "--output-dir",
    "ml-recommend/output",
    "--mall-user-ids",
    "1,3,4",
    "--dry-run",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(mappedUsersResult.status, 0, mappedUsersResult.stderr || mappedUsersResult.stdout);
assert(
  mappedUsersResult.stdout.includes("--mall-user-ids 1,3,4"),
  "dry run should pass local mall user ids into export commands"
);
