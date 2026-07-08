# 最终交付检查清单

本文档根据《行业大数据分析实战课程简介V1.pptx》的要求整理，用于答辩前检查本项目是否具备“可运行、可展示、可解释、可复现”的交付条件。

## 1. 课程评分点对照

| 评分维度 | 分值 | 本项目对应材料 | 当前状态 |
| --- | --- | --- | --- |
| 选题与场景 | 10 | 电商用户行为分析与商品推荐 | 已具备 |
| 数据链路完整性 | 20 | 行为采集、清洗、存储、画像、推荐、展示 | 已具备，建议答辩时重点讲链路 |
| 技术实现质量 | 25 | mall-analytics、mall-recommend、dashboard、ml-recommend | 已具备，需保证演示环境稳定 |
| 分析与智能化 | 20 | RFM、商品热度、ItemCF、NCF、DeepFM、推荐评估 | 已具备，建议补充训练截图 |
| 可视化与展示 | 15 | 用户画像、商品分析、推荐结果、算法评估看板 | 已具备，需准备截图 |
| 报告与答辩 | 10 | 项目报告、PPT、分工说明、演示脚本 | 需要最终整理 |

## 2. 最终提交材料

### 2.1 项目报告

建议文件名：

```text
基于电商用户行为数据的商品推荐与用户行为分析系统设计与实现_项目报告.docx
```

建议结构：

1. 项目背景与研究意义。
2. 数据来源与数据说明。
3. 系统总体架构。
4. 数据链路设计。
5. 用户行为采集与数据建模。
6. 用户画像与商品画像设计。
7. 推荐算法设计与实现。
8. 算法评估指标与结果分析。
9. 系统功能展示。
10. 项目部署与运行说明。
11. 存在问题与后续改进。
12. 小组成员分工。

可引用文档：

- `docs/project-improvement-summary-and-next-steps.md`
- `docs/data-dictionary.md`
- `docs/data-lineage-and-offline-pipeline.md`
- `docs/api-test-report.md`
- `docs/deployment-operations-guide.md`

### 2.2 源码仓库

GitHub 仓库：

```text
https://github.com/cjx-learn/zxcxzc.git
```

答辩前检查：

```bash
git status
git log --oneline -5
git remote -v
```

仓库应包含：

- 根目录 `README.md`。
- 后端新增模块 `mall-analytics`、`mall-recommend`。
- 前端看板 `dashboard`。
- 用户端 H5 接入验证 `mall-app-web`。
- 离线推荐训练脚本 `ml-recommend`。
- 课程文档 `docs`。
- SQL 和工具脚本 `document/sql`、`tools`。

不应提交：

- `target/`。
- `*.jar`。
- `*.class`。
- `*.pt` 模型权重。
- 完整淘宝原始 `UserBehavior.csv`。
- `.env`。
- 密钥文件。

### 2.3 运行结果

需要准备的截图或录屏：

| 材料 | 截图内容 |
| --- | --- |
| 用户端 H5 首页 | 商品列表、“猜你喜欢”推荐、商品图片正常显示 |
| 用户端商品详情 | 点击推荐商品后进入详情页 |
| 分析看板首页 | 核心指标卡片、行为趋势、转化漏斗 |
| 全体用户画像 | 用户等级分布、偏好分类分布 |
| 单用户画像 | 左侧活跃用户 TOP、RFM 分数、行为汇总、个性化推荐 |
| 商品分析 | 商品搜索、商品热度、转化率、相似商品推荐 |
| 推荐结果 | DeepFM、NCF、ItemCF、规则推荐切换 |
| 算法评估 | Precision@K、Recall@K、NDCG@K、分类命中率、覆盖率 |
| 接口测试 | 行为采集、画像重建、推荐查询接口返回 JSON |
| 数据库结果 | `user_behavior_event`、`user_profile`、`product_profile`、`recommend_result` 表中有数据 |

推荐录制一个 3 到 5 分钟演示视频，防止答辩现场网络不稳定。

### 2.4 汇报 PPT

建议 12 到 15 页：

| 页码 | 内容 |
| --- | --- |
| 1 | 项目题目、小组成员 |
| 2 | 选题背景：为什么做电商行为分析与推荐 |
| 3 | 课程要求与项目目标 |
| 4 | 系统总体架构 |
| 5 | 数据来源与数据链路 |
| 6 | 用户行为采集设计 |
| 7 | 用户画像与 RFM 活跃度 |
| 8 | 商品画像与商品分析 |
| 9 | 推荐算法设计：热门、规则、ItemCF、NCF、DeepFM |
| 10 | DeepFM 原理与训练流程 |
| 11 | 算法评估指标与结果 |
| 12 | 系统功能演示截图 |
| 13 | 云服务器部署与运行效果 |
| 14 | 存在问题与改进方向 |
| 15 | 小组分工与总结 |

PPT 中必须回答课程 PPT 提出的几个问题：

- 为什么选这个场景？
- 数据如何获得？
- 系统怎么设计？
- 用了什么算法？
- 结果说明什么问题？

### 2.5 分工说明

建议五人分工：

