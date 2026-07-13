# 基于电商用户行为的商品推荐与分析系统

本项目基于 [`macrozheng/mall-swarm`](https://github.com/macrozheng/mall-swarm) 二次开发，在微服务商城基础上增加了用户行为采集、用户与商品画像、热门/规则/ItemCF/NCF/DeepFM 推荐、离线评估、ECharts 分析看板和 H5 推荐展示。

本文档以 **Ubuntu 22.04、4 核 16 GB、100 GB 磁盘**为参考环境，包含从源码部署完整系统的步骤。所有命令默认使用 `root` 或具有 `sudo` 权限的用户执行。

## 在线演示

| 模块 | 地址 | 默认账号 |
| --- | --- | --- |
| 管理后台 | [http://182.92.113.19/](http://182.92.113.19/) | `admin / macro123` |
| 用户端 H5 | [http://182.92.113.19/app/](http://182.92.113.19/app/) | 可注册或登录 |
| 数据分析看板 | [http://182.92.113.19/dashboard/](http://182.92.113.19/dashboard/) | 无独立登录 |
| 兼容入口 | [http://182.92.113.19:8201/](http://182.92.113.19:8201/) | 与 80 端口内容相同 |

首次部署后请立即修改默认管理密码。仓库和本文档不保存云服务器、数据库或对象存储的生产密码。

## 核心功能

- 商城后台、商品、会员、订单、购物车、搜索等基础电商功能。
- 采集 `view/search/fav/cart/order/pay` 行为，通过 RabbitMQ 异步写入 MySQL。
- 基于 RFM、行为频次、消费金额构建用户画像和活跃用户排行。
- 聚合商品热度、转化率和偏好分类，构建商品画像。
- 提供热门推荐、规则推荐、相似商品、ItemCF、NCF 和 DeepFM 推荐。
- 使用时间切分完成 HitRate、Precision、Recall、NDCG、Coverage 等离线评估。
- 在管理看板展示用户分析、商品分析、推荐结果和算法评估。

## 数据链路

在线链路：

```text
H5 用户行为
  -> Nginx / Spring Cloud Gateway
  -> mall-analytics 接收与校验
  -> RabbitMQ 消息队列
  -> MySQL user_behavior_event
  -> SQL 聚合 user_profile / product_profile / user_product_score
  -> 热门推荐与规则推荐
  -> MySQL recommend_result
  -> mall-recommend 推荐接口
  -> H5 / ECharts 看板
```

离线链路：

```text
淘宝 UserBehavior.csv
  -> Python / Pandas 抽样清洗
  -> 每个用户按时间进行 80% 训练、20% 测试切分
  -> Python ItemCF + PyTorch NCF / DeepFM
  -> TopN 推荐、离线评估、ID 映射和分数归一化
  -> SQL 批量导入 recommend_result / recommend_evaluation
  -> 在线推荐接口读取预计算结果
```

当前在线服务通过 JDBC 查询 MySQL 中的预计算推荐结果，不在请求时加载 `.pt` 模型实时推理。项目当前未使用 Spark、Streamlit、DIN、DIEN 或 GNN。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5、Spring Cloud 2025、Spring Cloud Alibaba、MyBatis、Sa-Token |
| 数据组件 | MySQL 5.7、Redis 7、MongoDB 4、RabbitMQ 3.9、Elasticsearch 7.17、MinIO、Nacos 2.1 |
| 推荐训练 | Python 3、Pandas、NumPy、PyTorch、ItemCF、NCF、DeepFM |
| 前端与展示 | Vue 构建产物、H5、ECharts、Nginx |
| 部署 | Docker Compose、Maven、Nginx、Linux `nohup` |

Redis 在当前实现中用于管理员/会员缓存、验证码、网关权限资源映射和订单编号自增，不缓存 `recommend_result`。

## 项目目录

```text
mall-swarm/
├── config/                 # 原项目 Nacos 开发/生产配置
├── dashboard/              # ECharts 用户行为与推荐分析看板
├── data-analysis/          # 用户画像、商品画像、热门/规则推荐 SQL
├── deploy/
│   ├── infra/              # MySQL、Redis、RabbitMQ 等 Docker Compose
│   ├── nacos/              # 部署时导入 Nacos 的生产配置
│   └── logback-console.xml # 云服务器日志配置
├── document/
│   ├── sql/                # 商城初始化 SQL、分析扩展表 SQL、演示数据
│   ├── docker/             # 原项目 Docker 部署资料
│   └── resource/           # 架构图和项目截图
├── mall-admin/             # 管理后台接口服务，默认端口 8080
├── mall-admin-web/         # 已构建的管理后台静态文件
├── mall-analytics/         # 行为采集、画像与分析服务，端口 8206
├── mall-app-web/           # 已构建的用户端 H5 静态文件
├── mall-auth/              # 认证服务，默认端口 8401
├── mall-common/            # 通用代码
├── mall-demo/              # 微服务调用示例，默认端口 8082
├── mall-gateway/           # API 网关，部署时使用端口 8202
├── mall-mbg/               # MyBatis Generator 模型与 Mapper
├── mall-monitor/           # Spring Boot Admin 监控，默认端口 8101
├── mall-portal/            # 用户端商城接口，默认端口 8085
├── mall-recommend/         # 推荐查询、相似商品与评估接口，端口 8207
├── mall-search/            # Elasticsearch 商品搜索服务，默认端口 8081
├── ml-recommend/           # 数据抽样、模型训练、评估和推荐 SQL 导出
├── pom.xml                 # Maven 多模块入口
└── README.md
```

## 部署前准备

### 1. 服务器要求

- 推荐：Ubuntu 22.04、4C16G、100 GB SSD。
- 最低演示配置：4C8G，需要降低 Elasticsearch 和 Java 堆内存。
- 云安全组开放 `22/tcp` 和 `80/tcp`；需要兼容入口时再开放 `8201/tcp`。
- 不要向公网开放 `3306/6379/5672/8848/9000/9200/27017`。

### 2. 安装软件

```bash
apt-get update
apt-get install -y git openjdk-17-jdk maven nginx curl python3 python3-venv

curl -fsSL https://get.docker.com | sh
systemctl enable --now docker nginx
docker --version
docker compose version
java -version
mvn -version
```

如果 `docker compose version` 不可用，安装 Compose 插件：

```bash
apt-get install -y docker-compose-plugin
```

### 3. 获取代码并设置变量

```bash
git clone -b master https://github.com/cjx-learn/zxcxzc.git /opt/mall-swarm
cd /opt/mall-swarm

export SERVER_IP='你的公网IP'
export DB_ROOT_PASSWORD='设置一个强MySQLRoot密码'
export DB_APP_PASSWORD='设置一个强业务数据库密码'
export MINIO_PASSWORD='设置一个强MinIO密码'
```

以下命令仅修改服务器工作副本，不要把替换后的密码提交到 Git：

```bash
sed -i "s/MYSQL_ROOT_PASSWORD: root/MYSQL_ROOT_PASSWORD: ${DB_ROOT_PASSWORD}/" deploy/infra/docker-compose.yml
sed -i "s/MINIO_ROOT_PASSWORD: minioadmin/MINIO_ROOT_PASSWORD: ${MINIO_PASSWORD}/" deploy/infra/docker-compose.yml
sed -i "s/password: 123456/password: ${DB_APP_PASSWORD}/" deploy/nacos/mall-*-prod.yaml
sed -i "s/secretKey: minioadmin/secretKey: ${MINIO_PASSWORD}/" deploy/nacos/mall-admin-prod.yaml
```

## 完整部署步骤

### 1. 启动基础组件

Elasticsearch 启动前需要调整内核参数：

```bash
sysctl -w vm.max_map_count=262144
grep -q '^vm.max_map_count=' /etc/sysctl.conf || echo 'vm.max_map_count=262144' >> /etc/sysctl.conf

mkdir -p /mydata/{mysql/{data,conf,log},redis/data,rabbitmq/{data,log},mongo/db,elasticsearch/{data,plugins},minio/data,nacos/logs}
chmod 777 /mydata/elasticsearch/data

docker compose -f deploy/infra/docker-compose.yml up -d
docker compose -f deploy/infra/docker-compose.yml ps
```

等待容器完成初始化：

```bash
until docker exec mysql mysqladmin ping -uroot -p"${DB_ROOT_PASSWORD}" --silent; do sleep 3; done
until curl -fsS http://127.0.0.1:8848/nacos/ >/dev/null; do sleep 3; done
until curl -fsS http://127.0.0.1:9200 >/dev/null; do sleep 3; done
```

### 2. 初始化 MySQL

导入商城基础表、行为分析与推荐扩展表，并创建后端使用的数据库账号：

```bash
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall < document/sql/mall.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall < document/sql/analytics_tables.sql

docker exec -i mysql mysql -uroot -p"${DB_ROOT_PASSWORD}" <<SQL
CREATE USER IF NOT EXISTS 'reader'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
ALTER USER 'reader'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
GRANT ALL PRIVILEGES ON mall.* TO 'reader'@'%';
FLUSH PRIVILEGES;
SQL
```

需要课程演示行为时，可选导入种子数据并构建画像与推荐：

```bash
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall < document/sql/demo_behavior_seed.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall < data-analysis/build_profiles.sql
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall < data-analysis/build_recommend_result.sql
```

### 3. 初始化 MinIO

创建 `mall` 存储桶并允许公开读取商品图片：

```bash
docker run --rm --network host --entrypoint /bin/sh minio/mc -c \
  "mc alias set local http://127.0.0.1:9000 minioadmin '${MINIO_PASSWORD}' && \
   mc mb --ignore-existing local/mall && \
   mc anonymous set download local/mall"
```

### 4. 导入 Nacos 生产配置

Java 服务运行在宿主机，而 Nacos 容器名为 `nacos-registry`。加入本机解析后批量发布配置：

```bash
grep -q 'nacos-registry' /etc/hosts || echo '127.0.0.1 nacos-registry' >> /etc/hosts

for file in deploy/nacos/*.yaml; do
  data_id=$(basename "$file")
  curl -fsS -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode 'group=DEFAULT_GROUP' \
    --data-urlencode 'type=yaml' \
    --data-urlencode "content@${file}"
  echo " imported ${data_id}"
done
```

可访问 `http://127.0.0.1:8848/nacos/` 检查配置。Nacos 只绑定回环地址，远程检查请使用 SSH 端口转发。

### 5. 构建后端

```bash
cd /opt/mall-swarm
MAVEN_OPTS='-Xms256m -Xmx2g' mvn clean package -DskipTests
mkdir -p logs run
```

确认以下核心 JAR 已生成：

```bash
find mall-* -path '*/target/*.jar' -type f -printf '%p\n' | sort
```

### 6. 启动后端服务

先启动业务服务，最后启动网关。以下堆内存配置适用于 4C16G：

```bash
cd /opt/mall-swarm
COMMON='--spring.profiles.active=prod --logging.config=file:/opt/mall-swarm/deploy/logback-console.xml --logstash.enableInnerLog=false'

nohup java -Xms256m -Xmx512m -jar mall-admin/target/mall-admin-1.0-SNAPSHOT.jar $COMMON \
  --minio.publicEndpoint="http://${SERVER_IP}/minio" > logs/mall-admin.log 2>&1 & echo $! > run/mall-admin.pid

nohup java -Xms128m -Xmx256m -jar mall-analytics/target/mall-analytics-1.0-SNAPSHOT.jar \
  --spring.datasource.url='jdbc:mysql://127.0.0.1:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false' \
  --spring.datasource.username=reader --spring.datasource.password="${DB_APP_PASSWORD}" \
  --spring.rabbitmq.host=127.0.0.1 --spring.rabbitmq.virtual-host=/mall \
  --spring.rabbitmq.username=mall --spring.rabbitmq.password=mall \
  --spring.data.mongodb.host=127.0.0.1 --spring.data.mongodb.database=mall-port \
  --analytics.rebuild.fixed-delay-ms=300000 > logs/mall-analytics.log 2>&1 & echo $! > run/mall-analytics.pid

nohup java -Xms192m -Xmx384m -jar mall-auth/target/mall-auth-1.0-SNAPSHOT.jar $COMMON > logs/mall-auth.log 2>&1 & echo $! > run/mall-auth.pid
nohup java -Xms128m -Xmx256m -jar mall-demo/target/mall-demo-1.0-SNAPSHOT.jar $COMMON > logs/mall-demo.log 2>&1 & echo $! > run/mall-demo.pid
nohup java -Xms192m -Xmx384m -jar mall-monitor/target/mall-monitor-1.0-SNAPSHOT.jar $COMMON > logs/mall-monitor.log 2>&1 & echo $! > run/mall-monitor.pid
nohup java -Xms256m -Xmx512m -jar mall-portal/target/mall-portal-1.0-SNAPSHOT.jar $COMMON > logs/mall-portal.log 2>&1 & echo $! > run/mall-portal.pid

nohup java -Xms128m -Xmx256m -jar mall-recommend/target/mall-recommend-1.0-SNAPSHOT.jar \
  --spring.datasource.url='jdbc:mysql://127.0.0.1:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false' \
  --spring.datasource.username=reader --spring.datasource.password="${DB_APP_PASSWORD}" \
  > logs/mall-recommend.log 2>&1 & echo $! > run/mall-recommend.pid

nohup java -Xms256m -Xmx512m -jar mall-search/target/mall-search-1.0-SNAPSHOT.jar $COMMON > logs/mall-search.log 2>&1 & echo $! > run/mall-search.pid

sleep 20
nohup java -Xms192m -Xmx384m -jar mall-gateway/target/mall-gateway-1.0-SNAPSHOT.jar $COMMON \
  --server.port=8202 > logs/mall-gateway.log 2>&1 & echo $! > run/mall-gateway.pid
```

查看进程和监听端口：

```bash
pgrep -af 'java.*mall-.*\.jar'
ss -lntp | grep -E ':(8080|8081|8082|8085|8101|8202|8206|8207|8401)'
```

### 7. 部署前端静态文件

```bash
mkdir -p /usr/share/nginx/{mall-admin-web,mall-dashboard,mall-app-web}
cp -a mall-admin-web/. /usr/share/nginx/mall-admin-web/
cp -a dashboard/. /usr/share/nginx/mall-dashboard/
cp -a mall-app-web/. /usr/share/nginx/mall-app-web/
chown -R www-data:www-data /usr/share/nginx/mall-admin-web /usr/share/nginx/mall-dashboard /usr/share/nginx/mall-app-web
```

### 8. 配置 Nginx

创建 `/etc/nginx/sites-available/mall-swarm`：

```nginx
server {
    listen 80 default_server;
    listen 8201;
    listen [::]:80 default_server;
    listen [::]:8201;
    server_name _;

    root /usr/share/nginx/mall-admin-web;
    index index.html;
    client_max_body_size 20m;

    location /dashboard/ {
        alias /usr/share/nginx/mall-dashboard/;
        index index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri $uri/ /index.html;
    }
    location = /dashboard { return 301 /dashboard/; }

    location /app/ {
        alias /usr/share/nginx/mall-app-web/;
        index index.html;
        add_header Cache-Control "no-cache, no-store, must-revalidate" always;
        try_files $uri $uri/ /app/index.html;
    }
    location = /app { return 301 /app/; }

    location / { try_files $uri $uri/ /index.html; }

    location /minio/ {
        proxy_pass http://127.0.0.1:9000/;
        proxy_set_header Host 127.0.0.1:9000;
        proxy_set_header X-Real-IP $remote_addr;
    }
    location /app/minio/ {
        proxy_pass http://127.0.0.1:9000/;
        proxy_set_header Host 127.0.0.1:9000;
    }
    location /mall/ {
        proxy_pass http://macro-oss.oss-cn-shenzhen.aliyuncs.com/mall/;
        proxy_set_header Host macro-oss.oss-cn-shenzhen.aliyuncs.com;
    }

    location /mall-analytics/ {
        proxy_pass http://127.0.0.1:8206/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location /mall-recommend/ {
        proxy_pass http://127.0.0.1:8207/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /mall-admin/ {
        proxy_pass http://127.0.0.1:8202/mall-admin/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location /mall-portal/ {
        proxy_pass http://127.0.0.1:8202/mall-portal/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
    location /mall-search/ {
        proxy_pass http://127.0.0.1:8202/mall-search/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

启用站点：

```bash
rm -f /etc/nginx/sites-enabled/default
ln -sfn /etc/nginx/sites-available/mall-swarm /etc/nginx/sites-enabled/mall-swarm
nginx -t
systemctl reload nginx
```

## 部署验证

### 1. 服务健康检查

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8085/actuator/health
curl -fsS http://127.0.0.1:8202/actuator/health
curl -fsS http://127.0.0.1:8206/actuator/health
curl -fsS http://127.0.0.1:8207/actuator/health
curl -fsS 'http://127.0.0.1:8207/recommend/hot?limit=5'
```

### 2. 公网检查

```bash
curl -I "http://${SERVER_IP}/"
curl -I "http://${SERVER_IP}/app/"
curl -I "http://${SERVER_IP}/dashboard/"
curl -fsS "http://${SERVER_IP}/mall-recommend/recommend/hot?limit=5"
```

浏览器访问：

```text
管理后台：http://服务器IP/
用户端 H5：http://服务器IP/app/
分析看板：http://服务器IP/dashboard/
```

### 3. 数据检查

```bash
docker exec mysql mysql -ureader -p"${DB_APP_PASSWORD}" mall -e "
SELECT COUNT(*) AS members FROM ums_member;
SELECT COUNT(*) AS products FROM pms_product;
SELECT COUNT(*) AS events FROM user_behavior_event;
SELECT COUNT(*) AS profiles FROM user_profile;
SELECT COUNT(*) AS recommendations FROM recommend_result;
SELECT COUNT(*) AS evaluations FROM recommend_evaluation;"
```

## 离线推荐训练与发布

模型训练不是启动在线系统的必需步骤。需要重新训练时，先将淘宝 `UserBehavior.csv` 放到服务器或本机，再执行：

```bash
cd /opt/mall-swarm
python3 -m venv .venv
source .venv/bin/activate
pip install -r ml-recommend/requirements.txt

python ml-recommend/sample_taobao.py \
  --input /data/taobao/UserBehavior.csv \
  --output-dir ml-recommend/data \
  --max-users 5000 --max-items 10000 --max-events 300000 \
  --recent-days 9 --start-ts 1511539200 --end-ts 1512316799

python ml-recommend/train_itemcf.py \
  --events ml-recommend/data/sampled_events.csv \
  --output ml-recommend/output/itemcf_recommendations.csv --topn 10

python ml-recommend/train_ncf.py \
  --events ml-recommend/data/sampled_events.csv \
  --output ml-recommend/output/ncf_recommendations.csv \
  --model-output ml-recommend/output/ncf_model.pt \
  --topn 10 --embedding-dim 32 --epochs 6

python ml-recommend/train_deepfm.py \
  --events ml-recommend/data/sampled_events.csv \
  --output ml-recommend/output/deepfm_recommendations.csv \
  --model-output ml-recommend/output/deepfm_model.pt \
  --topn 10 --embedding-dim 16 --epochs 6
```

使用 `ml-recommend/export_recommend_sql.py` 完成淘宝 ID 到本地会员/商品 ID 的映射和 SQL 导出，再导入 MySQL：

```bash
docker exec -i mysql mysql --default-character-set=utf8mb4 -uroot -p"${DB_ROOT_PASSWORD}" mall \
  < ml-recommend/output/model_recommend_result.sql
```

详细参数见 [`ml-recommend/README.md`](ml-recommend/README.md)。服务器只需要导入 SQL；`.pt` 文件不需要复制给在线 Java 服务。

## 数据迁移与备份

从旧服务器迁移时，建议代码走 Git，数据库和组件数据单独备份：

```bash
# 旧服务器导出
docker exec mysql mysqldump -uroot -p'旧密码' --single-transaction --routines --triggers mall > mall.sql
tar -czf mall-component-data.tar.gz /mydata/minio/data /mydata/mongo/db

# 新服务器恢复 MySQL
docker exec -i mysql mysql -uroot -p'新密码' mall < mall.sql
```

迁移前后分别统计会员、商品、订单、行为、画像和推荐表数量；确认新服务器正常后再停止旧服务器写入，避免两边数据分叉。

## 常用运维命令

```bash
# 基础组件
docker compose -f /opt/mall-swarm/deploy/infra/docker-compose.yml ps
docker compose -f /opt/mall-swarm/deploy/infra/docker-compose.yml logs --tail=100
docker compose -f /opt/mall-swarm/deploy/infra/docker-compose.yml restart

# Java 服务
pgrep -af 'java.*mall-.*\.jar'
tail -f /opt/mall-swarm/logs/mall-analytics.log
tail -f /opt/mall-swarm/logs/mall-recommend.log

# 停止单个服务，然后按“启动后端服务”中的对应命令重启
kill "$(cat /opt/mall-swarm/run/mall-analytics.pid)"

# Nginx
nginx -t
systemctl reload nginx
journalctl -u nginx -n 100 --no-pager
```

## 常见问题

### Nacos 连接失败

检查 `nacos-registry` 是否解析到 `127.0.0.1`，并确认 Nacos 配置已导入：

```bash
getent hosts nacos-registry
curl -I http://127.0.0.1:8848/nacos/
tail -n 100 logs/mall-gateway.log
```

### 页面能打开但接口报 502

检查 `8202/8206/8207` 端口和相应 Java 日志。Nginx 只负责反向代理，不会自动启动 Java 服务。

### 商品图片不显示

- 新上传图片：检查 MinIO `mall` 桶、公开读取策略和 `/minio/` 代理。
- 原始商品图片：检查服务器是否能访问 `macro-oss.oss-cn-shenzhen.aliyuncs.com`。
- 数据迁移后图片缺失：除 MySQL 外还必须迁移 `/mydata/minio/data`。

### 推荐结果为空

```bash
docker exec -i mysql mysql -uroot -p"${DB_ROOT_PASSWORD}" mall < data-analysis/build_profiles.sql
docker exec -i mysql mysql -uroot -p"${DB_ROOT_PASSWORD}" mall < data-analysis/build_recommend_result.sql
curl -fsS 'http://127.0.0.1:8207/recommend/hot?limit=10'
```

深度学习推荐仍为空时，确认已导入模型导出的 SQL，并检查 `recommend_result.recommend_type` 是否为 `model_itemcf`、`model_ncf` 或 `model_deepfm`。

### Elasticsearch 无法启动

```bash
sysctl vm.max_map_count
chmod 777 /mydata/elasticsearch/data
docker logs --tail=100 elasticsearch
```

## 相关文档

- [`document/sql/analytics_tables.sql`](document/sql/analytics_tables.sql)：行为、画像、推荐和评估表结构。
- [`data-analysis/build_profiles.sql`](data-analysis/build_profiles.sql)：RFM 与用户/商品画像构建。
- [`data-analysis/build_recommend_result.sql`](data-analysis/build_recommend_result.sql)：热门和规则推荐生成。
- [`ml-recommend/README.md`](ml-recommend/README.md)：淘宝数据抽样与模型训练参数。
- [`LICENSE`](LICENSE)：Apache License 2.0。

## 许可证与来源

本项目基于 `macrozheng/mall-swarm` 进行课程二次开发，遵循 [Apache License 2.0](LICENSE)。课程新增代码和文档用于“基于电商用户行为数据的商品推荐与用户行为分析系统”教学、演示与研究。
