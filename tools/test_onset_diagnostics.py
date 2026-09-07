#!/usr/bin/env python3
"""Run host detector parity/evidence checks without Gradle, Android or network access."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
GOLDEN = ROOT / "tests/native/onset-baseline.csv"
BASELINE_HASHES = {
    "OnsetDetector.cpp": "81f9fb698ca3c8210752e834ed9f4d40bf2d259f044af48070507f299f363267",
    "OnsetDetector.h": "ff8dff01c7bfd1633b0038a090c7ee79be08f404a1a3b8af6975fe64ebcd1638",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-dir", type=Path, help="Verify oracle using original source; never rewrites golden")
    parser.add_argument("--sanitize", action="store_true", help="Enable address/undefined-behavior sanitizers")
    args = parser.parse_args()
    build = Path(tempfile.mkdtemp(prefix="onset-regression-"))
    source = args.baseline_dir or ROOT / "app/src/main/cpp"
    if args.baseline_dir:
        for name, digest in BASELINE_HASHES.items():
            assert hashlib.sha256((source / name).read_bytes()).hexdigest() == digest, name
    command = [
        "g++", "-std=c++17", "-O2", "-ffp-contract=off", "-Wall", "-Wextra", "-Werror",
        "-I", str(source), str(ROOT / "tests/native/onset_regression.cpp"),
        str(source / "OnsetDetector.cpp"), "-o", str(build / "regression"),
    ]
    if not args.baseline_dir:
        command.append("-DCHECK_DIAGNOSTICS")
    if args.sanitize:
        command += ["-fsanitize=address,undefined", "-fno-omit-frame-pointer"]
    subprocess.run(command, check=True)
    actual = subprocess.check_output([str(build / "regression")])
    (build / "actual.csv").write_bytes(actual)
    if actual != GOLDEN.read_bytes():
        raise SystemExit(f"FAIL: baseline mismatch; compare {build / 'actual.csv'} with {GOLDEN}")
    print(f"PASS: {len(actual.splitlines())} fixture configurations; exact legacy events/snapshots.")
    if not args.baseline_dir:
        print("PASS: event-local sample replay, raw frames/gaps, reset/drop provenance, all 24 wire slots.")
    print(f"Artifacts retained: {build}")


if __name__ == "__main__":
    main()
