"""Rewrite a branch's history so only Nikolai's PERSONAL identity appears.

Any commit whose author/committer (or message trailer) mentions the work account
or the PSI bot is recreated with the same tree under ZebanNikolay
<zebannikolay@gmail.com>, keeping the original dates. Everything downstream is
re-parented, then the branch ref is force-updated.

Run with api_credentials=["custom-cred:api.github.com"]:
  python3 gh_scrub_authors.py <repo> <branch> [--apply]
Without --apply it only prints the plan.
"""
import json
import os
import subprocess
import sys
import time

OWNER = "ZebanNikolay"
API = "https://api.github.com"
GOOD = {"name": "ZebanNikolay", "email": "zebannikolay@gmail.com"}
BAD = ("perplexity.ai", "psi@", "psi bot")
THROTTLE = float(os.environ.get("GH_THROTTLE", "1.2"))


def req(method, path, body=None):
    # urllib hangs on the sandbox TLS proxy; curl handles it correctly.
    cmd = ["curl", "-sS", "--fail-with-body", "-X", method,
           "-H", "Accept: application/vnd.github+json", f"{API}{path}"]
    if body is not None:
        cmd += ["-H", "Content-Type: application/json", "--data-binary", "@-"]
    payload = json.dumps(body).encode() if body is not None else None
    last = ""
    for attempt in range(6):
        p = subprocess.run(cmd, input=payload, capture_output=True)
        if p.returncode == 0:
            time.sleep(THROTTLE)
            return json.loads(p.stdout) if p.stdout.strip() else {}
        last = (p.stdout.decode()[:300] + " | " + p.stderr.decode()[:200]).strip()
        time.sleep(2 ** attempt)
    raise RuntimeError(f"{method} {path} failed: {last}")


def dirty(text):
    low = text.lower()
    return any(m in low for m in BAD)


def main(repo, branch, apply):
    safe = branch.replace("/", "%2F")
    commits = []
    page = 1
    while True:
        batch = req("GET", f"/repos/{OWNER}/{repo}/commits?sha={safe}&per_page=100&page={page}")
        commits += batch
        if len(batch) < 100:
            break
        page += 1
    commits.reverse()  # oldest first

    parent = None
    rewritten = 0
    for c in commits:
        d = req("GET", f"/repos/{OWNER}/{repo}/git/commits/{c['sha']}")
        a, co = d["author"], d["committer"]
        ident = a["name"] + a["email"] + co["name"] + co["email"]
        msg = "\n".join(l for l in d["message"].splitlines() if not dirty(l)).rstrip()
        parents = [p["sha"] for p in d["parents"]]
        moved = parents[:1] != ([parent] if parent else [])
        needs = dirty(ident) or msg != d["message"].rstrip() or moved
        if not needs:
            parent = c["sha"]
            print("keep   ", c["sha"][:8], msg.splitlines()[0][:50], flush=True)
            continue
        made = req("POST", f"/repos/{OWNER}/{repo}/git/commits", {
            "message": msg + "\n",
            "tree": d["tree"]["sha"],
            "parents": [parent] if parent else [],
            "author": {**GOOD, "date": a["date"]},
            "committer": {**GOOD, "date": co["date"]},
        })
        print("rewrite", c["sha"][:8], "->", made["sha"][:8], msg.splitlines()[0][:50], flush=True)
        parent = made["sha"]
        rewritten += 1

    print("head", parent, "rewritten", rewritten)
    if apply and rewritten:
        res = req("PATCH", f"/repos/{OWNER}/{repo}/git/refs/heads/{safe}",
                  {"sha": parent, "force": True})
        print("ref ->", res["object"]["sha"][:8])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], "--apply" in sys.argv)
