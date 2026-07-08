# 数据字典

本文档用于课程报告和答辩说明，描述“电商用户行为分析与商品推荐平台”的核心数据表、字段含义、数据来源和计算逻辑。

## 1. 数据来源概览

| 数据来源 | 说明 | 使用位置 |
| --- | --- | --- |
| mall-swarm 原业务库 | 商品、分类、会员、订单等基础业务数据 | 商品分析、用户画像、推荐展示 |
| 用户端 H5 行为埋点 | 浏览、搜索、收藏、加购、下单、支付等事件 | `user_behavior_event` |
| 淘宝 UserBehavior 抽样数据 | 用于离线训练 ItemCF、NCF、DeepFM | `ml-recommend/data/sampled_events.csv` |
| 离线训练输出 SQL | 推荐结果和算法评估结果 | `recommend_result`、`recommend_evaluation` |

## 2. 核心业务基础表

这些表来自 mall-swarm 原项目，本次改造主要读取，不改变其主体结构。

| 表名 | 作用 | 关键字段 |
| --- | --- | --- |
| `pms_product` | 商品基础信息 | `id`、`name`、`pic`、`price`、`product_sn`、`product_category_id`、`publish_status`、`delete_status` |
| `pms_product_category` | 商品分类信息 | `id`、`name`、`parent_id`、`level`、`sort`、`show_status` |
| `ums_member` | 会员信息 | `id`、`username` |
| `oms_order` | 订单信息 | `id`、`member_id`、`pay_amount`、`payment_time`、`create_time` |

## 3. 用户行为事件表：`user_behavior_event`

### 3.1 表作用

`user_behavior_event` 是本项目最核心的原始行为表。它把用户在商城中的不同动作统一为事件流，后续用户画像、商品画像、热度统计、转化漏斗和推荐算法都基于该表计算。

### 3.2 字段说明

| 字段 | 类型建议 | 含义 | 示例 |
| --- | --- | --- | --- |
| `id` | BIGINT | 行为事件主键 | `10001` |
| `user_id` | BIGINT | 用户 ID，对应 `ums_member.id` | `1` |
| `session_id` | VARCHAR | 会话 ID，用于统计访问会话 | `h5-u1-20260708` |
| `product_id` | BIGINT | 商品 ID，对应 `pms_product.id`；搜索行为可为空 | `37` |
| `category_id` | BIGINT | 商品分类 ID，对应 `pms_product_category.id` | `19` |
| `event_type` | VARCHAR | 行为类型：`view/search/fav/cart/order/pay` | `view` |
| `keyword` | VARCHAR | 搜索关键词，主要用于 `search` 行为 | `手机` |
| `source_page` | VARCHAR | 行为产生页面 | `/pages/index/index` |
| `device_type` | VARCHAR | 终端类型 | `h5`、`pc` |
| `ip` | VARCHAR | 客户端 IP，可用于地域或异常分析 | `10.0.8.1` |
| `user_agent` | VARCHAR | 浏览器或客户端标识 | `Mozilla/5.0` |
| `event_time` | DATETIME | 行为发生时间 | `2026-07-08 21:00:00` |
| `event_date` | DATE | 行为日期，便于按天聚合 | `2026-07-08` |

### 3.3 行为类型含义

| 行为类型 | 中文含义 | 权重解释 |
| --- | --- | --- |
| `view` | 浏览 | 弱兴趣，说明商品被曝光 |
| `search` | 搜索 | 主动需求，说明用户有明确意图 |
| `fav` | 收藏 | 中强兴趣 |
| `cart` | 加购 | 强购买意向 |
| `order` | 下单 | 转化行为 |
| `pay` | 支付 | 最强转化行为 |

## 4. 用户画像表：`user_profile`

### 4.1 表作用

`user_profile` 是根据 `user_behavior_event` 聚合生成的用户画像表，用于全体用户画像、单用户画像、活跃用户 TOP 和个性化推荐。

### 4.2 字段说明

