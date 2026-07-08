# 推荐算法训练记录

本文档记录最近一次推荐算法训练、评估和部署情况，便于课程报告和答辩引用。

## 1. 训练目标

本次训练的目标不是只重新跑一遍脚本，而是解决两个演示问题：

1. 原抽样数据规模较小，只有约 2.46 万条行为，模型训练说服力不足。
2. 原模型结果保留淘宝外部用户 ID，用户端使用 mall-swarm 本地用户登录时不一定能看到模型推荐。

因此本次训练做了两点改进：

- 增加活跃用户抽样策略，从完整淘宝 UserBehavior 数据集中抽取更密集的用户行为样本。
- 导出推荐 SQL 时增加本地用户 ID 映射，让本地用户 `1,3,4,5,6,7,8,9,10,11,12,13` 都能看到 DeepFM、NCF、ItemCF 推荐结果。

## 2. 数据集

原始数据：

```text
E:\大数据实战\datasets\taobao\UserBehavior.csv
```

原始字段：

```text
user_id,item_id,category_id,behavior_type,timestamp
```

行为映射：

| 淘宝行为 | 本项目行为 |
| --- | --- |
| `pv` | `view` |
| `fav` | `fav` |
| `cart` | `cart` |
| `buy` | `pay` |

## 3. 抽样方式

本次新增 `--strategy active` 活跃用户抽样策略：

```bash
python ml-recommend/sample_taobao.py ^
  --input E:\大数据实战\datasets\taobao\UserBehavior.csv ^
  --output-dir ml-recommend\data ^
  --strategy active ^
  --max-users 1200 ^
  --max-items 5000 ^
  --max-events 120000 ^
  --recent-days 9 ^
  --start-ts 1511539200 ^
  --end-ts 1512316799
```

抽样结果：

| 指标 | 数值 |
| --- | ---: |
| 用户数 | 1,195 |
| 商品数 | 4,952 |
| 行为数 | 89,123 |
| 浏览行为 | 83,797 |
| 收藏行为 | 2,023 |
| 加购行为 | 2,821 |
| 支付行为 | 482 |

说明：淘宝公开行为数据中浏览行为占比天然较高，收藏、加购、支付行为相对少，因此推荐评估时商品 ID 级命中率会偏低。

## 4. 训练命令

```bash
python ml-recommend/run_recommend_pipeline.py ^
  --events ml-recommend\data\sampled_events.csv ^
  --output-dir ml-recommend\output ^
  --train-ratio 0.8 ^
  --topn 10 ^
  --epochs 8 ^
  --negative-ratio 4 ^
  --max-users 60
```

训练集和测试集按用户时间顺序切分：

```text
每个用户前 80% 行为 -> 训练集
每个用户后 20% 行为 -> 测试集
```

正样本行为：

```text
fav, cart, pay
```

## 5. 模型训练结果

### 5.1 ItemCF

ItemCF 基于用户共同行为构建商品相似度，适合解释“与历史商品协同相似”。

输出：

```text
ml-recommend/output/itemcf_recommendations.csv
```

推荐数量：

```text
1195 users * Top10 = 11950 recommendations
```

### 5.2 NCF

NCF 使用用户 Embedding 和商品 Embedding，通过神经网络预测兴趣概率。

训练日志：

| Epoch | Avg Loss |
| --- | ---: |
| 1 | 0.263891 |
| 2 | 0.200510 |
| 3 | 0.197619 |
| 4 | 0.195656 |
| 5 | 0.193633 |
| 6 | 0.191929 |
| 7 | 0.190591 |
| 8 | 0.189628 |

说明：loss 持续下降，说明模型在训练集上学到了用户和商品的隐式匹配关系。

### 5.3 DeepFM

DeepFM 自动学习用户、商品、分类、活跃度、热度和时间上下文之间的特征交叉。

训练日志：

| Epoch | Avg Loss |
| --- | ---: |
| 1 | 2.431370 |
| 2 | 1.188329 |
| 3 | 0.943852 |
| 4 | 0.785494 |
| 5 | 0.665725 |
| 6 | 0.575180 |
| 7 | 0.517657 |
| 8 | 0.475380 |

