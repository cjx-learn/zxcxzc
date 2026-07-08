const fs = require("fs");
const path = require("path");
const assert = require("assert");

const controller = fs.readFileSync(
  path.join(__dirname, "src/main/java/com/macro/mall/analytics/controller/AnalyticsController.java"),
  "utf8"
);

assert(controller.includes('import com.macro.mall.common.domain.UserBehaviorEventDTO;'), "controller should accept behavior event DTO");
assert(controller.includes('@PostMapping("/event")'), "controller should expose POST /analytics/event");
assert(controller.includes("@RequestBody UserBehaviorEventDTO event"), "event endpoint should accept JSON body");
assert(controller.includes("behaviorEventRepository.insert(event)"), "event endpoint should persist behavior event");
assert(controller.includes("VALID_EVENT_TYPES"), "event endpoint should validate event type");
assert(controller.includes("view") && controller.includes("search") && controller.includes("fav") && controller.includes("cart") && controller.includes("order") && controller.includes("pay"), "event endpoint should support expected event types");

console.log("behavior event api checks passed");
