const fs = require("fs");
const path = require("path");
const assert = require("assert");

const controller = fs.readFileSync(
  path.join(__dirname, "src/main/java/com/macro/mall/recommend/controller/RecommendController.java"),
  "utf8"
);
const repository = fs.readFileSync(
  path.join(__dirname, "src/main/java/com/macro/mall/recommend/repository/RecommendRepository.java"),
  "utf8"
);

assert(
  controller.includes('@GetMapping("/evaluate")'),
  "RecommendController should expose GET /recommend/evaluate"
);

assert(
  controller.includes("recommendRepository.evaluation("),
  "RecommendController should delegate evaluation query to repository"
);

assert(
  repository.includes("recommend_evaluation"),
  "RecommendRepository should read recommend_evaluation table"
);

assert(
  repository.includes("precisionAtK") &&
    repository.includes("recallAtK") &&
    repository.includes("ndcgAtK") &&
    repository.includes("categoryHitRate") &&
    repository.includes("categoryNdcgAtK"),
  "Evaluation API should expose item-level, category-level, and category ranking metrics"
);

console.log("recommend evaluation api checks passed");
