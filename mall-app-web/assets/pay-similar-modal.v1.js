const PAY_SIMILAR_LIMIT = 16;
let lastPayRouteKey = "";
let lastTriggerRouteKey = "";
const exposureMemory = new Set();
const PAY_SIMILAR_TARGET_PATHS = new Set(["", "pages/order/order", "pages/index/index"]);
const PAY_SIMILAR_CORE_TYPES = new Set([
  "\u540c\u5c0f\u7c7b",
  "\u540c\u5927\u7c7b",
  "\u884c\u4e3a\u76f8\u4f3c"
]);

function currentHashPath() {
  return decodeURIComponent((location.hash || "").replace(/^#\/?/, "").split("?")[0]);
}

function currentHashParams() {
  const query = (location.hash || "").split("?")[1] || "";
  return new URLSearchParams(query);
}

function storageValue(key) {
  try {
    return localStorage.getItem(key) || sessionStorage.getItem(key) || "";
  } catch (error) {
    return "";
  }
}

function parseMaybeJson(value) {
  if (!value || typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch (error) {
    return value;
  }
}

function findIdDeep(value, depth = 0) {
  if (!value || depth > 4) return null;
  if (typeof value === "string") return findIdDeep(parseMaybeJson(value), depth + 1);
  if (typeof value !== "object") return null;
  const direct = value.id || value.memberId || value.userId;
  if (direct) return direct;
  for (const key of ["memberInfo", "userInfo", "user", "data", "value", "state"]) {
    const found = findIdDeep(value[key], depth + 1);
    if (found) return found;
  }
  return null;
}

function getStoredUserId() {
  const directKeys = ["member", "memberInfo", "userInfo", "user", "pinia-member"];
  for (const key of directKeys) {
    const id = findIdDeep(storageValue(key));
    if (id) return id;
  }
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const id = findIdDeep(localStorage.getItem(localStorage.key(i)));
      if (id) return id;
    }
  } catch (error) {}
  return null;
}

function getRecommendSessionId() {
  try {
    let sessionId = localStorage.getItem("mall_h5_session_id");
    if (!sessionId) {
      sessionId = `h5-${Date.now()}-${Math.random().toString(16).slice(2)}`;
      localStorage.setItem("mall_h5_session_id", sessionId);
    }
    return sessionId;
  } catch (error) {
    return "h5-session";
  }
}

function getToken() {
  const direct = storageValue("token") || storageValue("Authorization");
  if (direct) return direct;
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const value = localStorage.getItem(localStorage.key(i));
      if (typeof value === "string") {
        const match = value.match(/Bearer\s+[A-Za-z0-9._-]+/);
        if (match) return match[0];
      }
    }
  } catch (error) {}
  return "";
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}

