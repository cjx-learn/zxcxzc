import argparse
import csv
import math
import os
import random
from collections import defaultdict


EVENT_WEIGHT = {
    "view": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "pay": 5.0,
}


def parse_args():
    parser = argparse.ArgumentParser(description="Train a lightweight BPR matrix factorization recommender.")
    parser.add_argument("--events", required=True, help="sampled_events.csv")
    parser.add_argument("--output", required=True, help="Recommendation CSV output")
    parser.add_argument("--factors", type=int, default=24)
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--learning-rate", type=float, default=0.035)
    parser.add_argument("--regularization", type=float, default=0.01)
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--seed", type=int, default=20260707)
    return parser.parse_args()


def load_events(path):
    user_positive = defaultdict(dict)
    item_category = {}
    items = set()
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            user_id = row["user_id"]
            item_id = row["item_id"]
            event_type = row["event_type"]
            weight = EVENT_WEIGHT.get(event_type, 0.0)
            if weight <= 0:
                continue
            user_positive[user_id][item_id] = user_positive[user_id].get(item_id, 0.0) + weight
            item_category[item_id] = row["category_id"]
            items.add(item_id)
    return user_positive, sorted(items), item_category


def init_vector(rng, factors):
    return [(rng.random() - 0.5) * 0.1 for _ in range(factors)]


def dot(left, right):
    return sum(a * b for a, b in zip(left, right))


def sigmoid(value):
    if value > 35:
        return 1.0
    if value < -35:
        return 0.0
    return 1.0 / (1.0 + math.exp(-value))


def train_bpr(user_positive, items, factors, epochs, learning_rate, regularization, seed):
    rng = random.Random(seed)
    user_factors = {user_id: init_vector(rng, factors) for user_id in user_positive}
    item_factors = {item_id: init_vector(rng, factors) for item_id in items}
    item_list = list(items)
    training_pairs = [(user_id, item_id, weight) for user_id, positives in user_positive.items() for item_id, weight in positives.items()]

    for epoch in range(1, epochs + 1):
        rng.shuffle(training_pairs)
        total_loss = 0.0
        updates = 0
        for user_id, positive_item, weight in training_pairs:
            seen = user_positive[user_id]
            negative_item = rng.choice(item_list)
            retry = 0
            while negative_item in seen and retry < 20:
                negative_item = rng.choice(item_list)
                retry += 1
            if negative_item in seen:
                continue

            user_vec = user_factors[user_id]
            pos_vec = item_factors[positive_item]
            neg_vec = item_factors[negative_item]
            x_uij = dot(user_vec, pos_vec) - dot(user_vec, neg_vec)
            gradient = (1.0 - sigmoid(x_uij)) * min(weight, 5.0)
            total_loss += -math.log(max(sigmoid(x_uij), 1e-12))
            updates += 1

            for idx in range(factors):
                u = user_vec[idx]
                pi = pos_vec[idx]
                nj = neg_vec[idx]
                user_vec[idx] += learning_rate * (gradient * (pi - nj) - regularization * u)
                pos_vec[idx] += learning_rate * (gradient * u - regularization * pi)
                neg_vec[idx] += learning_rate * (-gradient * u - regularization * nj)

        avg_loss = total_loss / max(updates, 1)
        print(f"epoch={epoch} updates={updates} avg_loss={avg_loss:.6f}")
    return user_factors, item_factors


def recommend(user_positive, user_factors, item_factors, item_category, topn):
    rows = []
    all_items = list(item_factors)
    for user_id, user_vec in user_factors.items():
        seen = set(user_positive[user_id])
        scored = []
        for item_id in all_items:
            if item_id in seen:
                continue
            score = dot(user_vec, item_factors[item_id])
            scored.append((item_id, score))
        scored.sort(key=lambda item: item[1], reverse=True)
        for rank, (item_id, score) in enumerate(scored[:topn], start=1):
            rows.append({
                "user_id": user_id,
                "external_item_id": item_id,
                "category_id": item_category.get(item_id, ""),
                "score": f"{score:.6f}",
                "rank_no": rank,
                "recommend_type": "model_bpr",
                "reason": "BPR矩阵分解根据用户隐向量与商品隐向量预测兴趣",
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
    user_positive, items, item_category = load_events(args.events)
    user_factors, item_factors = train_bpr(
        user_positive=user_positive,
        items=items,
        factors=args.factors,
        epochs=args.epochs,
        learning_rate=args.learning_rate,
        regularization=args.regularization,
        seed=args.seed,
    )
    rows = recommend(user_positive, user_factors, item_factors, item_category, args.topn)
    write_rows(args.output, rows)
    print(f"users={len(user_positive)} items={len(items)} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
