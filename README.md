# 基于电商用户行为数据的商品推荐与用户行为分析系统

本项目基于开源电商微服务项目 [`macrozheng/mall-swarm`](https://github.com/macrozheng/mall-swarm) 进行二次开发，课程作业主题为：

```text
基于电商用户行为数据的商品推荐与用户行为分析系统设计与实现
```

项目不是只展示原商城功能，而是在原有商品、用户、订单等业务基础上，补充用户行为采集、用户画像、商品画像、推荐算法、算法评估、分析看板和用户端推荐展示，形成一条面向课程答辩的完整数据应用链路。

## 在线演示

| 模块 | 地址 | 说明 |
| --- | --- | --- |
| 管理端 | `http://114.55.170.17:8201/` | mall-swarm 原后台管理入口 |
| 用户端 H5 | `http://114.55.170.17:8201/app/` | 商城用户端，首页接入推荐结果 |
| 分析看板 | `http://114.55.170.17:8201/dashboard/` | 用户行为分析、用户画像、商品分析、推荐结果、算法评估 |
| 行为采集接口 | `POST /mall-analytics/analytics/event` | 采集浏览、搜索、收藏、加购、下单、支付等行为 |
| 推荐服务接口 | `GET /mall-recommend/recommend/*` | 热门推荐、个性化推荐、相似商品推荐、算法评估 |

如果云服务器地址发生变化，请同步更新 `docs/deployment-operations-guide.md` 和本 README。

## 课程要求对应关系

| 课程要求 | 本项目实现 |
| --- | --- |
| 真实行业场景 | 电商用户行为分析与商品推荐 |
| 开源项目二次改造 | 基于 mall-swarm 微服务商城扩展 |
| 数据获取 | 本地商城业务数据、H5 行为埋点、淘宝 UserBehavior 抽样数据 |
| 数据处理 | 行为事件标准化、商品映射、用户画像、商品画像、推荐训练样本构建 |
| 数据存储 | MySQL 存储行为事件、画像、推荐结果、算法评估结果 |
| 计算分析 | RFM 活跃度、商品热度、转化漏斗、ItemCF、NCF、DeepFM |
| 数据服务 | mall-analytics 行为分析服务、mall-recommend 推荐服务 |
| 可视化展示 | dashboard 分析看板、用户端 H5 推荐商品 |
| 可复现材料 | README、数据字典、接口测试报告、部署运维说明、课程交付说明 |

## 系统架构

```text
用户端 H5
  -> 行为埋点 view/search/fav/cart/order/pay
  -> mall-analytics 行为采集接口
  -> user_behavior_event 行为事件表
  -> 用户画像 user_profile / 商品画像 product_profile
  -> 推荐训练与推荐结果 recommend_result
  -> mall-recommend 推荐接口
  -> dashboard 看板 / 用户端 H5 展示
```

离线推荐训练链路：

```text
淘宝 UserBehavior.csv
  -> sample_taobao.py 抽样
  -> sampled_events.csv 标准化行为数据
  -> train_itemcf.py / train_ncf.py / train_deepfm.py
  -> evaluate_recommendations.py 算法评估
  -> export_recommend_sql.py 导出 SQL
  -> recommend_result / recommend_evaluation 入库
```

## 核心功能

### 1. 用户行为采集

系统统一建模以下行为：

| 行为类型 | 含义 | 分析价值 |
| --- | --- | --- |
| `view` | 浏览商品 | 表示曝光和初始兴趣 |
| `search` | 搜索商品 | 表示主动需求 |
| `fav` | 收藏商品 | 表示较强兴趣 |
| `cart` | 加入购物车 | 表示购买意向 |
| `order` | 下单 | 表示转化行为 |
| `pay` | 支付 | 表示最终购买 |

H5 首页商品点击已经接入 `view` 行为上报；其他行为类型可通过统一接口继续扩展。

### 2. 用户画像与活跃度分析

看板将全体用户画像和单用户画像分开展示：

- 全体画像：用户规模、用户等级分布、偏好分类分布、活跃用户 TOP。
- 单用户画像：RFM 分数、最近活跃时间、偏好分类、行为汇总、个性化推荐。
- 左侧活跃用户 TOP 按 RFM 综合分排序，点击用户即可切换个人画像。

RFM 活跃度计算：

```text
RFM = R * 40% + F * 40% + M * 20%
```

- R：最近活跃时间，越近分数越高。
- F：行为频率，浏览、搜索、收藏、加购、下单、支付按权重累计。
- M：消费价值，根据支付金额分层。

### 3. 商品分析

商品分析页面支持：

- 商品 ID 查询。
- 商品名称关键词查询。
- 商品货号查询。
- 无结果时显示“无商品”。
- 单商品浏览、收藏、加购、下单、支付统计。
- 商品热度、加购率、下单率、支付率。
- 同分类相似商品推荐。

### 4. 商品推荐

当前推荐方式包括：

| 推荐方式 | 说明 | 用途 |
| --- | --- | --- |
| 热门推荐 | 根据全站行为热度生成 | 未登录用户兜底推荐 |
| 规则推荐 | 根据用户偏好分类和商品热度生成 | 可解释的个性化推荐 |
| ItemCF | 基于商品协同关系 | 推荐相似兴趣商品 |
| NCF | 神经协同过滤 | 学习用户和商品的隐式匹配 |
| DeepFM | 自动学习特征交叉 | 当前主要展示的深度学习推荐 |

用户端 H5 的“猜你喜欢”逻辑：

```text
已登录用户 -> DeepFM 个性化推荐
未登录用户 -> 全站热门推荐
无推荐结果 -> 自动兜底热门推荐
```

普通用户端只展示商品图片、名称和价格；推荐分、算法类型、推荐原因保留在分析看板中展示。

### 5. 算法评估

推荐算法评估指标包括：

- HitRate@K：测试期真实感兴趣商品是否出现在 TopK。
- Precision@K：TopK 推荐中命中的比例。
- Recall@K：测试期正样本被覆盖的比例。
- NDCG@K：命中商品越靠前得分越高。
- CategoryHitRate：推荐商品分类是否命中用户兴趣分类。
- Coverage：推荐结果覆盖的商品比例。

由于淘宝外部商品需要映射到 mall-swarm 本地商品，商品 ID 级命中会受到映射影响；答辩时建议重点解释分类命中率、NDCG 和覆盖率，同时说明 ID 映射带来的限制。

## 项目结构

```text
mall-swarm
├── mall-analytics        # 新增：用户行为分析服务
├── mall-recommend        # 新增：推荐服务
├── mall-app-web          # 用户端 H5 静态资源与推荐接入验证
├── dashboard             # 新增：用户行为分析与推荐看板
├── ml-recommend          # 新增：淘宝数据抽样、ItemCF/NCF/DeepFM 训练、评估与导出
├── docs                  # 课程交付文档、部署说明、数据链路说明
├── document/sql          # 演示数据、商品图片修复 SQL
├── tools                 # 商品图片批量修复工具
├── mall-admin            # 原 mall-swarm 后台服务
├── mall-portal           # 原 mall-swarm 用户端服务
├── mall-search           # 原 mall-swarm 商品搜索服务
└── pom.xml               # 已加入 mall-analytics 和 mall-recommend 模块
```

## 关键文档

| 文档 | 说明 |
| --- | --- |
| `docs/project-improvement-summary-and-next-steps.md` | 项目改造总结与后续计划 |
| `docs/coursework-delivery-guide.md` | 课程作业交付说明与答辩口径 |
| `docs/data-lineage-and-offline-pipeline.md` | 数据链路与离线推荐流水线 |
| `docs/behavior-data-collection-guide.md` | 行为采集说明 |
| `docs/deployment-operations-guide.md` | 云服务器部署与运维说明 |
| `docs/data-dictionary.md` | 核心数据表和字段说明 |
| `docs/api-test-report.md` | 主要接口与测试样例 |
| `docs/final-delivery-checklist.md` | 最终提交材料检查清单 |
| `docs/recommendation-training-record.md` | 推荐算法训练、评估和部署记录 |

## 本地构建与验证

后端构建：

```bash
mvn -pl mall-analytics -am -DskipTests package
mvn -pl mall-recommend -am -DskipTests package
```

关键测试：

```bash
node mall-app-web/recommend-home.test.js
node mall-app-web/behavior-track.test.js
node mall-analytics/behavior-event-api.test.js
node mall-recommend/recommend-evaluation-api.test.js
node ml-recommend/recommend_pipeline.test.js
node dashboard/evaluation-ui.test.js
```

离线推荐训练示例：

```bash
python ml-recommend/run_recommend_pipeline.py \
  --events ml-recommend/data/sampled_events.csv \
  --output-dir ml-recommend/output \
  --train-ratio 0.8 \
  --epochs 8 \
  --negative-ratio 4
```

## 五人小组分工建议

| 成员 | 方向 | 可展示成果 |
| --- | --- | --- |
| 成员 A | 行为采集与后端接口 | 行为事件表、行为上报接口、画像重建接口 |
| 成员 B | 用户画像与商品画像 | RFM 模型、用户画像、商品画像、分析 SQL |
| 成员 C | 推荐算法 | ItemCF、NCF、DeepFM、评估指标、推荐结果 |
| 成员 D | 前端看板与用户端 | dashboard、H5 推荐展示、交互优化 |
| 成员 E | 部署测试与文档 | 云服务器部署、接口测试、演示脚本、PPT 和报告 |

## 答辩演示建议

1. 打开用户端 H5，展示商城首页和“猜你喜欢”推荐。
2. 点击商品，说明 H5 行为埋点如何写入行为事件表。
3. 打开 dashboard，展示全体用户画像。
4. 在活跃用户 TOP 中选择用户，展示单用户画像和 RFM 分数。
5. 切换商品分析，按 ID 或关键词查询商品。
6. 展示商品热度、转化率和相似商品推荐。
7. 展示推荐结果页，说明热门推荐、规则推荐、ItemCF、NCF、DeepFM 的区别。
8. 展示算法评估页，解释 Precision、Recall、NDCG、分类命中率和覆盖率。
9. 说明当前系统限制：淘宝数据与本地商品映射不完全真实、推荐结果以离线导入为主。
10. 总结后续改进：更多 H5 埋点、定时训练、实时数仓、模型训练过程可视化。

## 来源与许可

本项目基于 `macrozheng/mall-swarm` 二次开发，原项目遵循 Apache License 2.0。课程新增代码和文档用于“行业大数据分析实战”课程作业展示。