| 成员 | 负责方向 | 可检查成果 |
| --- | --- | --- |
| 成员 A | 行为采集与后端接口 | `mall-analytics`、行为上报接口、画像重建接口 |
| 成员 B | 用户画像与商品画像 | RFM 模型、用户画像、商品画像、分析 SQL |
| 成员 C | 推荐算法 | ItemCF、NCF、DeepFM、评估指标、训练脚本 |
| 成员 D | 前端看板与用户端 | `dashboard`、H5 推荐展示、页面交互优化 |
| 成员 E | 部署测试与文档 | 云服务器部署、接口测试、README、报告和 PPT |

每个人答辩时至少能讲清：

- 自己负责了哪个模块。
- 该模块输入数据是什么。
- 处理逻辑是什么。
- 输出结果在哪里展示。
- 遇到的问题和解决方法。

## 3. 答辩演示流程

建议演示顺序：

1. 打开用户端 H5：`http://114.55.170.17:8201/app/`。
2. 展示首页商品和“猜你喜欢”推荐。
3. 点击一个推荐商品，说明行为埋点会产生 `view` 事件。
4. 打开分析看板：`http://114.55.170.17:8201/dashboard/`。
5. 展示行为概览、趋势图和转化漏斗。
6. 进入全体用户画像，讲用户等级和偏好分类。
7. 在左侧活跃用户 TOP 中选择用户，讲单用户 RFM 分数和个人推荐。
8. 进入商品分析，用商品 ID 或关键词查询商品。
9. 展示商品热度、转化率和相似商品推荐。
10. 进入推荐结果页，切换 DeepFM、NCF、ItemCF、规则推荐。
11. 进入算法评估页，讲 Precision、Recall、NDCG、分类命中率。
12. 总结当前系统限制和下一步改进。

## 4. 答辩口径

### 4.1 本项目和普通商城后台有什么区别？

普通商城后台主要管理商品、订单和用户。本项目把用户行为事件作为核心数据，进一步生成用户画像、商品画像和推荐结果，并通过看板解释用户兴趣、商品热度和算法效果。

### 4.2 为什么要做淘宝数据到本地商品的映射？

淘宝数据集提供真实用户行为序列，但它的商品 ID 不属于 mall-swarm 本地商城。如果要把训练结果展示在本地商城页面，就需要把外部商品映射到本地商品。这个映射让模型训练流程和商城展示系统连接起来，但也会带来商品 ID 级命中率偏低的问题。

### 4.3 为什么 DeepFM 效果看起来不一定总比热门推荐高？

当前数据是抽样数据，且淘宝商品与本地商品存在映射误差。热门推荐对少量演示商品更容易命中，而 DeepFM 的价值在于能够学习用户、商品、分类、行为强度和时间上下文之间的特征交叉。答辩时应同时看分类命中率、NDCG、覆盖率，而不是只看商品 ID 级 Precision。

### 4.4 当前项目和生产级实时数仓有什么区别？

当前项目是课程级离线数据链路，重点是跑通采集、处理、存储、训练、服务和展示闭环。生产级实时数仓通常会引入 Kafka、Flink、ClickHouse/Doris、任务调度、监控告警、数据质量校验和在线特征服务。

## 5. 答辩前最终检查

### 5.1 功能检查

- 用户端 H5 能打开。
- 分析看板能打开。
- 商品图片能正常显示。
- 点击推荐商品能跳转详情。
- 行为上报接口返回成功。
- 点击“刷新分析”有完成提示。
- 点击“重建画像”返回非零画像和推荐数量。
- 用户画像页面左侧为活跃用户列表，不是手动输入用户 ID。
- 商品分析查不到商品时显示“无商品”。
- 推荐结果默认展示 DeepFM，并能切换其他算法。
- 算法评估页面有指标数据。

### 5.2 数据检查

```sql
SELECT COUNT(*) FROM user_behavior_event;
SELECT COUNT(*) FROM user_profile;
SELECT COUNT(*) FROM product_profile;
SELECT COUNT(*) FROM recommend_result;
SELECT COUNT(*) FROM recommend_evaluation;
```

以上表不应为空。若为空，先执行演示数据 SQL 或点击看板中的“重建画像”。

### 5.3 代码检查

```bash
git status
mvn -pl mall-analytics -am -DskipTests package
mvn -pl mall-recommend -am -DskipTests package
node mall-app-web/recommend-home.test.js
node mall-app-web/behavior-track.test.js
node mall-analytics/behavior-event-api.test.js
node mall-recommend/recommend-evaluation-api.test.js
node ml-recommend/recommend_pipeline.test.js
node dashboard/evaluation-ui.test.js
```

### 5.4 材料检查

- 项目报告已生成。
- 汇报 PPT 已生成。
- GitHub 仓库地址可访问。
- README 首页能说明项目主题。
- 数据字典已补充。
- 接口测试报告已补充。
- 演示截图或演示视频已准备。
- 成员分工和贡献说明已准备。

## 6. 后续加分改进

如果还有时间，优先做：

1. 把 H5 的 `search/fav/cart/order/pay` 行为也接入统一埋点。
2. 给 DeepFM 训练过程增加 loss 曲线或 AUC 指标截图。
3. 增加推荐结果版本号，便于说明模型迭代。
4. 增加定时任务，自动重建画像和推荐结果。
5. 准备一份演示视频作为答辩备用材料。
