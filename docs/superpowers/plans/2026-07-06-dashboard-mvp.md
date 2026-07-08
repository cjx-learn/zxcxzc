# Dashboard MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a public static dashboard for the deployed mall-swarm analytics and recommendation MVP.

**Architecture:** A static HTML page calls the existing `/mall-analytics/` and `/mall-recommend/` reverse-proxy endpoints. It renders KPI cards, behavior funnel, daily event trend, hot products, user recommendations, similar products, and a rebuild action without adding a new backend service.

**Tech Stack:** HTML, CSS, vanilla JavaScript, ECharts via CDN, existing Nginx static hosting.

---

### Task 1: Static Dashboard Page

**Files:**
- Create: `dashboard/index.html`

- [ ] **Step 1: Create a single-file dashboard**

Create `dashboard/index.html` with CSS, API client helpers, fallback table rendering, and ECharts charts.

- [ ] **Step 2: Run local syntax smoke check**

Run: `Select-String -Path dashboard/index.html -Pattern "mall-analytics|mall-recommend|echarts"`

Expected: matches for both backend paths and ECharts.

- [ ] **Step 3: Deploy static assets**

Copy `dashboard/index.html` to `/usr/share/nginx/mall-dashboard/index.html` on the cloud server.

- [ ] **Step 4: Add Nginx route**

Add:

```nginx
location /dashboard/ {
    alias /usr/share/nginx/mall-dashboard/;
    index index.html;
    try_files $uri $uri/ /dashboard/index.html;
}

location = /dashboard {
    return 301 /dashboard/;
}
```

- [ ] **Step 5: Verify**

Run:

```bash
nginx -t && systemctl reload nginx
curl -I http://127.0.0.1:8201/dashboard/
curl -s http://127.0.0.1:8201/mall-analytics/analytics/overview?days=30
curl -s http://127.0.0.1:8201/mall-recommend/recommend/hot?limit=1
```

Expected: Nginx config passes, dashboard returns `200`, analytics and recommendation endpoints return JSON with `code:200`.