说明：DeepFM loss 下降明显，说明特征交叉模型完成了有效训练。但由于淘宝商品 ID 与本地 mall 商品 ID 存在映射，商品 ID 级离线命中率不一定能体现它的全部价值。

## 6. 离线评估结果

评估用户数：537，TopK：10。

| 算法 | HitRate@10 | CategoryHitRate | Precision@10 | CategoryPrecision@10 | CategoryNDCG@10 | Recall@10 | NDCG@10 | Coverage |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| DeepFM | 0.000000 | 0.297952 | 0.000000 | 0.056611 | 0.169980 | 0.000000 | 0.000000 | 0.231624 |
| 热门推荐 | 0.027933 | 0.443203 | 0.002793 | 0.092179 | 0.312571 | 0.015846 | 0.007647 | 0.003635 |
| NCF | 0.014898 | 0.441341 | 0.001490 | 0.087896 | 0.294352 | 0.010397 | 0.006360 | 0.037157 |
| ItemCF | 0.014898 | 0.482309 | 0.001490 | 0.152700 | 0.510360 | 0.009732 | 0.004307 | 0.357229 |

## 7. 结果解释

本次结果有三个重要结论：

1. ItemCF 在分类命中率、分类 Precision 和分类 NDCG 上表现最好，说明协同过滤对“兴趣分类”捕捉较强。
2. 热门推荐的商品 ID 级 HitRate 较高，但 Coverage 很低，说明它集中推荐少数热门商品，个性化和多样性不足。
3. DeepFM 的 Coverage 高于热门推荐和 NCF，说明它推荐结果更分散，但商品 ID 级命中为 0，主要受外部淘宝商品到本地商品映射影响。

答辩时建议这样表述：

```text
由于训练数据来自淘宝公开数据集，而展示商品来自 mall-swarm 本地商品库，二者需要映射。
因此商品 ID 级 Precision、Recall、NDCG 会受到映射误差影响。
为了更合理地解释推荐效果，我们同时展示分类命中率、分类 NDCG 和覆盖率。
```

## 8. 部署结果

已将以下 SQL 导入云服务器 MySQL：

```text
/tmp/model_recommend_result.sql
/tmp/model_ncf_recommend_result.sql
/tmp/model_deepfm_recommend_result.sql
/tmp/recommend_evaluation.sql
```

导入后推荐结果数量：

| 推荐类型 | 条数 | 用户数 |
| --- | ---: | ---: |
| `model_deepfm` | 120 | 12 |
| `model_ncf` | 120 | 12 |
| `model_itemcf` | 120 | 12 |

本地 mall 用户映射：

```text
1,3,4,5,6,7,8,9,10,11,12,13
```

每个本地用户拥有：

```text
DeepFM Top10 + NCF Top10 + ItemCF Top10
```

## 9. 线上接口验证

公网接口：

```text
http://114.55.170.17:8201/mall-recommend/recommend/user/1?type=model_deepfm&limit=3
```

验证结果：

- HTTP 返回 `code=200`。
- `userId=1` 能返回 DeepFM 推荐商品。
- 推荐原因中文正常，例如：

```text
基于淘宝行为数据训练的DeepFM特征交叉推荐；DeepFM自动学习用户、商品、分类、活跃度、热度和时间上下文的特征交叉
```

推荐评估接口：

```text
http://114.55.170.17:8201/mall-recommend/recommend/evaluate
```

验证结果：

- HTTP 返回 `code=200`。
- 返回 DeepFM、NCF、ItemCF、热门推荐 4 类算法指标。

## 10. 后续优化方向

1. 继续扩大活跃用户样本，提高正样本数量。
2. 增加 AUC、LogLoss 等更适合二分类排序模型的指标。
3. 引入更真实的本地商城行为数据，减少淘宝商品映射误差。
4. 把训练 loss 和评估指标版本写入数据库，形成模型版本管理。
5. 将离线训练改为定时任务，自动更新推荐结果。
