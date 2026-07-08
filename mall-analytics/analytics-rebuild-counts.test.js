const fs = require("fs");
const path = require("path");
const assert = require("assert");

const servicePath = path.join(
  __dirname,
  "src",
  "main",
  "java",
  "com",
  "macro",
  "mall",
  "analytics",
  "service",
  "AnalysisJobService.java"
);

const source = fs.readFileSync(servicePath, "utf8");

assert(
  source.includes('result.put("userProfileCount"'),
  "rebuild result should include userProfileCount"
);

assert(
  source.includes('result.put("productProfileCount"'),
  "rebuild result should include productProfileCount"
);

assert(
  source.includes('result.put("recommendResultCount"'),
  "rebuild result should include recommendResultCount"
);

assert(
  source.includes('tableCount("user_profile")') &&
    source.includes('tableCount("product_profile")') &&
    source.includes('tableCount("recommend_result")') &&
    source.includes("SELECT COUNT(*) FROM "),
  "rebuild counts should be read from the generated profile and recommendation tables"
);

console.log("analytics rebuild count checks passed");
