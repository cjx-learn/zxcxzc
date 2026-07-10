import argparse
import csv
import math
import os
import random
from collections import Counter, defaultdict


EVENT_WEIGHT = {
    "view": 1.0,
    "search": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "order": 4.5,
    "pay": 5.0,
}


def parse_args():
    parser = argparse.ArgumentParser(description="Train a lightweight NCF-style embedding recommender from mall events.")
    parser.add_argument("--events", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--embedding-dim", type=int, default=24)
    parser.add_argument("--epochs", type=int, default=24)
    parser.add_argument("--learning-rate", type=float, default=0.035)
    parser.add_argument("--negative-ratio", type=int, default=4)
    parser.add_argument("--seed", type=int, default=20260710)
    return parser.parse_args()


def sigmoid(value):
    if value >= 35:
        return 1.0
    if value <= -35:
        return 0.0
    return 1.0 / (1.0 + math.exp(-value))


def load_events(path):
    user_items = defaultdict(Counter)
    item_category = {}
    item_popularity = Counter()
    user_category = defaultdict(Counter)
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            event_type = row["event_type"]
            weight = EVENT_WEIGHT.get(event_type, 0.0)
            if weight <= 0:
                continue
            user_id = row["user_id"]
            item_id = row["item_id"]
            category_id = row["category_id"]
            user_items[user_id][item_id] += weight
            item_popularity[item_id] += weight
            item_category[item_id] = category_id
            user_category[user_id][category_id] += weight
    return user_items, item_category, item_popularity, user_category


def build_samples(user_items, users, items, negative_ratio, seed):
    rng = random.Random(seed)
    item_set = set(items)
    samples = []
    for user_id in users:
        positives = user_items[user_id]
        max_strength = max(positives.values()) if positives else 1.0
        negative_pool = list(item_set - set(positives))
        if not negative_pool:
            continue
        for item_id, strength in positives.items():
            label = min(1.0, 0.35 + strength / max_strength * 0.65)
            samples.append((user_id, item_id, label))
            for _ in range(negative_ratio):
                samples.append((user_id, rng.choice(negative_pool), 0.0))
    return samples


def train(user_items, users, items, dim, epochs, lr, negative_ratio, seed):
    rng = random.Random(seed)
    user_vec = {user: [rng.uniform(-0.08, 0.08) for _ in range(dim)] for user in users}
    item_vec = {item: [rng.uniform(-0.08, 0.08) for _ in range(dim)] for item in items}
    user_bias = defaultdict(float)
    item_bias = defaultdict(float)
    samples = build_samples(user_items, users, items, negative_ratio, seed)
    global_bias = -0.35
    for epoch in range(1, epochs + 1):
        rng.shuffle(samples)
        total_loss = 0.0
        for user_id, item_id, label in samples:
            uv = user_vec[user_id]
            iv = item_vec[item_id]
            logit = global_bias + user_bias[user_id] + item_bias[item_id] + sum(a * b for a, b in zip(uv, iv))
            pred = sigmoid(logit)
            err = pred - label
            total_loss += -(label * math.log(max(pred, 1e-8)) + (1 - label) * math.log(max(1 - pred, 1e-8)))
            user_bias[user_id] -= lr * err * 0.15
            item_bias[item_id] -= lr * err * 0.15
            for idx in range(dim):
                old_u = uv[idx]
                old_i = iv[idx]
                uv[idx] -= lr * (err * old_i + 0.002 * old_u)
                iv[idx] -= lr * (err * old_u + 0.002 * old_i)
        print(f"epoch={epoch} avg_loss={total_loss / max(len(samples), 1):.6f}")
    return user_vec, item_vec, user_bias, item_bias, global_bias


def recommend(user_items, item_category, item_popularity, user_category, users, items, model, topn):
    user_vec, item_vec, user_bias, item_bias, global_bias = model
    max_pop = max(item_popularity.values()) if item_popularity else 1.0
    rows = []
    for user_id in users:
        seen = set(user_items[user_id])
        category_total = sum(user_category[user_id].values()) or 1.0
        scored = []
        for item_id in items:
            if item_id in seen:
                continue
            uv = user_vec[user_id]
            iv = item_vec[item_id]
            base = sigmoid(global_bias + user_bias[user_id] + item_bias[item_id] + sum(a * b for a, b in zip(uv, iv)))
            category_id = item_category.get(item_id, "")
            category_boost = user_category[user_id].get(category_id, 0.0) / category_total
            popularity_boost = item_popularity[item_id] / max_pop
            score = min(0.999999, base * 0.82 + category_boost * 0.13 + popularity_boost * 0.05)
            scored.append((item_id, score, category_boost))
        scored.sort(key=lambda row: row[1], reverse=True)
        for rank_no, (item_id, score, category_boost) in enumerate(scored[:topn], start=1):
            rows.append({
                "user_id": user_id,
                "external_item_id": item_id,
                "category_id": item_category.get(item_id, ""),
                "score": f"{score:.6f}",
                "rank_no": rank_no,
                "recommend_type": "model_ncf",
                "reason": f"NCF轻量Embedding模型训练结果；用户向量与商品向量匹配，类目偏好贡献{category_boost:.2f}",
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
    random.seed(args.seed)
    user_items, item_category, item_popularity, user_category = load_events(args.events)
    users = sorted(user_items, key=lambda value: int(value))
    items = [item for item, _ in item_popularity.most_common()]
    model = train(user_items, users, items, args.embedding_dim, args.epochs, args.learning_rate, args.negative_ratio, args.seed)
    rows = recommend(user_items, item_category, item_popularity, user_category, users, items, model, args.topn)
    write_rows(args.output, rows)
    print(f"users={len(users)} items={len(items)} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
