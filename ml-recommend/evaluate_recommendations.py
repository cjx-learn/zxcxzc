import argparse
import csv
import math
import os
from collections import Counter, defaultdict
from datetime import datetime


DEFAULT_LABELS = {
    "hot": "热门推荐",
    "rule": "规则推荐",
    "model_itemcf": "ItemCF",
    "model_ncf": "NCF",
    "model_deepfm": "DeepFM",
    "model_bpr": "BPR",
}

EVENT_WEIGHTS = {
    "view": 1.0,
    "search": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "order": 5.0,
    "pay": 5.0,
    "buy": 5.0,
}


def parse_args():
    parser = argparse.ArgumentParser(description="Evaluate recommendation CSV files with temporal holdout metrics.")
    parser.add_argument("--events", required=True, help="sampled_events.csv")
    parser.add_argument(
        "--recommendations",
        action="append",
        default=[],
        help="Algorithm CSV mapping, e.g. model_deepfm=ml-recommend/output/deepfm_recommendations.csv",
    )
    parser.add_argument("--output", required=True, help="Evaluation CSV output path")
    parser.add_argument("--sql", required=True, help="Evaluation SQL output path")
    parser.add_argument("--k", type=int, default=10)
    parser.add_argument("--test-ratio", type=float, default=0.2)
    parser.add_argument("--positive-events", default="fav,cart,pay,buy,order")
    return parser.parse_args()


def sql_string(value):
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def load_events(path):
    events_by_user = defaultdict(list)
    all_items = set()
    item_category = {}
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            user_id = str(row["user_id"])
            item_id = str(row["item_id"])
            event_type = row["event_type"]
            timestamp = int(float(row["timestamp"]))
            category_id = str(row.get("category_id", ""))
            events_by_user[user_id].append((timestamp, item_id, event_type, category_id))
            all_items.add(item_id)
            item_category[item_id] = category_id
    for rows in events_by_user.values():
        rows.sort(key=lambda item: item[0])
    return events_by_user, all_items, item_category


def split_train_test(events_by_user, test_ratio, positive_events):
    train_by_user = {}
    test_positive_by_user = {}
    test_positive_categories_by_user = {}
    hot_counter = Counter()
    for user_id, rows in events_by_user.items():
        if len(rows) < 2:
            continue
        split_index = max(1, min(len(rows) - 1, int(math.floor(len(rows) * (1 - test_ratio)))))
        train_rows = rows[:split_index]
        test_rows = rows[split_index:]
        positives = {item_id for _, item_id, event_type, _ in test_rows if event_type in positive_events}
        positive_categories = {category_id for _, _, event_type, category_id in test_rows if event_type in positive_events}
        train_by_user[user_id] = train_rows
        if positives:
            test_positive_by_user[user_id] = positives
            test_positive_categories_by_user[user_id] = positive_categories
        for _, item_id, event_type, _ in train_rows:
            hot_counter[item_id] += EVENT_WEIGHTS.get(event_type, 1.0)
    return train_by_user, test_positive_by_user, test_positive_categories_by_user, hot_counter


def parse_recommendation_args(values):
    mappings = []
    for value in values:
        if "=" not in value:
            raise SystemExit(f"--recommendations must be algorithm=path, got: {value}")
        algorithm, path = value.split("=", 1)
        mappings.append((algorithm.strip(), path.strip()))
    return mappings


def load_recommendations(path, k):
    recommendations = defaultdict(list)
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            user_id = str(row["user_id"])
            item_id = str(row["external_item_id"])
            category_id = str(row.get("category_id", ""))
            rank_no = int(float(row.get("rank_no") or len(recommendations[user_id]) + 1))
            score = float(row.get("score") or 0)
            recommendations[user_id].append((rank_no, -score, item_id, category_id))
    result = {}
    for user_id, rows in recommendations.items():
        rows.sort()
        result[user_id] = [(item_id, category_id) for _, _, item_id, category_id in rows[:k]]
    return result


def build_hot_recommendations(train_by_user, test_users, hot_counter, item_category, k):
    hot_items = [item for item, _ in hot_counter.most_common(max(200, k))]
    recommendations = {}
    for user_id in test_users:
        seen = {item_id for _, item_id, _, _ in train_by_user.get(user_id, [])}
        picked = [(item_id, item_category.get(item_id, "")) for item_id in hot_items if item_id not in seen][:k]
        recommendations[user_id] = picked
    return recommendations


