import argparse
import csv
import os
import random
from collections import Counter, defaultdict

import torch
from torch import nn
from torch.utils.data import DataLoader, TensorDataset


EVENT_WEIGHT = {
    "view": 1.0,
    "fav": 3.0,
    "cart": 4.0,
    "pay": 5.0,
}


class NCF(nn.Module):
    def __init__(self, user_count, item_count, embedding_dim=32, hidden_dims=(64, 32)):
        super().__init__()
        self.user_embedding = nn.Embedding(user_count, embedding_dim)
        self.item_embedding = nn.Embedding(item_count, embedding_dim)
        layers = []
        input_dim = embedding_dim * 2
        for hidden_dim in hidden_dims:
            layers.append(nn.Linear(input_dim, hidden_dim))
            layers.append(nn.ReLU())
            layers.append(nn.Dropout(0.15))
            input_dim = hidden_dim
        layers.append(nn.Linear(input_dim, 1))
        self.mlp = nn.Sequential(*layers)

    def forward(self, user_ids, item_ids):
        user_vec = self.user_embedding(user_ids)
        item_vec = self.item_embedding(item_ids)
        features = torch.cat([user_vec, item_vec], dim=1)
        return self.mlp(features).squeeze(1)


def parse_args():
    parser = argparse.ArgumentParser(description="Train Neural Collaborative Filtering on sampled Taobao events.")
    parser.add_argument("--events", required=True, help="sampled_events.csv")
    parser.add_argument("--output", required=True, help="Recommendation CSV output")
    parser.add_argument("--model-output", default="ml-recommend/output/ncf_model.pt")
    parser.add_argument("--embedding-dim", type=int, default=32)
    parser.add_argument("--epochs", type=int, default=6)
    parser.add_argument("--batch-size", type=int, default=2048)
    parser.add_argument("--learning-rate", type=float, default=0.002)
    parser.add_argument("--negative-ratio", type=int, default=3)
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--seed", type=int, default=20260707)
    return parser.parse_args()


def load_events(path):
    user_positive = defaultdict(dict)
    item_category = {}
    item_popularity = Counter()
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            user_id = row["user_id"]
            item_id = row["item_id"]
            event_type = row["event_type"]
            weight = EVENT_WEIGHT.get(event_type, 0.0)
            if weight <= 0:
                continue
            user_positive[user_id][item_id] = max(user_positive[user_id].get(item_id, 0.0), min(weight / 5.0, 1.0))
            item_category[item_id] = row["category_id"]
            item_popularity[item_id] += weight
    return user_positive, item_category, item_popularity


def build_indices(user_positive, item_popularity):
    users = sorted(user_positive)
    items = [item for item, _ in item_popularity.most_common()]
    user_to_idx = {user_id: idx for idx, user_id in enumerate(users)}
    item_to_idx = {item_id: idx for idx, item_id in enumerate(items)}
    idx_to_user = {idx: user_id for user_id, idx in user_to_idx.items()}
    idx_to_item = {idx: item_id for item_id, idx in item_to_idx.items()}
    return users, items, user_to_idx, item_to_idx, idx_to_user, idx_to_item


def build_training_tensors(user_positive, users, items, user_to_idx, item_to_idx, negative_ratio, seed):
    rng = random.Random(seed)
    item_set = set(items)
    user_tensor = []
    item_tensor = []
    label_tensor = []

    for user_id in users:
        positives = user_positive[user_id]
        positive_items = set(positives)
        negative_pool = list(item_set - positive_items)
        if not negative_pool:
            continue
        for item_id, label_value in positives.items():
            user_tensor.append(user_to_idx[user_id])
            item_tensor.append(item_to_idx[item_id])
            label_tensor.append(float(label_value))
            for _ in range(negative_ratio):
                neg_item = rng.choice(negative_pool)
                user_tensor.append(user_to_idx[user_id])
                item_tensor.append(item_to_idx[neg_item])
                label_tensor.append(0.0)

    return (
        torch.tensor(user_tensor, dtype=torch.long),
        torch.tensor(item_tensor, dtype=torch.long),
        torch.tensor(label_tensor, dtype=torch.float32),
    )


def train_model(model, tensors, epochs, batch_size, learning_rate):
    dataset = TensorDataset(*tensors)
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = nn.BCEWithLogitsLoss()

    model.train()
    for epoch in range(1, epochs + 1):
        total_loss = 0.0
        batch_count = 0
        for user_ids, item_ids, labels in loader:
            optimizer.zero_grad()
            logits = model(user_ids, item_ids)
            loss = criterion(logits, labels)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            batch_count += 1
        print(f"epoch={epoch} avg_loss={total_loss / max(batch_count, 1):.6f}")


def generate_recommendations(model, user_positive, users, items, user_to_idx, item_to_idx, item_category, topn):
    rows = []
    model.eval()
    all_item_indices = torch.arange(len(items), dtype=torch.long)
    with torch.no_grad():
        for user_id in users:
            seen = set(user_positive[user_id])
            candidate_items = [item for item in items if item not in seen]
            if not candidate_items:
                continue
            user_indices = torch.full((len(candidate_items),), user_to_idx[user_id], dtype=torch.long)
            item_indices = torch.tensor([item_to_idx[item] for item in candidate_items], dtype=torch.long)
            scores = torch.sigmoid(model(user_indices, item_indices)).tolist()
            ranked = sorted(zip(candidate_items, scores), key=lambda item: item[1], reverse=True)[:topn]
            for rank_no, (item_id, score) in enumerate(ranked, start=1):
                rows.append({
                    "user_id": user_id,
                    "external_item_id": item_id,
                    "category_id": item_category.get(item_id, ""),
                    "score": f"{score:.6f}",
                    "rank_no": rank_no,
                    "recommend_type": "model_ncf",
                    "reason": "NCF神经协同过滤根据用户Embedding和商品Embedding预测兴趣概率",
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

    user_positive, item_category, item_popularity = load_events(args.events)
    users, items, user_to_idx, item_to_idx, idx_to_user, idx_to_item = build_indices(user_positive, item_popularity)
    tensors = build_training_tensors(
        user_positive=user_positive,
        users=users,
        items=items,
        user_to_idx=user_to_idx,
        item_to_idx=item_to_idx,
        negative_ratio=args.negative_ratio,
        seed=args.seed,
    )
    model = NCF(len(users), len(items), embedding_dim=args.embedding_dim)
    train_model(model, tensors, args.epochs, args.batch_size, args.learning_rate)
    rows = generate_recommendations(model, user_positive, users, items, user_to_idx, item_to_idx, item_category, args.topn)
    write_rows(args.output, rows)
    os.makedirs(os.path.dirname(args.model_output), exist_ok=True)
    torch.save({
        "model_state_dict": model.state_dict(),
        "user_to_idx": user_to_idx,
        "item_to_idx": item_to_idx,
        "embedding_dim": args.embedding_dim,
    }, args.model_output)
    print(f"users={len(users)} items={len(items)} samples={len(tensors[2])} recommendations={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
