import argparse
import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ML_DIR = ROOT / "ml-recommend"


def parse_args():
    parser = argparse.ArgumentParser(description="Run the offline recommendation training, evaluation, and SQL export pipeline.")
    parser.add_argument("--events", default="ml-recommend/data/sampled_events.csv", help="Sampled behavior CSV")
    parser.add_argument("--output-dir", default="ml-recommend/output", help="Output directory")
    parser.add_argument("--train-ratio", type=float, default=0.8, help="Per-user temporal training ratio")
    parser.add_argument("--topn", type=int, default=10)
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--negative-ratio", type=int, default=4)
    parser.add_argument("--mall-product-ids", default="37,26,46,45,44,43,42,41,40,39")
    parser.add_argument("--max-users", type=int, default=30)
    parser.add_argument("--dry-run", action="store_true", help="Print commands without executing them")
    return parser.parse_args()


def command_text(command):
    return " ".join(str(part) for part in command)


def run_step(title, command, dry_run):
    print(f"\n[{title}]")
    print(command_text(command))
    if dry_run:
        return
    subprocess.run(command, cwd=ROOT, check=True)


def main():
    args = parse_args()
    events = args.events
    output_dir = args.output_dir
    os.makedirs(ROOT / output_dir, exist_ok=True)

    itemcf_csv = f"{output_dir}/itemcf_recommendations.csv"
    ncf_csv = f"{output_dir}/ncf_recommendations.csv"
    deepfm_csv = f"{output_dir}/deepfm_recommendations.csv"

    commands = [
        (
            "train ItemCF",
            [
                sys.executable,
                str(ML_DIR / "train_itemcf.py"),
                "--events",
                events,
                "--output",
                itemcf_csv,
                "--topn",
                str(args.topn),
                "--train-ratio",
                str(args.train_ratio),
            ],
        ),
        (
            "train NCF",
            [
                sys.executable,
                str(ML_DIR / "train_ncf.py"),
                "--events",
                events,
                "--output",
                ncf_csv,
                "--model-output",
                f"{output_dir}/ncf_model.pt",
                "--epochs",
                str(args.epochs),
                "--negative-ratio",
                str(args.negative_ratio),
                "--topn",
                str(args.topn),
                "--train-ratio",
                str(args.train_ratio),
            ],
        ),
        (
            "train DeepFM",
            [
                sys.executable,
                str(ML_DIR / "train_deepfm.py"),
                "--events",
                events,
                "--output",
                deepfm_csv,
                "--model-output",
                f"{output_dir}/deepfm_model.pt",
                "--epochs",
                str(args.epochs),
                "--negative-ratio",
                str(args.negative_ratio),
                "--topn",
                str(args.topn),
                "--train-ratio",
                str(args.train_ratio),
            ],
        ),
        (
            "evaluate algorithms",
            [
                sys.executable,
                str(ML_DIR / "evaluate_recommendations.py"),
                "--events",
                events,
                "--recommendations",
                f"model_deepfm={deepfm_csv}",
                "--recommendations",
                f"model_ncf={ncf_csv}",
                "--recommendations",
                f"model_itemcf={itemcf_csv}",
                "--output",
                f"{output_dir}/recommend_evaluation.csv",
                "--sql",
                f"{output_dir}/recommend_evaluation.sql",
                "--k",
                str(args.topn),
                "--test-ratio",
                str(1 - args.train_ratio),
                "--positive-events",
                "fav,cart,pay",
            ],
        ),
        (
            "export ItemCF SQL",
            [
                sys.executable,
                str(ML_DIR / "export_recommend_sql.py"),
                "--recommendations",
                itemcf_csv,
                "--output",
                f"{output_dir}/model_recommend_result.sql",
                "--recommend-type",
                "model_itemcf",
                "--mall-product-ids",
                args.mall_product_ids,
                "--max-users",
                str(args.max_users),
                "--reason-prefix",
                "基于淘宝行为数据训练的ItemCF协同过滤推荐",
            ],
        ),
        (
            "export NCF SQL",
            [
                sys.executable,
                str(ML_DIR / "export_recommend_sql.py"),
                "--recommendations",
                ncf_csv,
                "--output",
                f"{output_dir}/model_ncf_recommend_result.sql",
                "--recommend-type",
                "model_ncf",
                "--mall-product-ids",
                args.mall_product_ids,
                "--max-users",
                str(args.max_users),
                "--reason-prefix",
                "基于淘宝行为数据训练的NCF神经协同过滤推荐",
            ],
        ),
        (
            "export DeepFM SQL",
            [
                sys.executable,
                str(ML_DIR / "export_recommend_sql.py"),
                "--recommendations",
                deepfm_csv,
                "--output",
                f"{output_dir}/model_deepfm_recommend_result.sql",
                "--recommend-type",
                "model_deepfm",
                "--mall-product-ids",
                args.mall_product_ids,
                "--max-users",
                str(args.max_users),
                "--reason-prefix",
                "基于淘宝行为数据训练的DeepFM特征交叉推荐",
            ],
        ),
    ]

    for title, command in commands:
        run_step(title, command, args.dry_run)

    print("\npipeline_finished=true")


if __name__ == "__main__":
    main()