def ndcg_at_k(recommended_items, positives, k):
    dcg = 0.0
    for index, item_id in enumerate(recommended_items[:k], start=1):
        if item_id in positives:
            dcg += 1.0 / math.log2(index + 1)
    ideal_hits = min(len(positives), k)
    if ideal_hits == 0:
        return 0.0
    idcg = sum(1.0 / math.log2(index + 1) for index in range(1, ideal_hits + 1))
    return dcg / idcg if idcg else 0.0


def category_ndcg_at_k(recommended_categories, positive_categories, k):
    dcg = 0.0
    for index, category_id in enumerate(recommended_categories[:k], start=1):
        if category_id and category_id in positive_categories:
            dcg += 1.0 / math.log2(index + 1)
    ideal_hits = min(len(positive_categories), k)
    if ideal_hits == 0:
        return 0.0
    idcg = sum(1.0 / math.log2(index + 1) for index in range(1, ideal_hits + 1))
    return dcg / idcg if idcg else 0.0


def evaluate_algorithm(algorithm, recommendations, positives_by_user, positive_categories_by_user, all_items, k):
    evaluated = 0
    hit_users = 0
    category_hit_users = 0
    precision_sum = 0.0
    recall_sum = 0.0
    ndcg_sum = 0.0
    category_ndcg_sum = 0.0
    category_precision_sum = 0.0
    total_hits = 0
    total_category_hits = 0
    total_recommended = 0
    unique_recommended = set()

    for user_id, positives in positives_by_user.items():
        recs = recommendations.get(user_id, [])[:k]
        if not recs:
            continue
        recommended_items = [item_id for item_id, _ in recs]
        recommended_categories = [category_id for _, category_id in recs]
        positive_categories = positive_categories_by_user.get(user_id, set())
        evaluated += 1
        hits = sum(1 for item_id in recommended_items if item_id in positives)
        category_hits = sum(1 for category_id in recommended_categories if category_id and category_id in positive_categories)
        total_hits += hits
        total_category_hits += category_hits
        total_recommended += len(recs)
        unique_recommended.update(recommended_items)
        if hits:
            hit_users += 1
        if category_hits:
            category_hit_users += 1
        precision_sum += hits / float(k)
        category_precision_sum += category_hits / float(k)
        recall_sum += hits / float(len(positives))
        ndcg_sum += ndcg_at_k(recommended_items, positives, k)
        category_ndcg_sum += category_ndcg_at_k(recommended_categories, positive_categories, k)

    if evaluated == 0:
        return {
            "algorithm": algorithm,
            "algorithm_label": DEFAULT_LABELS.get(algorithm, algorithm),
            "k": k,
            "evaluated_user_count": 0,
            "hit_user_count": 0,
            "hit_rate": 0.0,
            "category_hit_rate": 0.0,
            "precision_at_k": 0.0,
            "category_precision_at_k": 0.0,
            "category_ndcg_at_k": 0.0,
            "recall_at_k": 0.0,
            "ndcg_at_k": 0.0,
            "coverage": 0.0,
            "total_hit_count": 0,
            "total_category_hit_count": 0,
            "total_recommend_count": 0,
        }

    return {
        "algorithm": algorithm,
        "algorithm_label": DEFAULT_LABELS.get(algorithm, algorithm),
        "k": k,
        "evaluated_user_count": evaluated,
        "hit_user_count": hit_users,
        "hit_rate": hit_users / float(evaluated),
        "category_hit_rate": category_hit_users / float(evaluated),
        "precision_at_k": precision_sum / evaluated,
        "category_precision_at_k": category_precision_sum / evaluated,
        "category_ndcg_at_k": category_ndcg_sum / evaluated,
        "recall_at_k": recall_sum / evaluated,
        "ndcg_at_k": ndcg_sum / evaluated,
        "coverage": len(unique_recommended) / float(len(all_items) or 1),
        "total_hit_count": total_hits,
        "total_category_hit_count": total_category_hits,
        "total_recommend_count": total_recommended,
    }


def write_csv(path, metrics):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    fieldnames = [
        "algorithm",
        "algorithm_label",
        "k",
        "evaluated_user_count",
        "hit_user_count",
        "hit_rate",
        "category_hit_rate",
        "precision_at_k",
        "category_precision_at_k",
        "category_ndcg_at_k",
        "recall_at_k",
        "ndcg_at_k",
        "coverage",
        "total_hit_count",
        "total_category_hit_count",
        "total_recommend_count",
    ]
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for row in metrics:
            formatted = dict(row)
            for key in ["hit_rate", "category_hit_rate", "precision_at_k", "category_precision_at_k", "category_ndcg_at_k", "recall_at_k", "ndcg_at_k", "coverage"]:
                formatted[key] = f"{float(formatted[key]):.6f}"
            writer.writerow(formatted)


