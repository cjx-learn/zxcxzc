USE mall;

CREATE TABLE IF NOT EXISTS user_behavior_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NULL COMMENT '用户ID，未登录时为空',
  session_id VARCHAR(100) NULL COMMENT '会话ID或设备标识',
  product_id BIGINT NULL COMMENT '商品ID',
  category_id BIGINT NULL COMMENT '商品分类ID',
  event_type VARCHAR(30) NOT NULL COMMENT '行为类型:view/search/fav/cart/order/pay',
  keyword VARCHAR(255) NULL COMMENT '搜索关键词',
  source_page VARCHAR(255) NULL COMMENT '来源页面',
  device_type VARCHAR(50) NULL COMMENT '设备类型',
  ip VARCHAR(64) NULL COMMENT '客户端IP',
  user_agent VARCHAR(500) NULL COMMENT 'User-Agent',
  event_time DATETIME NOT NULL COMMENT '行为发生时间',
  event_date DATE GENERATED ALWAYS AS (DATE(event_time)) STORED COMMENT '行为日期',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user_id(user_id),
  INDEX idx_session_id(session_id),
  INDEX idx_product_id(product_id),
  INDEX idx_category_id(category_id),
  INDEX idx_event_type(event_type),
  INDEX idx_event_time(event_time),
  INDEX idx_event_date(event_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为事件明细表';

CREATE TABLE IF NOT EXISTS user_profile (
  user_id BIGINT PRIMARY KEY COMMENT '用户ID',
  view_count INT DEFAULT 0,
  search_count INT DEFAULT 0,
  fav_count INT DEFAULT 0,
  cart_count INT DEFAULT 0,
  order_count INT DEFAULT 0,
  pay_count INT DEFAULT 0,
  active_days INT DEFAULT 0,
  favorite_category_id BIGINT NULL,
  favorite_category_score DOUBLE DEFAULT 0,
  last_active_time DATETIME NULL,
  user_level VARCHAR(50) NULL,
  update_time DATETIME NULL,
  INDEX idx_user_level(user_level),
  INDEX idx_favorite_category_id(favorite_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS product_profile (
  product_id BIGINT PRIMARY KEY COMMENT '商品ID',
  category_id BIGINT NULL,
  view_count INT DEFAULT 0,
  search_count INT DEFAULT 0,
  fav_count INT DEFAULT 0,
  cart_count INT DEFAULT 0,
  order_count INT DEFAULT 0,
  pay_count INT DEFAULT 0,
  hot_score DOUBLE DEFAULT 0,
  cart_rate DOUBLE DEFAULT 0,
  order_rate DOUBLE DEFAULT 0,
  pay_rate DOUBLE DEFAULT 0,
  update_time DATETIME NULL,
  INDEX idx_category_id(category_id),
  INDEX idx_hot_score(hot_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品画像表';

CREATE TABLE IF NOT EXISTS user_product_score (
  user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  score DOUBLE DEFAULT 0,
  view_count INT DEFAULT 0,
  search_count INT DEFAULT 0,
  fav_count INT DEFAULT 0,
  cart_count INT DEFAULT 0,
  order_count INT DEFAULT 0,
  pay_count INT DEFAULT 0,
  update_time DATETIME NULL,
  PRIMARY KEY(user_id, product_id),
  INDEX idx_product_id(product_id),
  INDEX idx_score(score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户商品隐式评分表';

CREATE TABLE IF NOT EXISTS recommend_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL DEFAULT 0 COMMENT '用户ID，0表示全局推荐',
  product_id BIGINT NOT NULL COMMENT '推荐商品ID',
  recommend_score DOUBLE DEFAULT 0 COMMENT '推荐分数',
  rank_no INT DEFAULT 0 COMMENT '排序',
  recommend_type VARCHAR(50) NOT NULL COMMENT 'hot/rule/als/similar',
  reason VARCHAR(255) NULL COMMENT '推荐原因',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_type_product(user_id, recommend_type, product_id),
  INDEX idx_user_type_rank(user_id, recommend_type, rank_no),
  INDEX idx_product_id(product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐结果表';

CREATE TABLE IF NOT EXISTS analysis_task_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(100) NOT NULL,
  start_time DATETIME NULL,
  end_time DATETIME NULL,
  status VARCHAR(30) NULL,
  message TEXT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析任务日志表';
