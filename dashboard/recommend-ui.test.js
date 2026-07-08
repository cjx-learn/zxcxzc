const fs = require("fs");
const path = require("path");
const assert = require("assert");

const html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");

assert(
  html.includes('id="recommendTypeTabs"'),
  "dashboard should render recommendation algorithm selector tabs"
);

assert(
  /recommendType:\s*"model_deepfm"/.test(html),
  "dashboard should default the current-user recommendation algorithm to DeepFM"
);

assert(
  html.includes("RECOMMEND_TYPES"),
  "dashboard should define recommendation algorithm metadata in one place"
);

assert(
  /API\.userRecommend\(state\.userId,\s*state\.recommendType,\s*8\)/.test(html),
  "loadUser should request only the selected recommendation algorithm"
);

assert(
  !/\.\.\.deepfmRows,\s*\.\.\.ncfRows,\s*\.\.\.itemcfRows,\s*\.\.\.ruleRows/.test(html),
  "dashboard should not merge different recommendation algorithms into one user list"
);

console.log("recommend-ui static checks passed");
