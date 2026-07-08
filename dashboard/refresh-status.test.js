const fs = require("fs");
const path = require("path");
const assert = require("assert");

const html = fs.readFileSync(path.join(__dirname, "index.html"), "utf8");

assert(
  html.includes("刷新分析完成"),
  "refresh button should show a completion message after analysis data reloads"
);

assert(
  html.includes("正在刷新分析..."),
  "refresh button should show a loading message while analysis data reloads"
);

assert(
  /async function refreshAll\(showRefreshStatus = false\)/.test(html),
  "refreshAll should only show refresh status when explicitly requested"
);

assert(
  html.includes('addEventListener("click", () => refreshAll(true))'),
  "refresh button click should request refresh status feedback"
);

assert(
  html.includes("await refreshAll(false);"),
  "rebuild should refresh data without overwriting the rebuild completion message"
);

assert(
  html.includes("function rebuildStatusText(result)"),
  "rebuild status should be formatted through a schema-aware helper"
);

assert(
  html.includes('$("rebuildStatus").textContent = rebuildStatusText(result);'),
  "rebuild status should use the schema-aware helper instead of direct count formatting"
);

console.log("dashboard refresh status checks passed");
