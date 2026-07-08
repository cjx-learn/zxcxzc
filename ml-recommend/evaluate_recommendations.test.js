const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { spawnSync } = require("child_process");

const root = path.resolve(__dirname, "..");
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "mall-recommend-eval-"));
const events = path.join(tmp, "events.csv");
const model = path.join(tmp, "model.csv");
const output = path.join(tmp, "evaluation.csv");
const sql = path.join(tmp, "evaluation.sql");

fs.writeFileSync(
  events,
  [
    "user_id,item_id,category_id,event_type,timestamp",
    "1,100,10,view,100",
    "1,101,10,cart,110",
    "1,200,20,pay,200",
    "1,201,20,fav,210",
    "2,300,30,view,100",
    "2,301,30,cart,120",
    "2,400,40,pay,220",
    "2,401,40,fav,230",
  ].join("\n"),
  "utf8"
);

fs.writeFileSync(
  model,
  [
    "user_id,external_item_id,category_id,score,rank_no,recommend_type,reason",
    "1,200,20,0.9,1,model_test,hit",
    "1,999,99,0.8,2,model_test,miss",
    "2,400,40,0.9,1,model_test,hit",
    "2,998,99,0.7,2,model_test,miss",
  ].join("\n"),
  "utf8"
);

const result = spawnSync(
  "python",
  [
    path.join(__dirname, "evaluate_recommendations.py"),
    "--events",
    events,
    "--recommendations",
    `model_test=${model}`,
    "--output",
    output,
    "--sql",
    sql,
    "--k",
    "2",
    "--test-ratio",
    "0.5",
    "--positive-events",
    "fav,cart,pay",
  ],
  { cwd: root, encoding: "utf8" }
);

assert.strictEqual(result.status, 0, result.stderr || result.stdout);

const csv = fs.readFileSync(output, "utf8");
assert(csv.includes("algorithm,algorithm_label,k,evaluated_user_count"), "should write evaluation header");
assert(csv.includes("category_hit_rate"), "should include category interest metrics");
assert(csv.includes("category_ndcg_at_k"), "should include category ranking metrics");
assert(csv.includes("model_test"), "should include model metrics");
assert(csv.includes("hot"), "should include hot baseline");
assert(csv.includes("0.500000"), "model precision@2 should be 0.5 on fixture");
assert(csv.includes("1.000000"), "model hit rate and recall should reach 1.0 on fixture");

const sqlText = fs.readFileSync(sql, "utf8");
assert(sqlText.includes("CREATE TABLE IF NOT EXISTS recommend_evaluation"), "sql should create table");
assert(sqlText.includes("DELETE FROM recommend_evaluation"), "sql should replace old metrics");
assert(sqlText.includes("'model_test'"), "sql should insert model_test metrics");
assert(sqlText.includes("'hot'"), "sql should insert hot baseline metrics");
assert(sqlText.includes("category_hit_rate"), "sql should persist category interest metrics");
assert(sqlText.includes("category_ndcg_at_k"), "sql should persist category ranking metrics");

console.log("recommend evaluation checks passed");
