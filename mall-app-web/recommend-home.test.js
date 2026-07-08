const fs = require("fs");
const path = require("path");
const assert = require("assert");

const homeApi = fs.readFileSync(path.join(__dirname, "home.C2rggBES.js"), "utf8");

assert(
  homeApi.includes("/mall-recommend/recommend/user/"),
  "home recommendation API should request personalized recommendation results"
);

assert(
  homeApi.includes("/mall-recommend/recommend/hot"),
  "home recommendation API should fall back to hot recommendations"
);

assert(
  homeApi.includes("model_deepfm"),
  "home recommendation API should use DeepFM as the default user-facing algorithm"
);

assert(
  !/url:\s*["']\/home\/recommendProductList/.test(homeApi),
  "home recommendation API should not use the legacy static recommendProductList endpoint"
);

assert(
  homeApi.includes("productName") && homeApi.includes("productPic") && homeApi.includes("productPrice"),
  "home recommendation API should normalize recommendation rows into product card fields"
);

console.log("mall-app recommendation checks passed");
