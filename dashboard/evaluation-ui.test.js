const fs = require("fs");
const path = require("path");
const assert = require("assert");

const html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");

assert(html.includes('data-view="evaluation"'), "sidebar should include algorithm evaluation view");
assert(html.includes('id="view-evaluation"'), "dashboard should include evaluation page");
assert(html.includes("recommendEvaluation"), "API should include recommendEvaluation endpoint");
assert(html.includes("/mall-recommend/recommend/evaluate"), "dashboard should call recommendation evaluation API");
assert(html.includes("evaluationKpis"), "evaluation page should render KPI cards");
assert(html.includes("evaluationChart"), "evaluation page should render comparison chart");
assert(html.includes("evaluationTable"), "evaluation page should render metric table");
assert(html.includes("Precision@K") && html.includes("Recall@K") && html.includes("NDCG@K"), "evaluation page should explain core metrics");
assert(html.includes("categoryHitRate") && html.includes("categoryPrecisionAtK") && html.includes("categoryNdcgAtK"), "evaluation page should show category interest metrics");
assert(html.includes("兴趣匹配分"), "evaluation page should highlight an interpretable interest matching score");
assert(html.includes("分类NDCG@K"), "evaluation page should expose category-level ranking quality");
assert(html.includes("renderEvaluation("), "dashboard should implement evaluation renderer");
assert(html.includes("loadEvaluation("), "dashboard should load evaluation data");

console.log("dashboard evaluation ui checks passed");
