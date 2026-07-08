# 接口测试报告

本文档记录课程答辩前需要重点验证的接口。线上演示环境统一经过 Nginx 网关访问：

```text
http://114.55.170.17:8201
```

本地或服务器内部调试时，可直接访问服务端口：

| 服务 | 默认端口 | 说明 |
| --- | --- | --- |
| `mall-analytics` | `8206` | 用户行为分析服务 |
| `mall-recommend` | `8207` | 商品推荐服务 |

## 1. 行为采集接口

### 1.1 接口信息

```text
POST /mall-analytics/analytics/event
```

### 1.2 请求示例

```bash
curl -X POST "http://114.55.170.17:8201/mall-analytics/analytics/event" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "sessionId": "demo-session-001",
    "productId": 37,
    "categoryId": 19,
    "eventType": "view",
    "sourcePage": "/pages/index/index",
    "deviceType": "h5"
  }'
```

### 1.3 预期返回

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "saved": true,
    "eventType": "view",
    "userId": 1,
    "productId": 37
  }
}
```

### 1.4 校验规则

| 规则 | 说明 |
| --- | --- |
| `userId` 必填 | 每条行为必须能关联到用户 |
| `eventType` 必须合法 | 支持 `view/search/fav/cart/order/pay` |
| 非搜索行为必须有 `productId` | 浏览、收藏、加购、下单、支付都要关联商品 |
| 搜索行为必须有 `keyword` 或 `productId` | 用于分析主动搜索需求 |

## 2. 行为概览接口

### 2.1 接口信息

```text
GET /mall-analytics/analytics/overview?days=7
```

### 2.2 请求示例

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/overview?days=7"
```

### 2.3 关键返回字段

| 字段 | 含义 |
| --- | --- |
| `eventCount` | 行为事件总数 |
| `userCount` | 有行为的用户数 |
| `sessionCount` | 会话数 |
| `productCount` | 涉及商品数 |
| `viewCount` | 浏览行为数 |
| `searchCount` | 搜索行为数 |
| `favCount` | 收藏行为数 |
| `cartCount` | 加购行为数 |
| `orderCount` | 下单行为数 |
| `payCount` | 支付行为数 |

## 3. 趋势与漏斗接口

### 3.1 每日趋势

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/daily-trend?days=7"
```

用于看板折线图，按日期和行为类型统计事件数。

### 3.2 转化漏斗

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/funnel?days=7"
```

用于展示：

```text
浏览 -> 收藏 -> 加购 -> 下单 -> 支付
```

## 4. 用户画像接口

### 4.1 全体用户画像

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/users/overview?days=90"
```

关键返回内容：

| 字段 | 含义 |
| --- | --- |
| `summary` | 用户规模、高价值用户数、平均活跃天数 |
| `levelDistribution` | 用户等级分布 |
| `favoriteCategoryDistribution` | 用户偏好分类分布 |
| `activeUsers` | 活跃用户 TOP |

### 4.2 活跃用户 TOP

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/users/active?days=90&limit=20"
```

关键字段：

| 字段 | 含义 |
| --- | --- |
| `userId` | 用户 ID |
| `username` | 用户名 |
| `rfmScore` | RFM 综合分 |
| `recencyScore` | 最近活跃得分 |
| `frequencyScore` | 行为频率得分 |
| `monetaryScore` | 消费价值得分 |
| `rfmLevel` | RFM 用户等级 |

### 4.3 单用户画像

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/user-profile/1?days=30"
```

用于点击左侧活跃用户后展示个人画像。

## 5. 商品分析接口

### 5.1 商品搜索

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/products/search?keyword=手机&limit=10"
```

支持：

- 商品 ID 精确查询。
- 商品名称关键词查询。
- 商品副标题查询。
- 商品关键词查询。
- 商品货号查询。

若没有匹配商品，前端显示“无商品”。

### 5.2 单商品分析

```bash
curl "http://114.55.170.17:8201/mall-analytics/analytics/product/37?days=30"
```

