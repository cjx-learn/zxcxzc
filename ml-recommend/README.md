# ml-recommend

淘宝 UserBehavior 数据集离线推荐训练模块。

## 数据集

将天池淘宝用户行为数据放到：

```text
E:\大数据实战\datasets\taobao\UserBehavior.csv
```

原始字段无表头：

```text
user_id,item_id,category_id,behavior_type,timestamp
```

行为映射：

| 淘宝行为 | 系统行为 | 权重 |
| --- | --- | --- |
| pv | view | 1 |
| fav | fav | 3 |
| cart | cart | 4 |
| buy | pay | 5 |

## 本地运行

先抽样：

```bash
python ml-recommend/sample_taobao.py ^
  --input E:\大数据实战\datasets\taobao\UserBehavior.csv ^
  --output-dir ml-recommend\data ^
  --max-users 5000 ^
  --max-items 10000 ^
  --max-events 300000 ^
  --recent-days 9 ^
  --start-ts 1511539200 ^
  --end-ts 1512316799
```

天池原始数据中可能存在极少量异常时间戳。建议显式使用 2017-11-25 到 2017-12-03 的时间范围：

```text
1511539200 - 1512316799
```

训练 ItemCF：

```bash
python ml-recommend/train_itemcf.py ^
  --events ml-recommend\data\sampled_events.csv ^
  --output ml-recommend\output\itemcf_recommendations.csv ^
  --topn 10
```

训练 BPR 矩阵分解：

```bash
python ml-recommend/train_bpr.py ^
  --events ml-recommend\data\sampled_events.csv ^
  --output ml-recommend\output\bpr_recommendations.csv ^
  --topn 10 ^
  --factors 24 ^
  --epochs 8
```

训练 NCF 神经协同过滤：

```bash
python ml-recommend/train_ncf.py ^
  --events ml-recommend\data\sampled_events.csv ^
  --output ml-recommend\output\ncf_recommendations.csv ^
  --model-output ml-recommend\output\ncf_model.pt ^
  --topn 10 ^
  --embedding-dim 32 ^
  --epochs 6
```

训练 DeepFM 特征交叉模型：

```bash
python ml-recommend/train_deepfm.py ^
  --events ml-recommend\data\sampled_events.csv ^
  --output ml-recommend\output\deepfm_recommendations.csv ^
  --model-output ml-recommend\output\deepfm_model.pt ^
  --topn 10 ^
  --embedding-dim 16 ^
  --epochs 6
```

导出写入 mall-swarm 的 SQL：

```bash
python ml-recommend/export_recommend_sql.py ^
  --recommendations ml-recommend\output\itemcf_recommendations.csv ^
  --output ml-recommend\output\model_recommend_result.sql ^
  --mall-product-ids 37,26,46,45,44,43,42 ^
  --mall-user-ids 12,1,2,3,4 ^
  --recommend-type model_itemcf
```

## 部署到服务器

上传 SQL 后执行：

```bash
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -proot mall < /opt/mall-swarm/ml-recommend/output/model_recommend_result.sql
```

推荐类型为：

```text
model_itemcf
model_bpr
model_ncf
model_deepfm
```

看板会显示为原始类型名；报告中可解释为“基于淘宝行为数据训练的 ItemCF 模型推荐”。
