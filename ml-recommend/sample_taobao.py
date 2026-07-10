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


def sample_events(args, cutoff_ts, end_ts):
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
            if len(row) < 5:
                continue
            user_id, item_id, category_id, behavior, timestamp = row[:5]
            event_type = EVENT_MAP.get(behavior)
            if not event_type:
                continue
            try:
                ts = int(timestamp)
            except ValueError:
                continue
            if ts < cutoff_ts or (end_ts is not None and ts > end_ts):
                continue

            known_user = user_id in users
            known_item = item_id in items
            if not known_user and len(users) >= args.max_users:
                continue
            if not known_item and len(items) >= args.max_items:
                continue

            users.add(user_id)
            items.add(item_id)
            writer.writerow([user_id, item_id, category_id, event_type, ts])
            event_counts[event_type] += 1
            written += 1

            if written >= args.max_events:
                break

    return output_path, len(users), len(items), written, event_counts


def main():
    args = parse_args()
    latest_ts = args.end_ts or find_latest_timestamp(args.input)
    if latest_ts <= 0:
        raise SystemExit("No valid timestamp found in input file.")
    cutoff_ts = latest_ts - args.recent_days * 86400
    if args.start_ts is not None:
        cutoff_ts = max(cutoff_ts, args.start_ts)
    output_path, user_count, item_count, event_count, event_counts = sample_events(args, cutoff_ts, args.end_ts)
    print(f"latest_timestamp={latest_ts}")
    print(f"cutoff_timestamp={cutoff_ts}")
    print(f"output={output_path}")
    print(f"users={user_count} items={item_count} events={event_count}")
    print("event_counts=" + ",".join(f"{k}:{event_counts[k]}" for k in sorted(event_counts)))


if __name__ == "__main__":
    main()
