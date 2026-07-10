const ORDER_BACK_PATHS = new Set([
  "pages/order/createOrder",
  "pages/order/order",
  "pages/order/orderDetail"
]);

function orderBackHashPath() {
  return decodeURIComponent((location.hash || "").replace(/^#\/?/, "").split("?")[0]);
}

function orderBackRouter() {
  const roots = [
    document.querySelector("#app"),
    document.querySelector("#app > div"),
    document.querySelector("body > div")
  ].filter(Boolean);
  for (const root of roots) {
    const app = root.__vue_app__;
    const router = app && (app.config?.globalProperties?.$router || app.router);
    if (router) return router;
  }
  return null;
}

function fallbackRoute(path) {
  if (path === "pages/order/createOrder") return "/pages/cart/cart";
  if (path === "pages/order/orderDetail") return "/pages/order/order?state=0";
  return "/pages/user/user";
}

function goOrderBack() {
  const path = orderBackHashPath();
  const router = orderBackRouter();
  if (history.length > 1) {
    if (router && typeof router.back === "function") {
      router.back();
      return;
    }
    history.back();
    return;
  }
  const target = fallbackRoute(path);
  if (router && typeof router.replace === "function") {
    router.replace(target);
    return;
  }
  location.hash = `#${target}`;
}

function ensureOrderBackButton() {
  const path = orderBackHashPath();
  const shouldShow = ORDER_BACK_PATHS.has(path);
  let button = document.querySelector(".mall-order-back-btn");
  if (!shouldShow) {
    if (button) button.remove();
    return;
  }
  if (!button) {
    button = document.createElement("button");
    button.className = "mall-order-back-btn";
    button.type = "button";
    button.setAttribute("aria-label", "返回");
    button.innerHTML = '<span aria-hidden="true">‹</span>';
    button.addEventListener("click", goOrderBack);
    document.body.appendChild(button);
  }
}

window.addEventListener("hashchange", () => setTimeout(ensureOrderBackButton, 80));
window.addEventListener("load", () => setTimeout(ensureOrderBackButton, 300));
setInterval(ensureOrderBackButton, 1000);
