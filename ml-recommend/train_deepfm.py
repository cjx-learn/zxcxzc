import argparse
import csv
import os
import random
from collections import Counter, defaultdict
from datetime import datetime

import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset


EVENT_WEIGHT = {
    "view": 1.0,
    "search": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "order": 4.5,
    "pay": 5.0,
}


class DeepFM(nn.Module):
    def __init__(self, field_dims, embedding_dim=16, hidden_dims=(128, 64)):
        super().__init__()
        self.field_dims = field_dims
        self.offsets = torch.tensor([0] + field_dims[:-1]).cumsum(0)
        feature_count = sum(field_dims)

        self.linear = nn.Embedding(feature_count, 1)
        self.fm_embedding = nn.Embedding(feature_count, embedding_dim)

        layers = []
        input_dim = len(field_dims) * embedding_dim
        for hidden_dim in hidden_dims:
            layers.append(nn.Linear(input_dim, hidden_dim))
            layers.append(nn.ReLU())
            layers.append(nn.Dropout(0.2))
            input_dim = hidden_dim
        layers.append(nn.Linear(input_dim, 1))
        self.deep = nn.Sequential(*layers)

    def forward(self, x):
        offsets = self.offsets.to(x.device)
        x = x + offsets
        linear_part = self.linear(x).sum(dim=1).squeeze(1)
        embeddings = self.fm_embedding(x)
        square_of_sum = embeddings.sum(dim=1) ** 2
        sum_of_square = (embeddings ** 2).sum(dim=1)
        fm_part = 0.5 * (square_of_sum - sum_of_square).sum(dim=1)
        deep_part = self.deep(embeddings.flatten(start_dim=1)).squeeze(1)
        return linear_part + fm_part + deep_part


def parse_args():
    parser = argparse.ArgumentParser(description="Train a lightweight DeepFM recommender from sampled Taobao events.")
    parser.add_argument("--events", required=True, help="sampled_events.csv")
    parser.add_argument("--output", required=True, help="Recommendation CSV output")
    parser.add_argument("--model-output", default="ml-recommend/output/deepfm_model.pt")
    parser.add_argument("--embedding-dim", type=int, default=16)
    parser.add_argument("--epochs", type=int, default=6)
    parser.add_argument("--batch-size", type=int, default=2048)
    parser.add_argument("--learning-rate", type=float, default=0.0015)
    parser.add_argument("--negative-ratio", type=int, default=3)
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--seed", type=int, default=20260707)
    return parser.parse_args()


def bucket(value, boundaries):
    for idx, boundary in enumerate(boundaries):
        if value <= boundary:
            return idx
    return len(boundaries)


def event_context(timestamp):
    dt = datetime.fromtimestamp(int(timestamp))
    return dt.hour, dt.weekday()


def load_events(path):
    user_positive = defaultdict(dict)
    user_strength = Counter()
    item_strength = Counter()
    item_category = {}
    item_context = {}

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
            label_value = min(weight / 5.0, 1.0)
            user_positive[user_id][item_id] = max(user_positive[user_id].get(item_id, 0.0), label_value)
            user_strength[user_id] += weight
            item_strength[item_id] += weight
            item_category[item_id] = category_id
            item_context[item_id] = event_context(row["timestamp"])

    return user_positive, user_strength, item_strength, item_category, item_context


def build_vocab(values):
    return {value: idx for idx, value in enumerate(sorted(values))}


def build_feature_maps(user_positive, item_strength, item_category):
    users = sorted(user_positive)
    items = [item for item, _ in item_strength.most_common()]
    categories = sorted(set(item_category.values()))
    return {
        "user": build_vocab(users),
        "item": build_vocab(items),
        "category": build_vocab(categories),
        "user_bucket_count": 6,
        "item_bucket_count": 6,
        "hour_count": 24,
        "weekday_count": 7,
    }, users, items


def make_feature(user_id, item_id, maps, user_strength, item_strength, item_category, item_context):
    category_id = item_category.get(item_id, "__unknown__")
    hour, weekday = item_context.get(item_id, (0, 0))
    return [
        maps["user"][user_id],
        maps["item"][item_id],
        maps["category"].get(category_id, 0),
        bucket(user_strength[user_id], [2, 5, 10, 20, 50]),
        bucket(item_strength[item_id], [2, 5, 10, 20, 50]),
        hour,
        weekday,
    ]


