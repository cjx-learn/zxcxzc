import argparse
import csv
import os
from collections import Counter


EVENT_MAP = {
    "pv": "view",
    "fav": "fav",
    "cart": "cart",
    "buy": "pay",
}


def parse_args():
    parser = argparse.ArgumentParser(description="Sample Taobao UserBehavior.csv for local recommendation training.")
    parser.add_argument("--input", required=True, help="Path to UserBehavior.csv")
    parser.add_argument("--output-dir", required=True, help="Output directory")
    parser.add_argument("--strategy", choices=["first", "active"], default="first", help="Sampling strategy")
    parser.add_argument("--max-users", type=int, default=5000)
    parser.add_argument("--max-items", type=int, default=10000)
    parser.add_argument("--max-events", type=int, default=300000)
    parser.add_argument("--recent-days", type=int, default=9)
    parser.add_argument("--start-ts", type=int, default=None, help="Optional inclusive start timestamp")
    parser.add_argument("--end-ts", type=int, default=None, help="Optional inclusive end timestamp")
    return parser.parse_args()


def find_latest_timestamp(path):
    latest = 0
    with open(path, "r", encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        for row in reader:
            if len(row) < 5:
                continue
            try:
                ts = int(row[4])
            except ValueError:
                continue
            if ts > latest:
                latest = ts
    return latest


def parse_valid_event(row, cutoff_ts, end_ts):
    if len(row) < 5:
        return None
    user_id, item_id, category_id, behavior, timestamp = row[:5]
    event_type = EVENT_MAP.get(behavior)
    if not event_type:
        return None
    try:
        ts = int(timestamp)
    except ValueError:
        return None
    if ts < cutoff_ts or (end_ts is not None and ts > end_ts):
        return None
    return user_id, item_id, category_id, event_type, ts


def write_sampled_events(args, cutoff_ts, end_ts, selected_users=None, selected_items=None):
    users = set()
    items = set()
    event_counts = Counter()
    output_path = os.path.join(args.output_dir, "sampled_events.csv")
    os.makedirs(args.output_dir, exist_ok=True)

    with open(args.input, "r", encoding="utf-8", newline="") as src, open(output_path, "w", encoding="utf-8", newline="") as dst:
        reader = csv.reader(src)
        writer = csv.writer(dst)
        writer.writerow(["user_id", "item_id", "category_id", "event_type", "timestamp"])

        written = 0
        for row in reader:
            parsed = parse_valid_event(row, cutoff_ts, end_ts)
            if parsed is None:
                continue
            user_id, item_id, category_id, event_type, ts = parsed
            if selected_users is not None and user_id not in selected_users:
                continue
            if selected_items is not None and item_id not in selected_items:
                continue

            known_user = user_id in users
            known_item = item_id in items
            if selected_users is None and not known_user and len(users) >= args.max_users:
                continue
            if selected_items is None and not known_item and len(items) >= args.max_items:
                continue

            users.add(user_id)
            items.add(item_id)
            writer.writerow([user_id, item_id, category_id, event_type, ts])
            event_counts[event_type] += 1
            written += 1

            if written >= args.max_events:
                break

    return output_path, len(users), len(items), written, event_counts


def sample_events_first(args, cutoff_ts, end_ts):
    return write_sampled_events(args, cutoff_ts, end_ts)


def sample_events_active(args, cutoff_ts, end_ts):
    user_counts = Counter()
    item_counts = Counter()
    with open(args.input, "r", encoding="utf-8", newline="") as src:
        reader = csv.reader(src)
        for row in reader:
            parsed = parse_valid_event(row, cutoff_ts, end_ts)
            if parsed is None:
                continue
            user_id, item_id, _, _, _ = parsed
            user_counts[user_id] += 1
            item_counts[item_id] += 1

    selected_users = {
        user_id for user_id, _ in sorted(user_counts.items(), key=lambda item: (-item[1], item[0]))[:args.max_users]
    }
    selected_items = {
        item_id for item_id, _ in sorted(item_counts.items(), key=lambda item: (-item[1], item[0]))[:args.max_items]
    }
    return write_sampled_events(args, cutoff_ts, end_ts, selected_users, selected_items)


def main():
    args = parse_args()
    latest_ts = args.end_ts or find_latest_timestamp(args.input)
    if latest_ts <= 0:
        raise SystemExit("No valid timestamp found in input file.")
    cutoff_ts = latest_ts - args.recent_days * 86400
    if args.start_ts is not None:
        cutoff_ts = max(cutoff_ts, args.start_ts)
    if args.strategy == "active":
        output_path, user_count, item_count, event_count, event_counts = sample_events_active(args, cutoff_ts, args.end_ts)
    else:
        output_path, user_count, item_count, event_count, event_counts = sample_events_first(args, cutoff_ts, args.end_ts)
    print(f"latest_timestamp={latest_ts}")
    print(f"cutoff_timestamp={cutoff_ts}")
    print(f"output={output_path}")
    print(f"users={user_count} items={item_count} events={event_count}")
    print("event_counts=" + ",".join(f"{k}:{event_counts[k]}" for k in sorted(event_counts)))


if __name__ == "__main__":
    main()
