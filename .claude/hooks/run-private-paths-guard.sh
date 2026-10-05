#!/usr/bin/env bash
# Runs private-paths-guard.mjs, failing closed. A worktree cut from a branch without the guard has no
# copy of it under $CLAUDE_PROJECT_DIR, and a hook command that cannot start exits with a code Claude
# Code treats as non-blocking, so the call would go through unguarded. So: the worktree's own copy,
# else the main checkout's, else refuse.
input=$(cat)
guard="$CLAUDE_PROJECT_DIR/.claude/hooks/private-paths-guard.mjs"
if [ ! -f "$guard" ]; then
  common=$(git -C "$CLAUDE_PROJECT_DIR" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)
  [ -n "$common" ] && guard="$(dirname "$common")/.claude/hooks/private-paths-guard.mjs"
fi
if [ ! -f "$guard" ]; then
  echo "Refused: the private-paths guard could not be found, so no tool call is allowed until it is." >&2
  exit 2
fi
printf '%s' "$input" | node "$guard"
status=$?
# Anything but a clean pass or a refusal is the guard failing to run: refuse rather than let it through.
[ "$status" -eq 0 ] || [ "$status" -eq 2 ] || { echo "Refused: the private-paths guard failed (exit $status)." >&2; exit 2; }
exit "$status"
