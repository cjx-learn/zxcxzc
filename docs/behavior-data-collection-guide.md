# 用户行为数据采集说明

## 1. 采集目标

本项目的用户行为分析围绕电商常见转化路径展开，核心目标是把用户在商城中的浏览、搜索、收藏、加购、下单、支付等行为统一沉淀为行为事件，再用于用户画像、商品画像、活跃度分析和推荐算法。

## 2. 行为事件类型

| 行为类型 | 中文含义 | 触发场景 | 分析价值 |
| --- | --- | --- | --- |
| `view` | 浏览商品 | 用户打开商品详情页或商品卡片曝光 | 反映初步兴趣 |
| `search` | 搜索商品 | 用户输入关键词并发起搜索 | 反映主动需求 |
| `fav` | 收藏商品 | 用户收藏商品 | 反映较强兴趣 |
| `cart` | 加入购物车 | 用户点击加入购物车 | 反映购买意向 |
| `order` | 下单 | 用户提交订单 | 反映转化行为 |
| `pay` | 支付 | 用户完成支付 | 反映最终购买 |

## 3. 推荐事件字段

课程作业当前已具备行为分析能力。若继续向真实业务系统完善，建议行为事件表保留以下字段：

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户 ID |
| `product_id` | 商品 ID |
| `category_id` | 商品分类 ID |
| `event_type` | 行为类型 |
| `event_time` | 行为发生时间 |
| `keyword` | 搜索关键词，仅搜索行为需要 |
| `session_id` | 会话 ID，用于串联一次访问 |
| `source_channel` | 来源渠道，如 H5、PC、APP、活动页 |
| `device_id` | 设备标识，可用于游客行为归并 |
| `trace_id` | 链路追踪 ID，便于排查数据问题 |

## 4. 行为权重

推荐训练和画像计算中，行为强度建议按转化深度加权：

```text
view/search = 1
fav = 3
cart = 4
order/pay = 5
```

权重含义是：支付、下单、加购比单纯浏览更能说明用户兴趣。

## 5. 当前实现与不足

当前项目已经实现了用户画像、商品画像、RFM 活跃度、推荐结果和算法评估展示，并已在 `mall-analytics` 中补充 HTTP 行为上报接口：

```text
POST /mall-analytics/analytics/event
```

请求示例：

```json
{
  "userId": 1,
  "productId": 37,
  "categoryId": 19,
  "eventType": "view",
  "sessionId": "h5-session-001",
  "sourcePage": "product-detail",
  "deviceType": "h5",
  "eventTime": "2026-07-07T16:00:00"
}
```

返回示例：

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

接口会校验：

- `userId` 不能为空。
- `eventType` 必须是 `view/search/fav/cart/order/pay` 之一。
- 非搜索行为必须包含 `productId`。
- 搜索行为必须包含 `keyword` 或 `productId`。

当前仍有以下不足：

- 用户端 H5 首页商品卡片点击已接入 `view` 行为上报。
- 搜索、收藏、加购、下单、支付等更深层行为还需要继续接入该接口。
- 没有数据去重、延迟补偿和异常行为过滤。
- 推荐训练仍以离线脚本为主。

## 6. 后续完善方向

后续可按以下路径增强：

1. 在用户端 H5 和管理端关键操作中统一埋点。
2. 后端提供 `behavior/event` 上报接口。
3. MySQL 保存原始行为事件。
4. 定时任务每日重建画像和推荐结果。
5. 引入 Kafka、Flink、ClickHouse 或 Doris 后升级为实时数仓链路。
