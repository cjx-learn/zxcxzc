# 部署与运维说明

## 1. 线上访问地址

| 模块 | 地址 |
| --- | --- |
| 管理端 | `http://114.55.170.17:8201/` |
| 用户端 H5 | `http://114.55.170.17:8201/app/` |
| 分析看板 | `http://114.55.170.17:8201/dashboard/` |
| 推荐评估接口 | `http://114.55.170.17:8201/mall-recommend/recommend/evaluate` |

## 2. 服务器目录

| 目录 | 说明 |
| --- | --- |
| `/opt/mall-swarm` | 后端服务部署目录 |
| `/opt/mall-swarm/logs` | 服务日志目录 |
| `/usr/share/nginx/mall-dashboard` | 分析看板静态文件 |
| `/usr/share/nginx/mall-app-web` | 用户端 H5 静态文件 |

## 3. 关键服务端口

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| Nginx 网关 | `8201` | 对外统一入口 |
| mall-analytics | `8206` | 用户行为分析、画像、行为上报 |
| mall-recommend | `8207` | 推荐结果和算法评估 |
| MySQL Docker | `3306` | 数据库 |
| RabbitMQ | `5672` | 行为事件队列 |

## 4. 常用检查命令

查看服务进程：

```bash
pgrep -af 'mall-analytics-1.0-SNAPSHOT[.]jar'
pgrep -af 'mall-recommend-1.0-SNAPSHOT[.]jar'
```

查看日志：

```bash
tail -n 80 /opt/mall-swarm/logs/mall-analytics.log
tail -n 80 /opt/mall-swarm/logs/mall-recommend.log
```

检查 Nginx：

```bash
nginx -t
nginx -s reload
```

## 5. 行为上报验证

```bash
curl -X POST 'http://127.0.0.1:8206/analytics/event' \
  -H 'Content-Type: application/json' \
  -d '{
    "userId": 1,
    "productId": 37,
    "categoryId": 19,
    "eventType": "view",
    "sessionId": "ops-test",
    "sourcePage": "deployment-guide",
    "deviceType": "pc"
  }'
```

公网验证地址：

```text
POST http://114.55.170.17:8201/mall-analytics/analytics/event
```

## 6. 推荐结果导入

本地重新训练后会生成：

```text
ml-recommend/output/model_recommend_result.sql
ml-recommend/output/model_ncf_recommend_result.sql
ml-recommend/output/model_deepfm_recommend_result.sql
ml-recommend/output/recommend_evaluation.sql
```

上传并导入：

```bash
scp ml-recommend/output/*.sql root@114.55.170.17:/tmp/
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -proot mall < /tmp/model_recommend_result.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -proot mall < /tmp/model_ncf_recommend_result.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -proot mall < /tmp/model_deepfm_recommend_result.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -proot mall < /tmp/recommend_evaluation.sql
```

## 7. 当前运维限制

当前部署可满足课程演示，但仍不是生产级运维：

- 服务启动命令仍以手动维护为主。
- 没有 systemd 服务文件。
- 没有自动备份策略。
- 没有健康检查和异常告警。
- 推荐训练和 SQL 导入没有定时调度。

后续可补充 systemd、Docker Compose、数据库备份脚本和定时任务。
