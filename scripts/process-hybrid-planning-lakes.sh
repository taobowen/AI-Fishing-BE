#!/usr/bin/env bash
# Operational Hybrid cascade for the four catalog lakes.
# Does not change YAML defaults. Generate Plan is not invoked here.
# POST returns 202; this script polls GET /api/v1/admin/lakes/jobs/{jobId} and writes result JSON.
set -euo pipefail

API_BASE="${API_BASE:-http://127.0.0.1:8080}"
ADMIN_USER_ID="${ADMIN_USER_ID:-11111111-1111-1111-1111-111111111111}"
OUT_DIR="${OUT_DIR:-docs/reports/ops}"
POLL_SECONDS="${POLL_SECONDS:-5}"
mkdir -p "$OUT_DIR"

lakes=(
  "head:44444444-4444-4444-4444-444444444444"
  "rice:44444444-4444-4444-4444-444444444445"
  "scugog:44444444-4444-4444-4444-444444444446"
  "simcoe:44444444-4444-4444-4444-444444444447"
)

admin_json() {
  local method="$1" path="$2"
  curl -sS -m 120 -H "X-User-Id: ${ADMIN_USER_ID}" -H "Accept: application/json" \
    -X "$method" "${API_BASE}${path}"
}

process_one() {
  local name="$1"
  local id="$2"
  local out="$OUT_DIR/${name}-hybrid.json"
  echo "${name} HYBRID start $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  local start body job_id status latest
  start="$(date +%s)"
  body="$(admin_json POST "/api/v1/admin/lakes/${id}/process?pipeline=HYBRID")"
  job_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["jobId"])' <<<"$body")"
  status="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' <<<"$body")"
  latest="$body"
  echo "${name} jobId=${job_id} status=${status}"
  while [[ "$status" != "SUCCEEDED" && "$status" != "FAILED" ]]; do
    sleep "$POLL_SECONDS"
    latest="$(admin_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
    status="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' <<<"$latest")"
    echo "${name} poll status=${status}"
  done
  python3 - "$out" <<<"$latest" <<'PY'
import json, sys
path = sys.argv[1]
job = json.load(sys.stdin)
json.dump(job.get("result") or job, open(path, "w"), indent=2)
PY
  local end elapsed
  end="$(date +%s)"
  elapsed="$((end - start))"
  echo "${name} HYBRID end $(date -u +%Y-%m-%dT%H:%M:%SZ) elapsed=${elapsed}s status=${status}"
  echo "$elapsed" > "$OUT_DIR/${name}-hybrid-elapsed-seconds.txt"
  if [[ "$status" != "SUCCEEDED" ]]; then
    echo "$latest" >&2
    return 1
  fi
}

if [[ $# -gt 0 ]]; then
  process_one "$1" "$2"
  exit 0
fi

for entry in "${lakes[@]}"; do
  process_one "${entry%%:*}" "${entry##*:}"
done
