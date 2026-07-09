const RECOMMEND_SCENES = {
  search: {
    path: "pages/product/search",
    type: "model_ncf",
    title: "\u641c\u7d22\u7075\u611f\u63a8\u8350",
    note: "NCF\u6839\u636e\u4f60\u7684\u6f5c\u5728\u5174\u8da3\u63a8\u8350\uff0c\u9002\u5408\u53d1\u73b0\u8fd8\u6ca1\u4e3b\u52a8\u641c\u7d22\u5230\u7684\u5546\u54c1\u3002",
    anchor: ".container"
  },
  cart: {
    path: "pages/cart/cart",
    type: "model_itemcf",
    title: "\u76f8\u4f3c\u642d\u914d\u63a8\u8350",
    note: "ItemCF\u6839\u636e\u4f60\u7684\u6d4f\u89c8\u3001\u6536\u85cf\u3001\u52a0\u8d2d\u5546\u54c1\u627e\u76f8\u4f3c\u548c\u53ef\u642d\u914d\u5546\u54c1\u3002",
    anchor: ".container"
  },
  order: {
    path: "pages/order/order",
    type: "rule",
    title: "\u590d\u8d2d\u4f18\u9009\u63a8\u8350",
    note: "\u89c4\u5219\u63a8\u8350\u7ed3\u5408\u504f\u597d\u5206\u7c7b\u548c\u70ed\u95e8\u5546\u54c1\uff0c\u9002\u5408\u8ba2\u5355\u540e\u7684\u590d\u8d2d\u4e0e\u8865\u5145\u8d2d\u4e70\u3002",
    anchor: ".list-scroll-content"
  }
};

const RECOMMEND_LIMIT = 6;
let lastSceneKey = "";
let pendingTimer = null;
let isRendering = false;
const cache = new Map();

