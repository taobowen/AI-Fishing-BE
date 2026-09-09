#!/usr/bin/env bash
# Operational Hybrid cascade for the four catalog lakes.
# Does not change YAML defaults. Generate Plan is not invoked here.
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080}"
ADMIN_USER_ID="${ADMIN_USER_ID:-11111111-1111-1111-1111-111111111111}"
OUT_DIR="${OUT_DIR:-docs/reports/ops}"
mkdir -p "$OUT_DIR"

lakes=(
  "head:44444444-4444-4444-4444-444444444444"
  "rice:44444444-4444-4444-4444-444444444445"
  "scugog:44444444-4444-4444-4444-444444444446"
  "simcoe:44444444-4444-4444-4444-444444444447"
)

process_one() {
  local name="$1"
  local id="$2"
  local out="$OUT_DIR/${name}-hybrid.json"
  echo "${name} HYBRID start $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  local start
  start="$(date +%s)"
  curl -sS -m 14400 -H "X-User-Id: ${ADMIN_USER_ID}" \
    -X POST "${API_BASE}/api/v1/admin/lakes/${id}/process?pipeline=HYBRID" \
    -o "$out" -w "http_code=%{http_code}\n"
  local end elapsed
  end="$(date +%s)"
  elapsed="$((end - start))"
  echo "${name} HYBRID end $(date -u +%Y-%m-%dT%H:%M:%SZ) elapsed=${elapsed}s"
  echo "$elapsed" > "$OUT_DIR/${name}-hybrid-elapsed-seconds.txt"
}

if [[ $# -gt 0 ]]; then
  process_one "$1" "$2"
  exit 0
fi

for entry in "${lakes[@]}"; do
  process_one "${entry%%:*}" "${entry##*:}"
done
