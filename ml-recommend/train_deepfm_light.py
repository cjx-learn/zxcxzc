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
    parser = argparse.ArgumentParser(description="Train a lightweight DeepFM-style feature-cross recommender from mall events.")
    parser.add_argument("--events", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--embedding-dim", type=int, default=16)
    parser.add_argument("--epochs", type=int, default=20)
    parser.add_argument("--learning-rate", type=float, default=0.025)
    parser.add_argument("--negative-ratio", type=int, default=4)
    parser.add_argument("--seed", type=int, default=20260710)
    return parser.parse_args()


def sigmoid(value):
    if value >= 35:
        return 1.0
    if value <= -35:
        return 0.0
    return 1.0 / (1.0 + math.exp(-value))


def bucket(value, boundaries):
    for idx, boundary in enumerate(boundaries):
        if value <= boundary:
            return str(idx)
    return str(len(boundaries))


def load_events(path):
    user_items = defaultdict(Counter)
    user_strength = Counter()
    item_strength = Counter()
    item_category = {}
    item_context = {}
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            weight = EVENT_WEIGHT.get(row["event_type"], 0.0)
            if weight <= 0:
                continue
            user_id = row["user_id"]
            item_id = row["item_id"]
            category_id = row["category_id"]
            timestamp = int(float(row.get("timestamp") or 0))
            user_items[user_id][item_id] += weight
            user_strength[user_id] += weight
            item_strength[item_id] += weight
            item_category[item_id] = category_id
            item_context[item_id] = (str((timestamp // 3600) % 24), str((timestamp // 86400 + 4) % 7))
    return user_items, user_strength, item_strength, item_category, item_context


def features(user_id, item_id, user_strength, item_strength, item_category, item_context):
    hour, weekday = item_context.get(item_id, ("0", "0"))
    return [
        "u:" + user_id,
        "i:" + item_id,
        "c:" + item_category.get(item_id, "unknown"),
        "ub:" + bucket(user_strength[user_id], [2, 5, 10, 20, 50, 120]),
        "ib:" + bucket(item_strength[item_id], [2, 5, 10, 20, 50, 120]),
        "h:" + hour,
        "w:" + weekday,
    ]


def build_samples(user_items, users, items, user_strength, item_strength, item_category, item_context, negative_ratio, seed):
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
            samples.append((features(user_id, item_id, user_strength, item_strength, item_category, item_context), label))
            for _ in range(negative_ratio):
                negative_item = rng.choice(negative_pool)
                samples.append((features(user_id, negative_item, user_strength, item_strength, item_category, item_context), 0.0))
    return samples


def train(samples, dim, epochs, lr, seed):
    rng = random.Random(seed)
    linear = defaultdict(float)
    vectors = {}
    bias = -0.35

    def vector(feature):
        if feature not in vectors:
            vectors[feature] = [rng.uniform(-0.06, 0.06) for _ in range(dim)]
        return vectors[feature]

    for epoch in range(1, epochs + 1):
        rng.shuffle(samples)
        total_loss = 0.0
        for feats, label in samples:
            vecs = [vector(feat) for feat in feats]
            linear_part = bias + sum(linear[feat] for feat in feats)
            fm_part = 0.0
            for k in range(dim):
                values = [vec[k] for vec in vecs]
                sum_value = sum(values)
                fm_part += 0.5 * (sum_value * sum_value - sum(v * v for v in values))
            pred = sigmoid(linear_part + fm_part)
            err = pred - label
            total_loss += -(label * math.log(max(pred, 1e-8)) + (1 - label) * math.log(max(1 - pred, 1e-8)))
            for feat in feats:
                linear[feat] -= lr * (err + 0.001 * linear[feat])
            for idx, feat in enumerate(feats):
                vec = vecs[idx]
                for k in range(dim):
                    sum_other = sum(vecs[j][k] for j in range(len(vecs)) if j != idx)
                    vec[k] -= lr * (err * sum_other + 0.002 * vec[k])
        print(f"epoch={epoch} avg_loss={total_loss / max(len(samples), 1):.6f}")
    return linear, vectors, bias


def predict(feats, model, dim):
    linear, vectors, bias = model
    vecs = [vectors.get(feat, [0.0] * dim) for feat in feats]
    linear_part = bias + sum(linear.get(feat, 0.0) for feat in feats)
    fm_part = 0.0
    for k in range(dim):
        values = [vec[k] for vec in vecs]
        sum_value = sum(values)
        fm_part += 0.5 * (sum_value * sum_value - sum(v * v for v in values))
    return sigmoid(linear_part + fm_part)


def recommend(user_items, users, items, user_strength, item_strength, item_category, item_context, model, dim, topn):
    rows = []
    max_item_strength = max(item_strength.values()) if item_strength else 1.0
    for user_id in users:
        seen = set(user_items[user_id])
        scored = []
        for item_id in items:
            if item_id in seen:
                continue
            feats = features(user_id, item_id, user_strength, item_strength, item_category, item_context)
            base = predict(feats, model, dim)
            hot = item_strength[item_id] / max_item_strength
            score = min(0.999999, base * 0.92 + hot * 0.08)
            scored.append((item_id, score))
        scored.sort(key=lambda row: row[1], reverse=True)
        for rank_no, (item_id, score) in enumerate(scored[:topn], start=1):
            rows.append({
                "user_id": user_id,
                "external_item_id": item_id,
                "category_id": item_category.get(item_id, ""),
                "score": f"{score:.6f}",
                "rank_no": rank_no,
                "recommend_type": "model_deepfm",
                "reason": "DeepFM轻量特征交叉模型训练结果；综合用户、商品、分类、强度桶和时间上下文特征",
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
    user_items, user_strength, item_strength, item_category, item_context = load_events(args.events)
    users = sorted(user_items, key=lambda value: int(value))
    items = [item for item, _ in item_strength.most_common()]
    samples = build_samples(user_items, users, items, user_strength, item_strength, item_category, item_context, args.negative_ratio, args.seed)
    model = train(samples, args.embedding_dim, args.epochs, args.learning_rate, args.seed)
    rows = recommend(user_items, users, items, user_strength, item_strength, item_category, item_context, model, args.embedding_dim, args.topn)
    write_rows(args.output, rows)
    print(f"users={len(users)} items={len(items)} samples={len(samples)} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