function currentHashPath() {
  return decodeURIComponent((location.hash || "").replace(/^#\/?/, "").split("?")[0]);
}

function currentScene() {
  const path = currentHashPath();
  return Object.entries(RECOMMEND_SCENES).find(([, scene]) => path === scene.path || path.startsWith(scene.path + "?"));
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

function getToken() {
  const direct = storageValue("token") || storageValue("Authorization");
  if (direct) return direct;
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const value = localStorage.getItem(localStorage.key(i));
      if (typeof value === "string") {
        const match = value.match(/Bearer\s+[A-Za-z0-9._\-]+/);
        if (match) return match[0];
      }
    }
  } catch (error) {}
  return "";
}

async function fetchCurrentUserId() {
  const storedId = getStoredUserId();
  if (storedId) return storedId;
  const token = getToken();
  if (!token) return null;
  try {
    const response = await fetch("/mall-portal/sso/info", {
      headers: { Authorization: token, "source-client": "miniapp" }
    });
    const json = await response.json();
    return findIdDeep(json && json.data);
  } catch (error) {
    return null;
  }
}

function imageUrl(path) {
  if (!path) return "./static/errorImage.jpg";
  if (/^https?:\/\//.test(path)) return path;
  return path.startsWith("/") ? path : "/" + path;
}

function productId(item) {
  return item.productId || item.id;
}

function productName(item) {
  return item.productName || item.name || "\u4e3a\u4f60\u63a8\u8350";
}

function productPrice(item) {
  const value = item.productPrice ?? item.price;
  return value == null ? "" : Number(value).toFixed(2);
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

function mergeUniqueProducts(primary, fallback) {
  const result = [];
  const seen = new Set();
  [...(primary || []), ...(fallback || [])].forEach(item => {
    const id = productId(item);
    if (!id || seen.has(String(id))) return;
    seen.add(String(id));
    result.push(item);
  });
  return result.slice(0, RECOMMEND_LIMIT);
}

async function fetchHotProducts() {
  const hot = await fetch(`/mall-recommend/recommend/hot?limit=${RECOMMEND_LIMIT}`, { headers: { "source-client": "miniapp" } });
  const hotJson = await hot.json();
  return hotJson && hotJson.code === 200 && Array.isArray(hotJson.data) ? hotJson.data : [];
}

async function requestRecommendations(scene) {
  const userId = await fetchCurrentUserId();
  const cacheKey = `${scene.type}:${userId || "hot"}`;
  if (cache.has(cacheKey)) return cache.get(cacheKey);
  let url = `/mall-recommend/recommend/hot?limit=${RECOMMEND_LIMIT}`;
  if (userId) {
    url = `/mall-recommend/recommend/user/${encodeURIComponent(userId)}?type=${encodeURIComponent(scene.type)}&limit=${RECOMMEND_LIMIT}`;
  }
  try {
    const response = await fetch(url, { headers: { "source-client": "miniapp" } });
    const json = await response.json();
    let rows = json && json.code === 200 && Array.isArray(json.data) ? json.data : [];
    if (rows.length < RECOMMEND_LIMIT) {
      rows = mergeUniqueProducts(rows, await fetchHotProducts());
    }
    cache.set(cacheKey, rows.slice(0, RECOMMEND_LIMIT));
    return cache.get(cacheKey);
  } catch (error) {
    console.warn("context recommendation failed", error);
    return [];
  }
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
  const url = `/pages/product/product?id=${encodeURIComponent(id)}&_t=${Date.now()}&_from=cartRecommend`;
  const router = appRouter();
  if (router) {
    router.push(url);
    [0, 80, 250, 600].forEach(delay => {
      setTimeout(() => window.dispatchEvent(new CustomEvent("mall-product-id-change", { detail: { id: Number(id) || id } })), delay);
    });
    return;
  }
  console.warn("recommend router unavailable");
}

function createSection(scene, rows) {
  const section = document.createElement("section");
  section.className = "mall-context-recommend";
  section.dataset.recommendType = scene.type;
  section.innerHTML = `
    <div class="mall-context-head">
      <div>
        <div class="mall-context-title">${scene.title}</div>
        <div class="mall-context-note">${scene.note}</div>
      </div>
      <span class="mall-context-badge">${scene.type.replace("model_", "").toUpperCase()}</span>
    </div>
    <div class="mall-context-grid">
      ${rows.map(item => `
        <article class="mall-context-card" data-product-id="${productId(item) || ""}">
          <img class="mall-context-img" src="${escapeHtml(imageUrl(item.productPic || item.pic))}" alt="" loading="lazy">
          <div class="mall-context-name">${escapeHtml(productName(item))}</div>
          <div class="mall-context-meta">${escapeHtml(item.reason ? item.reason : "\u4e3a\u4f60\u63a8\u8350")}</div>
          <div class="mall-context-price">${escapeHtml(productPrice(item))}</div>
        </article>
      `).join("")}
    </div>`;
  section.querySelectorAll(".mall-context-card").forEach(card => {
    card.addEventListener("click", event => {
      event.preventDefault();
      openProduct(card.dataset.productId);
    });
  });
  return section;
}

function directRecommendChild(anchor) {
  return anchor && anchor.querySelector ? anchor.querySelector(".mall-context-recommend") : null;
}

function findAnchors(scene) {
  if (scene.path === "pages/cart/cart") {
    const empty = document.querySelector(".container > .empty");
    if (empty) return [empty];
  }
  if (scene.path === "pages/order/order") {
    const content = document.querySelector(".content");
    if (content) return [content];
  }
  const nodes = Array.from(document.querySelectorAll(scene.anchor));
  return nodes.slice(0, 1);
}

async function renderScene(sceneKey, scene) {
  const anchors = findAnchors(scene);
  if (!anchors.length) return false;
  const existing = anchors.every(anchor => directRecommendChild(anchor));
  if (existing && document.documentElement.dataset.contextRecommend === sceneKey) return true;
  const rows = await requestRecommendations(scene);
  if (!rows.length) return false;
  anchors.forEach(anchor => {
    const old = directRecommendChild(anchor);
    const section = createSection(scene, rows);
    if (scene.path === "pages/order/order") {
      section.classList.add("mall-context-recommend-order");
    }
    if (old) {
      old.replaceWith(section);
    } else {
      anchor.appendChild(section);
    }
  });
  document.documentElement.dataset.contextRecommend = sceneKey;
  return true;
}

function scheduleRender() {
  if (isRendering) return;
  clearTimeout(pendingTimer);
  pendingTimer = setTimeout(async () => {
    if (isRendering) return;
    isRendering = true;
    try {
      const entry = currentScene();
      if (!entry) {
        lastSceneKey = "";
        document.documentElement.dataset.contextRecommend = "";
        document.querySelectorAll(".mall-context-recommend").forEach(el => el.remove());
        return;
      }
      const [sceneKey, scene] = entry;
      lastSceneKey = sceneKey;
      for (let attempt = 0; attempt < 8; attempt++) {
        if (sceneKey !== lastSceneKey) return;
        if (await renderScene(sceneKey, scene)) return;
        await new Promise(resolve => setTimeout(resolve, 250));
      }
    } finally {
      isRendering = false;
    }
  }, 120);
}

window.addEventListener("hashchange", scheduleRender);
window.addEventListener("pageshow", scheduleRender);
window.addEventListener("load", scheduleRender);
new MutationObserver(() => {
  const entry = currentScene();
  if (!entry) return;
  const [, scene] = entry;
  if (!findAnchors(scene).some(anchor => directRecommendChild(anchor))) {
    scheduleRender();
  }
}).observe(document.documentElement, { childList: true, subtree: true });
scheduleRender();