def build_training_tensors(user_positive, users, items, maps, user_strength, item_strength, item_category, item_context, negative_ratio, seed):
    rng = random.Random(seed)
    item_set = set(items)
    features = []
    labels = []

    for user_id in users:
        positives = user_positive[user_id]
        seen = set(positives)
        negative_pool = list(item_set - seen)
        if not negative_pool:
            continue
        for item_id, label_value in positives.items():
            features.append(make_feature(user_id, item_id, maps, user_strength, item_strength, item_category, item_context))
            labels.append(float(label_value))
            for _ in range(negative_ratio):
                negative_item = rng.choice(negative_pool)
                features.append(make_feature(user_id, negative_item, maps, user_strength, item_strength, item_category, item_context))
                labels.append(0.0)

    return torch.tensor(features, dtype=torch.long), torch.tensor(labels, dtype=torch.float32)


def train_model(model, tensors, epochs, batch_size, learning_rate):
    dataset = TensorDataset(*tensors)
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = nn.BCEWithLogitsLoss()

    model.train()
    for epoch in range(1, epochs + 1):
        total_loss = 0.0
        batch_count = 0
        for features, labels in loader:
            optimizer.zero_grad()
            logits = model(features)
            loss = criterion(logits, labels)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            batch_count += 1
        print(f"epoch={epoch} avg_loss={total_loss / max(batch_count, 1):.6f}")


def generate_recommendations(model, user_positive, users, items, maps, user_strength, item_strength, item_category, item_context, topn):
    rows = []
    model.eval()
    with torch.no_grad():
        for user_id in users:
            seen = set(user_positive[user_id])
            candidate_items = [item for item in items if item not in seen]
            if not candidate_items:
                continue
            feature_rows = [
                make_feature(user_id, item_id, maps, user_strength, item_strength, item_category, item_context)
                for item_id in candidate_items
            ]
            features = torch.tensor(feature_rows, dtype=torch.long)
            scores = torch.sigmoid(model(features)).tolist()
            ranked = sorted(zip(candidate_items, scores), key=lambda item: item[1], reverse=True)[:topn]
            for rank_no, (item_id, score) in enumerate(ranked, start=1):
                rows.append({
                    "user_id": user_id,
                    "external_item_id": item_id,
                    "category_id": item_category.get(item_id, ""),
                    "score": f"{score:.6f}",
                    "rank_no": rank_no,
                    "recommend_type": "model_deepfm",
                    "reason": "DeepFM自动学习用户、商品、分类、活跃度、热度和时间上下文的特征交叉",
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
    torch.manual_seed(args.seed)

    user_positive, user_strength, item_strength, item_category, item_context = load_events(args.events)
    maps, users, items = build_feature_maps(user_positive, item_strength, item_category)
    tensors = build_training_tensors(
        user_positive=user_positive,
        users=users,
        items=items,
        maps=maps,
        user_strength=user_strength,
        item_strength=item_strength,
        item_category=item_category,
        item_context=item_context,
        negative_ratio=args.negative_ratio,
        seed=args.seed,
    )
    field_dims = [
        len(maps["user"]),
        len(maps["item"]),
        len(maps["category"]),
        maps["user_bucket_count"],
        maps["item_bucket_count"],
        maps["hour_count"],
        maps["weekday_count"],
    ]
    model = DeepFM(field_dims, embedding_dim=args.embedding_dim)
    train_model(model, tensors, args.epochs, args.batch_size, args.learning_rate)
    rows = generate_recommendations(model, user_positive, users, items, maps, user_strength, item_strength, item_category, item_context, args.topn)
    write_rows(args.output, rows)
    os.makedirs(os.path.dirname(args.model_output), exist_ok=True)
    torch.save({
        "model_state_dict": model.state_dict(),
        "field_dims": field_dims,
        "maps": maps,
        "embedding_dim": args.embedding_dim,
    }, args.model_output)
    print(f"users={len(users)} items={len(items)} samples={len(tensors[1])} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
