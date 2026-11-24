#!/usr/bin/env bash
# Applies patches/nuvio-face.patch onto an official Nuvio checkout (current directory).
# Clean 3-way apply first; where both Nuvio and the patch added lines in the same spot, keep both
# (union). Anything that still doesn't fit fails the build, so a broken APK is never published.
set -euo pipefail
PATCH="$1"

if git apply --3way --whitespace=nowarn "$PATCH"; then
  echo "Patch applied cleanly."
  exit 0
fi

conflicted=$(git diff --name-only --diff-filter=U)
if [ -z "$conflicted" ]; then
  echo "::error::The patch didn't apply and there are no conflicts to resolve."
  exit 1
fi

echo "Resolving overlaps by keeping both sides:"
while IFS= read -r f; do
  [ -z "$f" ] && continue
  echo "  $f"
  tmp=$(mktemp -d)
  git show ":1:$f" > "$tmp/base" 2>/dev/null || : > "$tmp/base"
  git show ":2:$f" > "$tmp/ours"
  git show ":3:$f" > "$tmp/theirs"
  git merge-file --union "$tmp/ours" "$tmp/base" "$tmp/theirs" || true
  cp "$tmp/ours" "$f"
  git add "$f"
  rm -rf "$tmp"
done <<< "$conflicted"

if grep -rIl --exclude-dir=.git -e '^<<<<<<< ' -e '^>>>>>>> ' . | head -1 | grep -q .; then
  echo "::error::Conflict markers left after resolving."
  exit 1
fi
echo "::warning::Nuvio changed files the patch also touches: $(echo $conflicted | tr '\n' ' '). Merged by keeping both sides."