| 字段 | 含义 | 计算来源 |
| --- | --- | --- |
| `user_id` | 用户 ID | `user_behavior_event.user_id` |
| `view_count` | 浏览次数 | `event_type='view'` 的数量 |
| `search_count` | 搜索次数 | `event_type='search'` 的数量 |
| `fav_count` | 收藏次数 | `event_type='fav'` 的数量 |
| `cart_count` | 加购次数 | `event_type='cart'` 的数量 |
| `order_count` | 下单次数 | `event_type='order'` 的数量 |
| `pay_count` | 支付次数 | `event_type='pay'` 的数量 |
| `active_days` | 活跃天数 | 用户有行为的不同日期数 |
| `favorite_category_id` | 偏好分类 ID | 行为得分最高的分类 |
| `favorite_category_score` | 偏好分类得分 | 该分类下行为按权重累计 |
| `last_active_time` | 最近活跃时间 | 用户最新行为时间 |
| `user_level` | 用户等级 | 根据行为强度和购买行为分层 |
| `update_time` | 画像更新时间 | 重建画像任务写入 |

### 4.3 RFM 活跃度说明

活跃用户 TOP 使用 RFM 综合分排序：

```text
RFM = R * 40% + F * 40% + M * 20%
```

| 指标 | 含义 | 当前评分口径 |
| --- | --- | --- |
| R Recency | 最近活跃时间 | 7 天内 100 分，30 天内 70 分，90 天内 40 分，更早 10 分 |
| F Frequency | 行为频率 | 浏览/搜索 1 分，收藏 2 分，加购 3 分，下单 4 分，支付 5 分，活跃天数额外加权，封顶 100 分 |
| M Monetary | 消费价值 | 支付金额 >=5000 得 100 分，>=1000 得 70 分，大于 0 得 40 分，否则 0 分 |

用户分层：

| 综合分 | 用户类型 |
| --- | --- |
| `>= 80` | 高价值活跃用户 |
| `>= 60` | 活跃用户 |
| `>= 40` | 一般活跃用户 |
| `< 40` | 低活跃用户 |

## 5. 商品画像表：`product_profile`

### 5.1 表作用

`product_profile` 根据行为事件聚合商品热度和转化表现，用于热门商品、商品分析、相似商品推荐和规则推荐。

### 5.2 字段说明

| 字段 | 含义 | 计算来源 |
| --- | --- | --- |
| `product_id` | 商品 ID | `pms_product.id` |
| `category_id` | 商品分类 ID | `pms_product.product_category_id` 或行为事件分类 |
| `view_count` | 浏览次数 | 商品 `view` 行为数量 |
| `search_count` | 搜索次数 | 商品相关 `search` 行为数量 |
| `fav_count` | 收藏次数 | 商品 `fav` 行为数量 |
| `cart_count` | 加购次数 | 商品 `cart` 行为数量 |
| `order_count` | 下单次数 | 商品 `order` 行为数量 |
| `pay_count` | 支付次数 | 商品 `pay` 行为数量 |
| `hot_score` | 商品热度分 | 多行为加权得分 |
| `cart_rate` | 加购率 | `cart_count / view_count` |
| `order_rate` | 下单率 | `order_count / view_count` |
| `pay_rate` | 支付率 | `pay_count / view_count` |
| `update_time` | 商品画像更新时间 | 重建画像任务写入 |

### 5.3 商品热度解释

商品热度不是单纯浏览量，而是综合多种行为：

```text
浏览、搜索 -> 曝光与兴趣
收藏、加购 -> 中强购买意向
下单、支付 -> 转化结果
```

因此，支付和下单行为对热度的贡献应高于浏览行为。

## 6. 推荐结果表：`recommend_result`

### 6.1 表作用

`recommend_result` 存储不同推荐算法生成的 TopN 推荐结果，供用户端 H5 和推荐看板读取。

### 6.2 字段说明

