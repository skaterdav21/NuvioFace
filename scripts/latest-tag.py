#!/usr/bin/env python3
"""Prints the newest release tag of a git remote: 1.2.0 beats 1.2.0-beta.3 beats 1.2.0-beta.2."""
import re
import subprocess
import sys

out = subprocess.run(["git", "ls-remote", "--tags", "--refs", sys.argv[1]], capture_output=True, text=True, check=True).stdout
tags = [line.split("refs/tags/", 1)[1] for line in out.splitlines() if "refs/tags/" in line]

def key(tag):
    m = re.match(r"^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$", tag)
    if not m or m.group(1) == "0":
        return None
    nums = tuple(int(x or 0) for x in m.group(1, 2, 3))
    pre = m.group(4)
    if pre is None:
        return nums + (1, ())
    parts = tuple((0, int(p), "") if p.isdigit() else (1, 0, p) for p in re.split(r"[.-]", pre))
    return nums + (0, parts)

ranked = sorted((k, t) for t in tags if (k := key(t)) is not None)
if not ranked:
    sys.exit("no release tags")
print(ranked[-1][1])