关键返回内容：

| 字段 | 含义 |
| --- | --- |
| `productName` | 商品名称 |
| `productPic` | 商品图片 |
| `hotScore` | 商品热度分 |
| `cartRate` | 加购率 |
| `orderRate` | 下单率 |
| `payRate` | 支付率 |
| `eventSummary` | 近 30 天行为汇总 |
| `recentEvents` | 最近行为明细 |

## 6. 画像与推荐重建接口

### 6.1 接口信息

```text
POST /mall-analytics/analytics/rebuild
```

### 6.2 请求示例

```bash
curl -X POST "http://114.55.170.17:8201/mall-analytics/analytics/rebuild"
```

### 6.3 预期返回

```json
{
  "code": 200,
  "data": {
    "userProfileCount": 6,
    "productProfileCount": 11,
    "recommendResultCount": 722,
    "profileSql": "/opt/mall-swarm/data-analysis/build_profiles.sql",
    "recommendSql": "/opt/mall-swarm/data-analysis/build_recommend_result.sql",
    "runTime": "2026-07-08T21:00:00"
  }
}
```

答辩说明：该接口会执行画像构建 SQL 和推荐结果构建 SQL，然后返回用户画像、商品画像、推荐结果的当前数量。

## 7. 推荐服务接口

### 7.1 全站热门推荐

```bash
curl "http://114.55.170.17:8201/mall-recommend/recommend/hot?limit=10"
```

用于未登录用户和推荐兜底。

### 7.2 用户个性化推荐

```bash
curl "http://114.55.170.17:8201/mall-recommend/recommend/user/1?type=model_deepfm&limit=10"
```

`type` 可选：

| 类型 | 说明 |
| --- | --- |
| `rule` | 规则推荐 |
| `model_itemcf` | ItemCF 推荐 |
| `model_ncf` | NCF 推荐 |
| `model_deepfm` | DeepFM 推荐 |

如果指定用户没有该类型推荐结果，服务会自动返回热门推荐作为兜底。

### 7.3 相似商品推荐

```bash
curl "http://114.55.170.17:8201/mall-recommend/recommend/similar/37?limit=10"
```

用于商品分析页面，根据同分类商品和商品画像热度返回相似商品。

### 7.4 推荐算法评估

```bash
curl "http://114.55.170.17:8201/mall-recommend/recommend/evaluate"
```

关键字段：

| 字段 | 含义 |
| --- | --- |
| `algorithm` | 算法编码 |
| `algorithmLabel` | 算法名称 |
| `k` | TopK |
| `evaluatedUserCount` | 评估用户数 |
| `hitRate` | HitRate@K |
| `precisionAtK` | Precision@K |
| `recallAtK` | Recall@K |
| `ndcgAtK` | NDCG@K |
| `categoryHitRate` | 分类命中率 |
| `coverage` | 覆盖率 |

## 8. 前端页面自测

| 页面 | 地址 | 检查点 |
| --- | --- | --- |
| 用户端 H5 | `http://114.55.170.17:8201/app/` | 首页商品图片、猜你喜欢、商品跳转 |
| 分析看板 | `http://114.55.170.17:8201/dashboard/` | 核心指标、用户画像、商品分析、推荐结果、算法评估 |
| 管理端 | `http://114.55.170.17:8201/` | 商品、订单、会员等原商城功能 |

## 9. 答辩前接口验收清单

- 行为上报接口返回 `code=200` 且 `saved=true`。
- 行为概览接口有非零 `eventCount`。
- 活跃用户接口返回按 `rfmScore` 排序的用户列表。
- 商品搜索输入不存在关键词时，前端显示“无商品”。
- 单商品分析能显示热度、转化率和最近行为。
- 重建画像接口返回 `userProfileCount`、`productProfileCount`、`recommendResultCount`。
- DeepFM 个性化推荐接口返回商品图片、名称、价格、推荐分和推荐原因。
- 推荐评估接口返回 DeepFM、NCF、ItemCF、热门推荐的指标。
