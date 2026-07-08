const fs = require("fs");
const path = require("path");
const assert = require("assert");

const homeApi = fs.readFileSync(path.join(__dirname, "home.C2rggBES.js"), "utf8");
const indexPage = fs.readFileSync(path.join(__dirname, "pages-index-index.X49IQ7Du.js"), "utf8");

assert(homeApi.includes("trackBehavior"), "home API should expose trackBehavior");
assert(homeApi.includes("/mall-analytics/analytics/event"), "behavior tracking should post to analytics event endpoint");
assert(homeApi.includes("eventType:\"view\""), "product click tracking should send view behavior");
assert(homeApi.includes("sourcePage:\"h5-home\""), "tracking should identify H5 home source page");

assert(indexPage.includes("t as ee"), "home page should import trackBehavior");
assert(indexPage.includes("ee(a).catch"), "product click should send behavior event before navigation");

console.log("mall-app behavior tracking checks passed");
