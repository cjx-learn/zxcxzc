# 数据链路与离线推荐流水线说明

## 1. 总体链路

本项目目前形成了两条主链路：用户行为分析链路和推荐算法训练链路。

```text
用户行为 / 淘宝抽样数据
 -> 行为事件建模
 -> 用户画像 / 商品画像
 -> 推荐模型训练
 -> 推荐结果入库
 -> 推荐效果评估
 -> 看板与用户端展示
```

## 2. 用户行为分析链路

```text
行为事件表
 -> 用户画像 user_profile
 -> 商品画像 product_profile
 -> mall-analytics 接口
 -> dashboard 看板
```

看板展示内容包括：

- 全体用户画像
- 单用户画像
- 活跃用户 TOP
- RFM 活跃度分析
- 偏好分类分析
- 商品热度与转化率
- 相似商品推荐

## 3. 推荐训练链路

当前推荐训练使用淘宝 UserBehavior 数据集抽样结果。

```text
UserBehavior.csv
 -> sample_taobao.py
 -> sampled_events.csv
 -> train_itemcf.py
 -> train_ncf.py
 -> train_deepfm.py
 -> evaluate_recommendations.py
 -> export_recommend_sql.py
 -> recommend_result / recommend_evaluation
```

其中 `sampled_events.csv` 字段为：

```text
user_id,item_id,category_id,event_type,timestamp
```

行为映射：

```text
pv   -> view
fav  -> fav
cart -> cart
buy  -> pay
```

## 4. 时间切分训练

为避免评估时出现数据泄漏或测试商品被提前排除，当前训练脚本已支持时间切分：

```text
每个用户前 80% 行为 -> 训练集
每个用户后 20% 行为 -> 测试集
```

训练命令通过参数控制：

```text
--train-ratio 0.8
```

这样推荐结果可以和测试期真实行为进行 HitRate@K、Precision@K、Recall@K、NDCG@K、分类命中率等指标评估。

## 5. 一键离线流水线

已新增脚本：

```text
ml-recommend/run_recommend_pipeline.py
```

示例命令：

```bash
python ml-recommend/run_recommend_pipeline.py \
  --events ml-recommend/data/sampled_events.csv \
  --output-dir ml-recommend/output \
  --train-ratio 0.8 \
  --epochs 8 \
  --negative-ratio 4
```

该脚本会依次执行：

1. 训练 ItemCF。
2. 训练 NCF。
3. 训练 DeepFM。
4. 计算推荐评估指标。
5. 导出 ItemCF 推荐 SQL。
6. 导出 NCF 推荐 SQL。
7. 导出 DeepFM 推荐 SQL。

## 6. 当前限制

当前链路适合课程答辩和功能演示，但与生产级实时数仓相比仍有差距：

- 数据更新依赖离线脚本。
- 淘宝商品 ID 与 mall 本地商品 ID 需要映射。
- 推荐结果不是实时更新。
- 缺少任务调度平台。
- 缺少数据质量监控、血缘追踪和告警。

## 7. 后续升级方向

后续可逐步升级为：

```text
用户端埋点
 -> Kafka
 -> Flink 实时清洗与聚合
 -> Doris / ClickHouse
 -> 实时看板
 -> 在线特征服务
 -> 推荐召回与排序服务
```

对于课程作业，可以重点说明：本项目当前已完成离线数据链路闭环，具备向实时数仓扩展的基础。
