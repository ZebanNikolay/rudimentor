"""Read/write code in Nikolai's PERSONAL GitHub repo (ZebanNikolay) via the API.

Git-over-HTTPS can't traverse the sandbox TLS proxy, so we use the Git Data API.
Always run with api_credentials=["custom-cred:api.github.com"].

Commands:
  pull  <repo> <ref> <dest_dir>        download a ref's tree into dest_dir
  push  <repo> <branch> <src_dir> <msg>  commit src_dir contents onto branch
  ls    <repo>                          list branches
"""
import base64
import hashlib
import io
import json
import os
import subprocess
import sys
import time

OWNER = "ZebanNikolay"
API = "https://api.github.com"
SKIP_DIRS = {".git", "build", ".gradle", ".idea", "node_modules", "__pycache__"}
THROTTLE = float(os.environ.get("GH_THROTTLE", "0.7"))


def req(method, path, body=None):
    # urllib hangs on the sandbox's https:// proxy; curl handles it correctly.
    proxy = os.environ.get("HTTPS_PROXY", "")
    cmd = ["curl", "-sS", "--fail-with-body"]
    if proxy:
        cmd += ["-x", proxy]
    cmd += [
        "-X", method,
        "-H", "Accept: application/vnd.github+json",
        f"{API}{path}",
    ]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "--data-binary", "@-"]
    payload = json.dumps(body).encode() if body is not None else None
    last = ""
    for attempt in range(6):
        p = subprocess.run(cmd, input=payload, capture_output=True)
        if p.returncode == 0:
            time.sleep(THROTTLE)  # the sandbox proxy 429s on bursts
            return json.loads(p.stdout) if p.stdout.strip() else {}
        last = (p.stdout.decode()[:300] + " | " + p.stderr.decode()[:200]).strip()
        time.sleep(2 ** attempt)
    raise RuntimeError(f"{method} {path} failed: {last}")


def pull(repo, ref, dest):
    # codeload.github.com is not reachable through the sandbox proxy, so walk
    # the tree via the API and fetch blobs individually.
    safe = ref.replace("/", "%2F")
    tree = req("GET", f"/repos/{OWNER}/{repo}/git/trees/{safe}?recursive=1")["tree"]
    n = 0
    for e in tree:
        if e["type"] != "blob":
            continue
        blob = req("GET", f"/repos/{OWNER}/{repo}/git/blobs/{e['sha']}")
        out = os.path.join(dest, e["path"])
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as f:
            f.write(base64.b64decode(blob["content"]))
        n += 1
    print(f"pulled {OWNER}/{repo}@{ref} -> {dest} ({n} files)")


def collect(src):
    out = []
    for dirpath, dirnames, filenames in os.walk(src):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, src)
            out.append((rel, full))
    return sorted(out)


def blob_sha(content):
    header = f"blob {len(content)}\0".encode()
    return hashlib.sha1(header + content).hexdigest()


def push(repo, branch, src, message):
    base = req("GET", f"/repos/{OWNER}/{repo}/git/ref/heads/{branch}")["object"]["sha"]
    base_commit = req("GET", f"/repos/{OWNER}/{repo}/git/commits/{base}")
    base_tree_sha = base_commit["tree"]["sha"]
    base_tree = req("GET", f"/repos/{OWNER}/{repo}/git/trees/{base_tree_sha}?recursive=1")["tree"]
    remote_blobs = {
        entry["path"]: (entry["sha"], entry["mode"])
        for entry in base_tree
        if entry["type"] == "blob"
    }
    tree = []
    local_paths = set()
    for rel, full in collect(src):
        local_paths.add(rel)
        with open(full, "rb") as f:
            content = f.read()
        mode = "100755" if os.access(full, os.X_OK) else "100644"
        existing = remote_blobs.get(rel)
        if existing == (blob_sha(content), mode):
            continue
        blob = req(
            "POST",
            f"/repos/{OWNER}/{repo}/git/blobs",
            {"content": base64.b64encode(content).decode(), "encoding": "base64"},
        )
        tree.append({"path": rel, "mode": mode, "type": "blob", "sha": blob["sha"]})
    for deleted_path in sorted(remote_blobs.keys() - local_paths):
        tree.append({"path": deleted_path, "mode": "100644", "type": "blob", "sha": None})
    if not tree:
        print(f"no changes for {OWNER}/{repo}@{branch}")
        return
    new_tree = req(
        "POST",
        f"/repos/{OWNER}/{repo}/git/trees",
        {"base_tree": base_tree_sha, "tree": tree},
    )
    commit = req(
        "POST",
        f"/repos/{OWNER}/{repo}/git/commits",
        {"message": message, "tree": new_tree["sha"], "parents": [base]},
    )
    req(
        "PATCH",
        f"/repos/{OWNER}/{repo}/git/refs/heads/{branch}",
        {"sha": commit["sha"], "force": False},
    )
    print(f"pushed {commit['sha'][:8]} to {OWNER}/{repo}@{branch} ({len(tree)} changed files)")


def ls(repo):
    for b in req("GET", f"/repos/{OWNER}/{repo}/branches"):
        print(b["name"], b["commit"]["sha"][:8])


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"pull": pull, "push": push, "ls": ls}[cmd](*args)