def write_sql(path, metrics):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("-- Generated by ml-recommend/evaluate_recommendations.py\n")
        f.write("USE mall;\n")
        f.write("SET NAMES utf8mb4;\n")
        f.write("DROP TABLE IF EXISTS recommend_evaluation;\n")
        f.write(
            """
CREATE TABLE IF NOT EXISTS recommend_evaluation (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  algorithm VARCHAR(64) NOT NULL,
  algorithm_label VARCHAR(64) NOT NULL,
  k_value INT NOT NULL,
  evaluated_user_count INT NOT NULL,
  hit_user_count INT NOT NULL,
  hit_rate DECIMAL(10,6) NOT NULL,
  category_hit_rate DECIMAL(10,6) NOT NULL,
  precision_at_k DECIMAL(10,6) NOT NULL,
  category_precision_at_k DECIMAL(10,6) NOT NULL,
  category_ndcg_at_k DECIMAL(10,6) NOT NULL,
  recall_at_k DECIMAL(10,6) NOT NULL,
  ndcg_at_k DECIMAL(10,6) NOT NULL,
  coverage DECIMAL(10,6) NOT NULL,
  total_hit_count INT NOT NULL,
  total_category_hit_count INT NOT NULL,
  total_recommend_count INT NOT NULL,
  evaluation_note VARCHAR(255) NOT NULL,
  create_time DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""
        )
        f.write("DELETE FROM recommend_evaluation;\n")
        note = "时间切分测试集，正样本为收藏/加购/支付/购买/下单行为"
        for row in metrics:
            f.write(
                "INSERT INTO recommend_evaluation "
                "(algorithm, algorithm_label, k_value, evaluated_user_count, hit_user_count, hit_rate, "
                "category_hit_rate, precision_at_k, category_precision_at_k, category_ndcg_at_k, recall_at_k, ndcg_at_k, coverage, "
                "total_hit_count, total_category_hit_count, total_recommend_count, "
                "evaluation_note, create_time) VALUES "
                f"({sql_string(row['algorithm'])}, {sql_string(row['algorithm_label'])}, {int(row['k'])}, "
                f"{int(row['evaluated_user_count'])}, {int(row['hit_user_count'])}, {float(row['hit_rate']):.6f}, "
                f"{float(row['category_hit_rate']):.6f}, {float(row['precision_at_k']):.6f}, "
                f"{float(row['category_precision_at_k']):.6f}, {float(row['category_ndcg_at_k']):.6f}, {float(row['recall_at_k']):.6f}, "
                f"{float(row['ndcg_at_k']):.6f}, {float(row['coverage']):.6f}, "
                f"{int(row['total_hit_count'])}, {int(row['total_category_hit_count'])}, {int(row['total_recommend_count'])}, "
                f"{sql_string(note)}, {sql_string(generated_at)});\n"
            )


def main():
    args = parse_args()
    positive_events = {item.strip() for item in args.positive_events.split(",") if item.strip()}
    events_by_user, all_items, item_category = load_events(args.events)
    train_by_user, positives_by_user, positive_categories_by_user, hot_counter = split_train_test(events_by_user, args.test_ratio, positive_events)

    metrics = []
    hot_recs = build_hot_recommendations(train_by_user, positives_by_user.keys(), hot_counter, item_category, args.k)
    metrics.append(evaluate_algorithm("hot", hot_recs, positives_by_user, positive_categories_by_user, all_items, args.k))

    for algorithm, path in parse_recommendation_args(args.recommendations):
        recs = load_recommendations(path, args.k)
        metrics.append(evaluate_algorithm(algorithm, recs, positives_by_user, positive_categories_by_user, all_items, args.k))

    metrics.sort(key=lambda row: (row["algorithm"] != "model_deepfm", -row["ndcg_at_k"], row["algorithm"]))
    write_csv(args.output, metrics)
    write_sql(args.sql, metrics)
    print(f"evaluated_users={len(positives_by_user)} algorithms={len(metrics)} output={args.output}")


if __name__ == "__main__":
    main()
