import argparse
import csv
import math
import os
from collections import Counter, defaultdict


EVENT_WEIGHT = {
    "view": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "pay": 5.0,
}


def parse_args():
    parser = argparse.ArgumentParser(description="Train a simple ItemCF recommender from sampled behavior events.")
    parser.add_argument("--events", required=True, help="sampled_events.csv")
    parser.add_argument("--output", required=True, help="Recommendation CSV output")
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--max-history-per-user", type=int, default=80)
    return parser.parse_args()


def load_user_history(path, max_history_per_user):
    user_events = defaultdict(dict)
    item_popularity = Counter()
    item_category = {}

    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            user_id = row["user_id"]
            item_id = row["item_id"]
            category_id = row["category_id"]
            event_type = row["event_type"]
            weight = EVENT_WEIGHT.get(event_type, 0.0)
            if weight <= 0:
                continue
            item_category[item_id] = category_id
            user_events[user_id][item_id] = user_events[user_id].get(item_id, 0.0) + weight
            item_popularity[item_id] += weight

    trimmed = {}
    for user_id, items in user_events.items():
        top_items = sorted(items.items(), key=lambda kv: kv[1], reverse=True)[:max_history_per_user]
        trimmed[user_id] = dict(top_items)
    return trimmed, item_popularity, item_category


def build_item_similarity(user_events):
    co_counts = defaultdict(Counter)
    item_norm = Counter()

    for items in user_events.values():
        entries = list(items.items())
        for item_i, weight_i in entries:
            item_norm[item_i] += weight_i * weight_i
        for idx, (item_i, weight_i) in enumerate(entries):
            for item_j, weight_j in entries[idx + 1:]:
                score = math.sqrt(weight_i * weight_j)
                co_counts[item_i][item_j] += score
                co_counts[item_j][item_i] += score

    similarities = {}
    for item_i, related in co_counts.items():
        similarities[item_i] = {}
        for item_j, co_score in related.items():
            denom = math.sqrt(item_norm[item_i] * item_norm[item_j])
            if denom > 0:
                similarities[item_i][item_j] = co_score / denom
    return similarities


def recommend(user_events, similarities, item_popularity, item_category, topn):
    rows = []
    popular_items = [item for item, _ in item_popularity.most_common(topn * 3)]

    for user_id, history in user_events.items():
        seen = set(history)
        scores = Counter()
        reasons = {}
        for item_id, behavior_weight in history.items():
            for candidate, sim in similarities.get(item_id, {}).items():
                if candidate in seen:
                    continue
                scores[candidate] += sim * behavior_weight
                reasons[candidate] = f"与用户历史商品 {item_id} 协同相似"

        if len(scores) < topn:
            for candidate in popular_items:
                if candidate not in seen and candidate not in scores:
                    scores[candidate] = item_popularity[candidate] * 0.01
                    reasons[candidate] = "冷启动补充：淘宝样本热门商品"

        for rank, (item_id, score) in enumerate(scores.most_common(topn), start=1):
            rows.append({
                "user_id": user_id,
                "external_item_id": item_id,
                "category_id": item_category.get(item_id, ""),
                "score": f"{score:.6f}",
                "rank_no": rank,
                "recommend_type": "model_itemcf",
                "reason": reasons.get(item_id, "ItemCF 模型推荐"),
            })
    return rows


def write_rows(path, rows):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        fieldnames = ["user_id", "external_item_id", "category_id", "score", "rank_no", "recommend_type", "reason"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def main():
    args = parse_args()
    user_events, item_popularity, item_category = load_user_history(args.events, args.max_history_per_user)
    similarities = build_item_similarity(user_events)
    rows = recommend(user_events, similarities, item_popularity, item_category, args.topn)
    write_rows(args.output, rows)
    print(f"users={len(user_events)} items={len(item_popularity)} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
