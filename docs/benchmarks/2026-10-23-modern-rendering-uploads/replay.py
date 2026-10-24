#!/usr/bin/env python3
import hashlib
import json
import shutil
import sys
from pathlib import Path

def sha256_file(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def replay(out_dir):
    script_dir = Path(__file__).resolve().parent
    out_dir = Path(out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest_path = script_dir / "raw-manifest.json"
    if not manifest_path.exists():
        raise FileNotFoundError(f"Missing {manifest_path}")

    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    verified_raw = 0
    verified_logs = 0
    for rel_path, expected_hash in manifest.items():
        src_file = script_dir / rel_path
        if not src_file.exists():
            raise FileNotFoundError(f"Source file missing: {src_file}")
        actual_hash = sha256_file(src_file)
        if actual_hash != expected_hash:
            raise ValueError(f"Hash mismatch for {rel_path}: expected {expected_hash}, got {actual_hash}")

        dst_file = out_dir / rel_path
        dst_file.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src_file, dst_file)
        if rel_path.startswith("raw/"):
            verified_raw += 1
        elif rel_path.startswith("logs/"):
            verified_logs += 1

    shutil.copy2(script_dir / "launches.json", out_dir / "launches.json")
    shutil.copy2(script_dir / "evaluate.py", out_dir / "evaluate.py")

    import evaluate
    out_eval = evaluate.evaluate_archive(out_dir)

    orig_eval_path = script_dir / "evaluation.json"
    structurally_identical = False
    if orig_eval_path.exists():
        with open(orig_eval_path, "r", encoding="utf-8") as f:
            orig_eval = json.load(f)
        structurally_identical = (out_eval == orig_eval)

    receipt = {
        "raw_files_verified": verified_raw,
        "logs_verified": verified_logs,
        "evaluation_structurally_identical": structurally_identical,
        "gates_pass": out_eval["gates_pass"],
        "note": "JSON numeric/structural equality; no cross-platform byte-identity claim."
    }
    print(json.dumps(receipt, indent=2))
    return receipt

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python replay.py <OUTPUT_DIR>")
        sys.exit(1)
    replay(sys.argv[1])