| 字段 | 含义 | 示例 |
| --- | --- | --- |
| `id` | 推荐记录主键 | `1` |
| `user_id` | 用户 ID；全站热门推荐可使用 `0` | `1` |
| `product_id` | 推荐商品 ID | `37` |
| `recommend_score` | 推荐分 | `0.98` |
| `rank_no` | 推荐排序，从 1 开始 | `1` |
| `recommend_type` | 推荐类型 | `hot`、`rule`、`model_itemcf`、`model_ncf`、`model_deepfm` |
| `reason` | 推荐原因 | `基于 DeepFM 特征交叉推荐` |
| `create_time` | 生成时间 | `2026-07-08 21:00:00` |

### 6.3 推荐类型

| 类型 | 说明 |
| --- | --- |
| `hot` | 全站热门推荐，用于未登录或兜底 |
| `rule` | 基于用户偏好分类和商品热度的规则推荐 |
| `model_itemcf` | ItemCF 商品协同过滤推荐 |
| `model_ncf` | NCF 神经协同过滤推荐 |
| `model_deepfm` | DeepFM 深度学习推荐 |

## 7. 推荐评估表：`recommend_evaluation`

### 7.1 表作用

`recommend_evaluation` 保存离线推荐评估结果，用于看板展示算法效果对比。

### 7.2 字段说明

| 字段 | 含义 |
| --- | --- |
| `algorithm` | 算法编码，例如 `model_deepfm` |
| `algorithm_label` | 算法中文或展示名称，例如 `DeepFM` |
| `k_value` | TopK 的 K 值 |
| `evaluated_user_count` | 参与评估的用户数 |
| `hit_user_count` | TopK 中至少命中一个真实兴趣商品的用户数 |
| `hit_rate` | `hit_user_count / evaluated_user_count` |
| `category_hit_rate` | 推荐商品分类命中用户真实兴趣分类的用户比例 |
| `precision_at_k` | 商品 ID 级 Precision@K |
| `category_precision_at_k` | 分类级 Precision@K |
| `category_ndcg_at_k` | 分类级 NDCG@K |
| `recall_at_k` | 商品 ID 级 Recall@K |
| `ndcg_at_k` | 商品 ID 级 NDCG@K |
| `coverage` | 推荐结果覆盖商品比例 |
| `total_hit_count` | 商品 ID 级总命中数 |
| `total_category_hit_count` | 分类级总命中数 |
| `total_recommend_count` | 总推荐条数 |
| `evaluation_note` | 评估说明 |
| `create_time` | 评估生成时间 |

## 8. 离线训练数据：`sampled_events.csv`

### 8.1 文件作用

`ml-recommend/data/sampled_events.csv` 是从淘宝 UserBehavior 数据集中抽样并标准化后的行为数据，用于推荐模型训练和评估。

### 8.2 字段说明

| 字段 | 含义 | 示例 |
| --- | --- | --- |
| `user_id` | 淘宝用户 ID | `1000001` |
| `item_id` | 淘宝商品 ID | `2333346` |
| `category_id` | 淘宝商品分类 ID | `2520771` |
| `event_type` | 标准化行为类型 | `view`、`fav`、`cart`、`pay` |
| `timestamp` | 行为时间戳 | `1511658000` |

行为映射：

| 淘宝原始行为 | 本项目行为 |
| --- | --- |
| `pv` | `view` |
| `fav` | `fav` |
| `cart` | `cart` |
| `buy` | `pay` |

## 9. 数据限制说明

1. 淘宝商品 ID 与 mall-swarm 本地商品 ID 不是天然一致，需要做映射，因此商品 ID 级命中率会偏低。
2. 当前推荐结果以离线训练和 SQL 导入为主，不是实时在线训练。
3. H5 已接入商品浏览 `view` 行为，搜索、收藏、加购、下单、支付行为可以继续按统一接口扩展。
4. 课程答辩时建议重点强调：本项目已跑通完整离线数据链路，并具备向实时数仓和在线推荐扩展的基础。