function imageUrl(path) {
  if (!path) return "./static/errorImage.jpg";
  if (/^https?:\/\//.test(path)) return path;
  return path.startsWith("/") ? path : "/" + path;
}

function productId(item) {
  return item.productId || item.id;
}

function productCategoryId(item) {
  return item.categoryId || item.productCategoryId;
}

function productName(item) {
  return item.productName || item.name || "相似好物";
}

function productPrice(item) {
  const value = item.productPrice ?? item.price;
  return value == null ? "" : Number(value).toFixed(2);
}

function markSimilarExposure(id) {
  const key = `mallRecommendView:h5-pay-similar:${id}`;
  try {
    if (sessionStorage.getItem(key)) return false;
    sessionStorage.setItem(key, "1");
  } catch (error) {
    if (exposureMemory.has(key)) return false;
    exposureMemory.add(key);
  }
  return true;
}

function trackSimilarExposure(item) {
  const id = Number(productId(item));
  if (!Number.isFinite(id) || id <= 0 || !markSimilarExposure(id)) return;
  const categoryId = Number(productCategoryId(item));
  const userId = Number(getStoredUserId());
  fetch("/mall-analytics/analytics/event", {
    method: "POST",
    headers: { "Content-Type": "application/json", "source-client": "miniapp" },
    body: JSON.stringify({
      userId: Number.isFinite(userId) && userId > 0 ? userId : 1,
      productId: id,
      categoryId: Number.isFinite(categoryId) && categoryId > 0 ? categoryId : null,
      eventType: "view",
      sessionId: getRecommendSessionId(),
      sourcePage: "h5-pay-similar",
      deviceType: "h5"
    })
  }).catch(error => console.warn("pay similar exposure track failed", error));
}

async function fetchOrderPrimaryProduct(orderId) {
  if (!orderId) return;
  const routeKey = `pay:${orderId}`;
  if (lastPayRouteKey === routeKey) return;
  lastPayRouteKey = routeKey;
  try {
    const token = getToken();
    const response = await fetch(`/mall-portal/order/detail/${encodeURIComponent(orderId)}`, {
      headers: {
        "source-client": "miniapp",
        ...(token ? { Authorization: token } : {})
      }
    });
    const json = await response.json();
    const detail = json && json.code === 200 ? json.data : null;
    const item = detail && Array.isArray(detail.orderItemList) ? detail.orderItemList[0] : null;
    const id = item && (item.productId || item.product_id || item.id);
    if (id) {
      sessionStorage.setItem("mallLastPaidProductId", String(id));
      sessionStorage.setItem("mallLastPaidOrderId", String(orderId));
      sessionStorage.removeItem(`mallSimilarModalShown:${orderId}:${id}`);
    }
  } catch (error) {
    console.warn("pay similar preload failed", error);
  }
}

async function fetchSimilarProducts(productId) {
  const response = await fetch(`/mall-recommend/recommend/similar/${encodeURIComponent(productId)}?limit=${PAY_SIMILAR_LIMIT}`, {
    headers: { "source-client": "miniapp" }
  });
  const json = await response.json();
  if (!json || json.code !== 200 || !Array.isArray(json.data)) return [];
  return json.data.filter(item => PAY_SIMILAR_CORE_TYPES.has(item.similarType));
}

function appRouter() {
  const roots = [document.querySelector("#app"), document.querySelector("#app > div"), document.querySelector("body > div")].filter(Boolean);
  for (const root of roots) {
    const app = root.__vue_app__;
    const router = app && (app.config?.globalProperties?.$router || app.router);
    if (router && typeof router.push === "function") return router;
  }
  return null;
}

function openProduct(id) {
  if (!id) return;
  closeSimilarModal();
  const url = `/pages/product/product?id=${encodeURIComponent(id)}&_t=${Date.now()}&_from=paySimilar`;
  const router = appRouter();
  if (router) {
    router.push(url);
    return;
  }
  location.hash = `#${url}`;
}

function closeSimilarModal() {
  const modal = document.querySelector(".mall-pay-similar-mask");
  if (modal) modal.remove();
}

function markPaySimilarPending() {
  if (currentHashPath() !== "pages/money/paySuccess") return;
  if (sessionStorage.getItem("mallLastPaidProductId")) {
    sessionStorage.setItem("mallSimilarModalPending", "1");
  }
}

function renderSimilarModal(rows) {
  closeSimilarModal();
  const mask = document.createElement("div");
  mask.className = "mall-pay-similar-mask";
  mask.innerHTML = `
    <section class="mall-pay-similar-dialog" role="dialog" aria-modal="true" aria-label="相似好物推荐">
      <button class="mall-pay-similar-close" type="button" aria-label="关闭">×</button>
      <div class="mall-pay-similar-title">相似好物推荐</div>
      <div class="mall-pay-similar-subtitle">根据刚付款商品，为你推荐同类、同价位和共同行为用户喜欢的商品</div>
      <div class="mall-pay-similar-list">
        ${rows.map(item => `
          <article class="mall-pay-similar-card" data-product-id="${escapeHtml(productId(item) || "")}">
            <img class="mall-pay-similar-img" src="${escapeHtml(imageUrl(item.productPic || item.pic))}" alt="" loading="lazy">
            <div class="mall-pay-similar-info">
              <div class="mall-pay-similar-name">${escapeHtml(productName(item))}</div>
              <div class="mall-pay-similar-reason">${escapeHtml(item.similarType || item.reason || "相似商品")}</div>
              <div class="mall-pay-similar-price">${escapeHtml(productPrice(item))}</div>
            </div>
          </article>
        `).join("")}
      </div>
    </section>`;
  mask.addEventListener("click", event => {
    if (event.target === mask || event.target.closest(".mall-pay-similar-close")) closeSimilarModal();
  });
  mask.querySelectorAll(".mall-pay-similar-card").forEach(card => {
    card.addEventListener("click", () => openProduct(card.dataset.productId));
  });
  document.body.appendChild(mask);
}

async function maybeShowPendingPaySimilar() {
  const path = currentHashPath();
  const productId = sessionStorage.getItem("mallLastPaidProductId");
  const orderId = sessionStorage.getItem("mallLastPaidOrderId") || "";
  if (!productId) return;

  if (path === "pages/money/paySuccess") {
    sessionStorage.setItem("mallSimilarModalPending", "1");
    closeSimilarModal();
    return;
  }

  if (!PAY_SIMILAR_TARGET_PATHS.has(path)) return;
  if (sessionStorage.getItem("mallSimilarModalPending") !== "1") return;

  const showKey = `mallSimilarModalShown:${orderId}:${productId}`;
  if (sessionStorage.getItem(showKey)) return;
  const routeKey = `${path}:${orderId}:${productId}:${location.hash}`;
  if (lastTriggerRouteKey === routeKey) return;
  lastTriggerRouteKey = routeKey;
  try {
    const rows = await fetchSimilarProducts(productId);
    if (!rows.length) return;
    sessionStorage.setItem(showKey, "1");
    sessionStorage.removeItem("mallSimilarModalPending");
    setTimeout(() => renderSimilarModal(rows), 360);
  } catch (error) {
    console.warn("pay similar modal failed", error);
  }
}

function observeRoute() {
  const path = currentHashPath();
  if (path === "pages/money/pay") {
    fetchOrderPrimaryProduct(currentHashParams().get("orderId"));
  }
  maybeShowPendingPaySimilar();
}

window.addEventListener("hashchange", () => setTimeout(observeRoute, 80));
window.addEventListener("load", () => setTimeout(observeRoute, 300));
document.addEventListener("click", markPaySimilarPending, true);
setInterval(observeRoute, 900);
