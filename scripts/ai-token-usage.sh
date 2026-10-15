#!/usr/bin/env bash
# AI-generated (Claude Code, Claude Opus 5.5)
# Sums Claude Code token usage for this project from local session logs.
# Each teammate runs this on their own machine and records the result in
# AI_USAGE.md. Requires jq.
# Usage: ./scripts/ai-token-usage.sh            (all sessions)
#        ./scripts/ai-token-usage.sh <session>  (one session id)
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "jq not found. Install it first (e.g. brew install jq)." >&2
  exit 1
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
log_dir="$HOME/.claude/projects/$(echo "$repo_root" | sed 's|[^A-Za-z0-9]|-|g')"

if [ ! -d "$log_dir" ]; then
  echo "No Claude Code logs found at $log_dir" >&2
  exit 1
fi

if [ $# -ge 1 ]; then
  files=("$log_dir/$1.jsonl")
else
  files=("$log_dir"/*.jsonl)
fi

for f in "${files[@]}"; do
  jq -s --arg s "$(basename "$f" .jsonl)" '
    [.[] | select(.type == "assistant" and .message.usage != null)
         | {id: .message.id, model: .message.model, u: .message.usage}]
    | unique_by(.id)
    | group_by(.model)[]
    | {
        session: $s,
        model: .[0].model,
        input: (map(.u.input_tokens // 0) | add),
        output: (map(.u.output_tokens // 0) | add),
        cache_write: (map(.u.cache_creation_input_tokens // 0) | add),
        cache_read: (map(.u.cache_read_input_tokens // 0) | add)
      }
    | . + {total: (.input + .output + .cache_write + .cache_read)}
  ' "$f"
done | jq -s '
  {
    sessions: .,
    grand_total: {
      input: (map(.input) | add),
      output: (map(.output) | add),
      cache_write: (map(.cache_write) | add),
      cache_read: (map(.cache_read) | add),
      total: (map(.total) | add)
    }
  }'
