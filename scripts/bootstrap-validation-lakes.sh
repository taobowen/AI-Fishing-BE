#!/usr/bin/env bash
# Phase 8.5 bootstrap: real Ontario import → GIS process → user Generate Plan.
# Credentials come from the environment only. Default run always live-refreshes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCENARIOS="${ROOT}/scripts/validation-scenarios.json"
REPORT_DIR="${ROOT}/docs/reports"
ARTIFACT_ROOT="${ROOT}/build/validation-artifacts"
STATE_FILE="${ARTIFACT_ROOT}/bootstrap-state.json"

API_BASE="${API_BASE:-}"
ENVIRONMENT="${ENVIRONMENT:-local-live}"
PIPELINE="${PIPELINE:-GIS}"
CURL_MAX_TIME="${CURL_MAX_TIME:-1800}"
RESUME=0
SKIP_IMPORT=0
SKIP_PROCESS=0
SKIP_PLAN=0
CMD="all"

usage() {
  cat <<EOF
Usage: API_BASE=... ENVIRONMENT=local-live|prod ./scripts/bootstrap-validation-lakes.sh [options] [command]

Commands: all (default) | bootstrap-lakes | import-lake | process-lake | validate-lake | generate-validation-trip | generate-validation-report

Options:
  --resume          Continue the same interrupted run (state file). Does not skip because status is AVAILABLE.
  --skip-import     Intentionally reuse existing canonical data (no live Ontario refresh).
  --skip-process    Skip POST process.
  --skip-plan       Skip user trip/plan.
  --pipeline=GIS    Process pipeline (default GIS).
  --lake-id=UUID    Limit to one lake (repeatable via LAKE_IDS csv).
                    A subset writes docs/reports/lake-ops-{ENVIRONMENT}-{runId}.md
                    and does not overwrite production-data-validation.md.

Auth:
  prod: ADMIN_BEARER and USER_BEARER (USER must not be ADMIN)
  local-live: ADMIN_USER_ID and optional USER_USER_ID (X-User-Id). Dev grants ADMIN to every user.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --resume) RESUME=1 ;;
    --skip-import) SKIP_IMPORT=1 ;;
    --skip-process) SKIP_PROCESS=1 ;;
    --skip-plan) SKIP_PLAN=1 ;;
    --pipeline=*) PIPELINE="${1#*=}" ;;
    --lake-id=*) LAKE_IDS="${LAKE_IDS:-},${1#*=}" ;;
    -h|--help) usage; exit 0 ;;
    bootstrap-lakes|import-lake|process-lake|validate-lake|generate-validation-trip|generate-validation-report|all)
      CMD="$1"
      ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 2 ;;
  esac
  shift
done

admin_token() {
  if [[ -n "${ADMIN_BEARER_FILE:-}" && -s "${ADMIN_BEARER_FILE}" ]]; then
    cat "${ADMIN_BEARER_FILE}"
  else
    printf '%s' "${ADMIN_BEARER:-}"
  fi
}

user_token() {
  if [[ -n "${USER_BEARER_FILE:-}" && -s "${USER_BEARER_FILE}" ]]; then
    cat "${USER_BEARER_FILE}"
  else
    printf '%s' "${USER_BEARER:-}"
  fi
}

if [[ -z "$API_BASE" ]]; then
  echo "API_BASE is required" >&2
  exit 2
fi
if [[ "$ENVIRONMENT" != "local-live" && "$ENVIRONMENT" != "prod" ]]; then
  echo "ENVIRONMENT must be local-live or prod" >&2
  exit 2
fi
if [[ "$ENVIRONMENT" == "prod" ]]; then
  if [[ -z "$(admin_token)" || -z "$(user_token)" ]]; then
    echo "prod requires ADMIN_BEARER/ADMIN_BEARER_FILE and USER_BEARER/USER_BEARER_FILE" >&2
    exit 2
  fi
  if [[ -n "${ADMIN_USER_ID:-}" || -n "${USER_USER_ID:-}" ]]; then
    echo "X-User-Id is forbidden when ENVIRONMENT=prod" >&2
    exit 2
  fi
else
  ADMIN_USER_ID="${ADMIN_USER_ID:-11111111-1111-1111-1111-111111111111}"
  USER_USER_ID="${USER_USER_ID:-11111111-1111-1111-1111-111111111112}"
fi

need() { command -v "$1" >/dev/null || { echo "missing $1" >&2; exit 2; }; }
need curl
need python3

mkdir -p "$REPORT_DIR" "$ARTIFACT_ROOT/$ENVIRONMENT"

admin_curl() {
  if [[ "$ENVIRONMENT" == "prod" ]]; then
    curl -sS --max-time "$CURL_MAX_TIME" -H "Accept: application/json" -H "Authorization: Bearer $(admin_token)" "$@"
  else
    curl -sS --max-time "$CURL_MAX_TIME" -H "Accept: application/json" -H "X-User-Id: ${ADMIN_USER_ID}" "$@"
  fi
}

user_curl() {
  if [[ "$ENVIRONMENT" == "prod" ]]; then
    curl -sS --max-time "$CURL_MAX_TIME" -H "Accept: application/json" -H "Content-Type: application/json" -H "Authorization: Bearer $(user_token)" "$@"
  else
    curl -sS --max-time "$CURL_MAX_TIME" -H "Accept: application/json" -H "Content-Type: application/json" -H "X-User-Id: ${USER_USER_ID}" "$@"
  fi
}

http_json() {
  local method="$1" path="$2" role="${3:-admin}" body="${4:-}"
  local tmp hdr code
  tmp="$(mktemp)"
  hdr="$(mktemp)"
  if [[ "$role" == "user" ]]; then
    if [[ -n "$body" ]]; then
      user_curl -D "$hdr" -o "$tmp" -X "$method" -d "$body" "${API_BASE}${path}" || true
    else
      user_curl -D "$hdr" -o "$tmp" -X "$method" "${API_BASE}${path}" || true
    fi
  else
    if [[ -n "$body" ]]; then
      admin_curl -H "Content-Type: application/json" -D "$hdr" -o "$tmp" -X "$method" -d "$body" "${API_BASE}${path}" || true
    else
      admin_curl -D "$hdr" -o "$tmp" -X "$method" "${API_BASE}${path}" || true
    fi
  fi
  code="$(python3 -c 'import sys; t=open(sys.argv[1]).read().splitlines(); s=next((l for l in t if l.startswith("HTTP/")), "HTTP/1.1 000"); print(s.split()[1])' "$hdr")"
  case "$code" in
    2??) ;;
    *)
      echo "HTTP $code $method $path" >&2
      cat "$tmp" >&2 || true
      rm -f "$tmp" "$hdr"
      return 1
      ;;
  esac
  cat "$tmp"
  rm -f "$tmp" "$hdr"
}

job_status_from_json() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])'
}

job_failure_code_from_json() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get("failureCode") or "")'
}

# Gate on status + structured fields only. Never parse failureMessage / errorMessage text.
job_import_contract_ok() {
  python3 -c '
import json,sys
job=json.loads(sys.stdin.read())
result=job.get("result") or {}
ok = job.get("status")=="SUCCEEDED" and result.get("identityResolved") is True and result.get("ogfId") not in (None,"")
raise SystemExit(0 if ok else 1)
'
}

job_process_contract_ok() {
  python3 -c '
import json,sys
job=json.loads(sys.stdin.read())
result=job.get("result") or {}
if job.get("status")!="SUCCEEDED":
    raise SystemExit(1)
if sys.argv[1]=="VISION":
    raise SystemExit(0 if result.get("processingStatus") in ("READY","PARTIAL") else 1)
ok = result.get("spatialSnapshotStatus")=="READY" and result.get("spatialSnapshotId") not in (None,"")
raise SystemExit(0 if ok else 1)
' "$PIPELINE"
}

job_id_from_json() {
  python3 -c 'import json,sys; print(json.load(sys.stdin)["jobId"])'
}

write_job_result() {
  local artifact="$1"
  python3 -c 'import json,sys; job=json.load(sys.stdin); json.dump(job.get("result") or job, open(sys.argv[1],"w"), indent=2); print(sys.argv[1])' "$artifact"
}

# POST 202 enqueue only. Does not wait for SUCCEEDED.
enqueue_ops_job() {
  local path="$1"
  local attempt body
  for attempt in 1 2 3 4 5; do
    body="$(http_json POST "$path" || true)"
    if [[ -n "$body" ]] && python3 -c 'import json,sys; json.loads(sys.argv[1])["jobId"]' "$body" >/dev/null 2>&1; then
      job_id_from_json <<<"$body"
      return 0
    fi
    echo "enqueue retry $attempt empty/invalid POST $path" >&2
    sleep $((attempt * 3))
  done
  echo "enqueue failed after retries: POST $path" >&2
  echo "${body:-<empty>}" >&2
  return 1
}

poll_ops_job() {
  local job_id="$1"
  local artifact="$2"
  local kind="${3:-}"
  local latest status
  latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
  status="$(job_status_from_json <<<"$latest")"
  while [[ "$status" != "SUCCEEDED" && "$status" != "FAILED" ]]; do
    sleep 5
    latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
    status="$(job_status_from_json <<<"$latest")"
  done
  if [[ "$status" != "SUCCEEDED" ]]; then
    echo "lake ops job ${job_id} ${status} failureCode=$(job_failure_code_from_json <<<"$latest")" >&2
    echo "$latest" >&2
    return 1
  fi
  if [[ "$kind" == "import" ]] && ! job_import_contract_ok <<<"$latest"; then
    echo "lake ops job ${job_id} SUCCEEDED but identityResolved/ogfId missing" >&2
    echo "$latest" >&2
    return 1
  fi
  if [[ "$kind" == "process" ]] && ! job_process_contract_ok <<<"$latest"; then
    echo "lake ops job ${job_id} SUCCEEDED but snapshot/processing contract failed" >&2
    echo "$latest" >&2
    return 1
  fi
  write_job_result "$artifact" <<<"$latest"
}

# POST 202 enqueue, poll GET /jobs/{id} (GET also reconciles STOPPED ECS tasks), write result JSON.
post_ops_job() {
  local path="$1"
  local artifact="$2"
  local kind="${3:-}"
  local job_id
  job_id="$(enqueue_ops_job "$path")"
  poll_ops_job "$job_id" "$artifact" "$kind"
}

skip_import() {
  local id="$1"
  [[ "$SKIP_IMPORT" -eq 1 ]] || [[ "$RESUME" -eq 1 && "$(state_done "$id" import)" == "yes" ]]
}

skip_process() {
  local id="$1"
  [[ "$SKIP_PROCESS" -eq 1 ]] || [[ "$RESUME" -eq 1 && "$(state_done "$id" process)" == "yes" ]]
}

enqueue_import_if_needed() {
  local id="$1"
  if skip_import "$id"; then
    echo "skip-import $id"
    return 0
  fi
  local jobid_file="${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.jobid"
  if [[ -f "$jobid_file" ]]; then
    local existing latest
    existing="$(cat "$jobid_file")"
    latest="$(http_json GET "/api/v1/admin/lakes/jobs/${existing}" || true)"
    if [[ -n "$latest" ]] && job_import_contract_ok <<<"$latest"; then
      echo "skip-import $id already identity-SUCCEEDED job $existing"
      return 0
    fi
  fi
  echo "== enqueue import $id =="
  local job_id
  job_id="$(enqueue_ops_job "/api/v1/admin/lakes/${id}/import")"
  echo "$job_id" > "$jobid_file"
  echo "import job $id $job_id"
}

enqueue_process_if_needed() {
  local id="$1"
  if skip_process "$id"; then
    echo "skip-process $id"
    return 0
  fi
  echo "== enqueue process $id pipeline=$PIPELINE =="
  local job_id
  job_id="$(enqueue_ops_job "/api/v1/admin/lakes/${id}/process?pipeline=${PIPELINE}")"
  echo "$job_id" > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid"
  echo "process job $id $job_id"
}

finish_succeeded_job() {
  local id="$1" kind="$2" latest="$3" start_epoch="$4"
  local artifact="${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-${kind}.json"
  write_job_result "$artifact" <<<"$latest" >/dev/null
  echo "${kind}_seconds=$(( $(date +%s) - start_epoch ))" | tee "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-${kind}.time"
  mark_done "$id" "$kind"
}

# Poll pending IMPORT jobs. Submit PROCESS as each IMPORT succeeds.
# Does not wait for lake A's PROCESS/SNAPSHOT before lake B's IMPORT (imports already enqueued).
queue_imports_then_process() {
  local ids=()
  while IFS= read -r id; do
    [[ -n "$id" ]] && ids+=("$id")
  done < <(lake_ids)

  local id
  for id in "${ids[@]}"; do
    enqueue_import_if_needed "$id"
  done

  local pending_imports=()
  for id in "${ids[@]}"; do
    if [[ -f "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.jobid" ]] && [[ "$(state_done "$id" import)" != "yes" ]]; then
      pending_imports+=("$id")
    fi
  done

  local failed=0
  if ((${#pending_imports[@]})); then
    for id in "${pending_imports[@]}"; do
      date +%s > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.start"
    done
  fi

  while [[ ${#pending_imports[@]} -gt 0 ]]; do
    local still=()
    for id in "${pending_imports[@]}"; do
      local job_id latest status
      job_id="$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.jobid")"
      latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
      status="$(job_status_from_json <<<"$latest")"
      if [[ "$status" == "SUCCEEDED" ]] && job_import_contract_ok <<<"$latest"; then
        echo "import succeeded $id"
        finish_succeeded_job "$id" import "$latest" "$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.start")"
        enqueue_process_if_needed "$id"
      elif [[ "$status" == "SUCCEEDED" ]]; then
        echo "import job $id SUCCEEDED but identityResolved/ogfId missing; not enqueueing PROCESS" >&2
        echo "$latest" >&2
        failed=1
      elif [[ "$status" == "FAILED" ]]; then
        echo "import failed $id failureCode=$(job_failure_code_from_json <<<"$latest")" >&2
        echo "$latest" >&2
        failed=1
      else
        still+=("$id")
      fi
    done
    pending_imports=()
    if ((${#still[@]})); then
      pending_imports=("${still[@]}")
    fi
    if [[ ${#pending_imports[@]} -gt 0 ]]; then
      sleep 5
    fi
  done

  for id in "${ids[@]}"; do
    if [[ ! -f "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid" ]]; then
      enqueue_process_if_needed "$id"
    fi
  done

  local pending_process=()
  for id in "${ids[@]}"; do
    if [[ -f "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid" ]] && [[ "$(state_done "$id" process)" != "yes" ]]; then
      pending_process+=("$id")
      date +%s > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.start"
    fi
  done

  while [[ ${#pending_process[@]} -gt 0 ]]; do
    local still=()
    for id in "${pending_process[@]}"; do
      local job_id latest status
      job_id="$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid")"
      latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
      status="$(job_status_from_json <<<"$latest")"
      if [[ "$status" == "SUCCEEDED" ]] && job_process_contract_ok <<<"$latest"; then
        echo "process succeeded $id"
        finish_succeeded_job "$id" process "$latest" "$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.start")"
      elif [[ "$status" == "SUCCEEDED" ]]; then
        echo "process job $id SUCCEEDED but snapshot/processing contract failed" >&2
        echo "$latest" >&2
        failed=1
      elif [[ "$status" == "FAILED" ]]; then
        echo "process failed $id failureCode=$(job_failure_code_from_json <<<"$latest")" >&2
        echo "$latest" >&2
        failed=1
      else
        still+=("$id")
      fi
    done
    pending_process=()
    if ((${#still[@]})); then
      pending_process=("${still[@]}")
    fi
    if [[ ${#pending_process[@]} -gt 0 ]]; then
      sleep 5
    fi
  done

  if [[ "$failed" -ne 0 ]]; then
    echo "one or more IMPORT/PROCESS jobs failed" >&2
    return 1
  fi
}

queue_imports_only() {
  local ids=()
  while IFS= read -r id; do
    [[ -n "$id" ]] && ids+=("$id")
  done < <(lake_ids)
  local id
  for id in "${ids[@]}"; do
    enqueue_import_if_needed "$id"
  done
  local pending=()
  for id in "${ids[@]}"; do
    if [[ -f "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.jobid" ]] && [[ "$(state_done "$id" import)" != "yes" ]]; then
      pending+=("$id")
      date +%s > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.start"
    fi
  done
  local failed=0
  while [[ ${#pending[@]} -gt 0 ]]; do
    local still=()
    for id in "${pending[@]}"; do
      local job_id latest status
      job_id="$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.jobid")"
      latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
      status="$(job_status_from_json <<<"$latest")"
      if [[ "$status" == "SUCCEEDED" ]] && job_import_contract_ok <<<"$latest"; then
        finish_succeeded_job "$id" import "$latest" "$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.start")"
      elif [[ "$status" == "SUCCEEDED" ]]; then
        echo "import job $id SUCCEEDED but identityResolved/ogfId missing" >&2
        echo "$latest" >&2
        failed=1
      elif [[ "$status" == "FAILED" ]]; then
        echo "import failed $id failureCode=$(job_failure_code_from_json <<<"$latest")" >&2
        echo "$latest" >&2
        failed=1
      else
        still+=("$id")
      fi
    done
    pending=()
    if ((${#still[@]})); then
      pending=("${still[@]}")
    fi
    if [[ ${#pending[@]} -gt 0 ]]; then
      sleep 5
    fi
  done
  if [[ "$failed" -ne 0 ]]; then
    return 1
  fi
}

queue_processes_only() {
  local ids=()
  while IFS= read -r id; do
    [[ -n "$id" ]] && ids+=("$id")
  done < <(lake_ids)
  local id
  for id in "${ids[@]}"; do
    enqueue_process_if_needed "$id"
  done
  local pending=()
  for id in "${ids[@]}"; do
    if [[ -f "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid" ]] && [[ "$(state_done "$id" process)" != "yes" ]]; then
      pending+=("$id")
      date +%s > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.start"
    fi
  done
  local failed=0
  while [[ ${#pending[@]} -gt 0 ]]; do
    local still=()
    for id in "${pending[@]}"; do
      local job_id latest status
      job_id="$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.jobid")"
      latest="$(http_json GET "/api/v1/admin/lakes/jobs/${job_id}")"
      status="$(job_status_from_json <<<"$latest")"
      if [[ "$status" == "SUCCEEDED" ]] && job_process_contract_ok <<<"$latest"; then
        finish_succeeded_job "$id" process "$latest" "$(cat "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.start")"
      elif [[ "$status" == "SUCCEEDED" ]]; then
        echo "process job $id SUCCEEDED but snapshot/processing contract failed" >&2
        echo "$latest" >&2
        failed=1
      elif [[ "$status" == "FAILED" ]]; then
        echo "process failed $id failureCode=$(job_failure_code_from_json <<<"$latest")" >&2
        echo "$latest" >&2
        failed=1
      else
        still+=("$id")
      fi
    done
    pending=()
    if ((${#still[@]})); then
      pending=("${still[@]}")
    fi
    if [[ ${#pending[@]} -gt 0 ]]; then
      sleep 5
    fi
  done
  if [[ "$failed" -ne 0 ]]; then
    return 1
  fi
}

init_state() {
  if [[ "$RESUME" -eq 1 && -f "$STATE_FILE" ]]; then
    return
  fi
  python3 - "$STATE_FILE" "$ENVIRONMENT" <<'PY'
import json, sys, uuid, datetime
path, env = sys.argv[1], sys.argv[2]
json.dump({"runId": str(uuid.uuid4()), "environment": env, "startedAt": datetime.datetime.utcnow().isoformat()+"Z", "lakes": {}}, open(path, "w"), indent=2)
PY
}

state_done() {
  python3 - "$STATE_FILE" "$1" "$2" <<'PY'
import json, sys
path, lake, step = sys.argv[1:4]
data = json.load(open(path))
print("yes" if data.get("lakes", {}).get(lake, {}).get(step) else "no")
PY
}

mark_done() {
  python3 - "$STATE_FILE" "$1" "$2" <<'PY'
import json, sys
path, lake, step = sys.argv[1:4]
data = json.load(open(path))
data.setdefault("lakes", {}).setdefault(lake, {})[step] = True
json.dump(data, open(path, "w"), indent=2)
PY
}

lake_ids() {
  if [[ -n "${LAKE_IDS:-}" ]]; then
    echo "$LAKE_IDS" | tr ',' '\n' | sed '/^$/d'
    return
  fi
  python3 - "$SCENARIOS" <<'PY'
import json, sys
cfg = json.load(open(sys.argv[1]))
for lid in cfg["lakes"]:
    print(lid)
PY
}

choose_species() {
  local lake_id="$1" mapped_json="$2"
  python3 - "$SCENARIOS" "$lake_id" "$mapped_json" <<'PY'
import json, sys
cfg = json.load(open(sys.argv[1]))
lake_id = sys.argv[2]
mapped = json.loads(sys.argv[3])
wanted = cfg["lakes"][lake_id]["primaryTargetSpecies"]
fallback = cfg["sportFishFallback"]
if wanted in mapped:
    print(json.dumps({"species": wanted, "source": "configured", "warning": None}))
    raise SystemExit
warning = f"configured species {wanted} not in canonical mapped fish {mapped}"
for cand in fallback:
    if cand in mapped:
        print(json.dumps({"species": cand, "source": "fallback", "warning": warning}))
        raise SystemExit
print(json.dumps({"species": None, "source": "none", "warning": warning + "; no fallback matched"}))
PY
}

bootstrap_lakes() {
  echo "== validation-catalog =="
  http_json POST /api/v1/admin/lakes/validation-catalog
  echo
}

import_one() {
  local id="$1"
  if [[ "$SKIP_IMPORT" -eq 1 ]]; then
    echo "skip-import $id"
    return
  fi
  if [[ "$RESUME" -eq 1 && "$(state_done "$id" import)" == "yes" ]]; then
    echo "resume skip import $id"
    return
  fi
  echo "== import $id =="
  local start end
  start="$(date +%s)"
  post_ops_job "/api/v1/admin/lakes/${id}/import" "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.json" import
  end="$(date +%s)"
  echo "import_seconds=$((end-start))" | tee "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-import.time"
  mark_done "$id" import
}

process_one() {
  local id="$1"
  if [[ "$SKIP_PROCESS" -eq 1 ]]; then
    echo "skip-process $id"
    return
  fi
  if [[ "$RESUME" -eq 1 && "$(state_done "$id" process)" == "yes" ]]; then
    echo "resume skip process $id"
    return
  fi
  echo "== process $id pipeline=$PIPELINE =="
  local start end
  start="$(date +%s)"
  post_ops_job "/api/v1/admin/lakes/${id}/process?pipeline=${PIPELINE}" "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.json" process
  end="$(date +%s)"
  echo "process_seconds=$((end-start))" | tee "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-process.time"
  mark_done "$id" process
}

validate_one() {
  local id="$1"
  echo "== validate $id =="
  http_json GET "/api/v1/admin/lakes/${id}/bootstrap-validation" > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-validation.json"
  mkdir -p "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}"
  admin_curl --max-time "$CURL_MAX_TIME" \
    -o "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}/features.geojson" \
    "${API_BASE}/api/v1/admin/lakes/${id}/features.geojson?pipeline=${PIPELINE}" || true
  admin_curl --max-time "$CURL_MAX_TIME" \
    -o "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}/map.png" \
    "${API_BASE}/api/v1/admin/lakes/${id}/map.png" || true
  python3 - "$ARTIFACT_ROOT/$ENVIRONMENT/$id" <<'PY'
import hashlib, json, os, sys
root = sys.argv[1]
out = {}
for name in ("features.geojson", "map.png"):
    path = os.path.join(root, name)
    if os.path.isfile(path) and os.path.getsize(path) > 0:
        h = hashlib.sha256(open(path, "rb").read()).hexdigest()
        out[name] = {"path": path, "sha256": h, "bytes": os.path.getsize(path)}
json.dump(out, open(os.path.join(root, "artifacts.json"), "w"), indent=2)
PY
}

plan_one() {
  local id="$1"
  if [[ "$SKIP_PLAN" -eq 1 ]]; then
    echo "skip-plan $id"
    return
  fi
  if [[ "$RESUME" -eq 1 && "$(state_done "$id" plan)" == "yes" ]]; then
    echo "resume skip plan $id"
    return
  fi
  echo "== user trip+plan $id =="
  local mapped
  mapped="$(python3 - "$ARTIFACT_ROOT/$ENVIRONMENT/${id}-validation.json" <<'PY'
import json, sys
print(json.dumps(json.load(open(sys.argv[1])).get("mappedSpecies") or []))
PY
)"
  local choice species
  choice="$(choose_species "$id" "$mapped")"
  echo "$choice" > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-species.json"
  species="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["species"] or "")' "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-species.json")"
  if [[ -z "$species" ]]; then
    echo "No valid validation species for $id" >&2
    return 1
  fi
  local date
  date="$(python3 - <<'PY'
from datetime import date, timedelta
print((date.today() + timedelta(days=3)).isoformat())
PY
)"
  local body
  body="$(python3 -c 'import json,sys; print(json.dumps({
    "lakeId": sys.argv[1],
    "primaryTargetSpecies": sys.argv[2],
    "secondaryTargetSpecies": [],
    "plannedDate": sys.argv[3],
    "fishingStartTime": "06:00:00",
    "fishingEndTime": "15:00:00",
    "fishingMode": "SHORE",
    "notes": "Phase 8.5 validation trip"
  }))' "$id" "$species" "$date")"
  local start end
  start="$(date +%s)"
  local trip
        trip="$(http_json POST /api/v1/trips user "$body")" || {
          echo "trip create failed for $id" >&2
          return 1
        }
        echo "$trip" > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-trip.json"
        local trip_id
        trip_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("id") or "")' "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-trip.json")"
        if [[ -z "$trip_id" ]]; then
          echo "trip response missing id for $id" >&2
          return 1
        fi
        http_json POST "/api/v1/trips/${trip_id}/plan" user '{}' > "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-plan.json" || {
          echo "plan failed for $id" >&2
          return 1
        }
  end="$(date +%s)"
  echo "plan_seconds=$((end-start))" | tee "${ARTIFACT_ROOT}/${ENVIRONMENT}/${id}-plan.time"
  mark_done "$id" plan
}

write_report() {
  local run_id subset
  run_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("runId") or "")' "$STATE_FILE" 2>/dev/null || true)"
  if [[ -z "$run_id" ]]; then
    run_id="$(date -u +%Y%m%dT%H%M%SZ)"
  fi
  subset=0
  if [[ -n "${LAKE_IDS:-}" ]]; then
    subset=1
  fi
  python3 - "$ROOT" "$ENVIRONMENT" "$API_BASE" "$PIPELINE" "$SCENARIOS" "$subset" "$run_id" "${LAKE_IDS:-}" <<'PY'
import datetime, json, os, sys
root, env, api, pipeline, scenarios, subset, run_id, lake_ids_csv = sys.argv[1:9]
art = os.path.join(root, "build/validation-artifacts", env)
report_dir = os.path.join(root, "docs/reports")
os.makedirs(report_dir, exist_ok=True)
cfg = json.load(open(scenarios))
generated = datetime.datetime.now(datetime.UTC).replace(tzinfo=None).isoformat() + "Z"
subset = subset == "1"
selected = {item.strip() for item in lake_ids_csv.split(",") if item.strip()}

def loadj(path):
    if not os.path.isfile(path):
        return None
    raw = open(path).read()
    if not raw.strip():
        return None
    try:
        return json.loads(raw)
    except Exception:
        return {"parseError": True, "rawPrefix": raw[:500]}

def plan_completed(plan):
    if not isinstance(plan, dict) or plan.get("parseError") or plan.get("code"):
        return False
    return bool(plan.get("strategyRunId") or plan.get("plan") or plan.get("status") in ("COMPLETED", "READY"))

lakes = []
for lake_id, meta in cfg["lakes"].items():
    if selected and lake_id not in selected:
        continue
    row = {"lakeId": lake_id, "name": meta["name"]}
    for kind in ("import", "process", "validation", "trip", "plan", "species"):
        row[kind] = loadj(os.path.join(art, f"{lake_id}-{kind}.json"))
    for kind in ("import", "process", "plan"):
        tpath = os.path.join(art, f"{lake_id}-{kind}.time")
        row[f"{kind}Seconds"] = None
        if os.path.isfile(tpath):
            text = open(tpath).read()
            try:
                row[f"{kind}Seconds"] = int(text.split("=")[-1].strip())
            except Exception:
                pass
    ap = os.path.join(art, lake_id, "artifacts.json")
    row["artifacts"] = loadj(ap) or {}
    val = row.get("validation") or {}
    geom = (val.get("geometry") or {})
    summary = (val.get("summary") or {})
    analysis = (summary.get("analysis") or {})
    warnings = list(val.get("warnings") or [])
    gis_ready = val.get("postgisAvailable") and analysis.get("processingStatus") == "READY" and (analysis.get("featureCountByType") or {})
    plan_ok = plan_completed(row.get("plan"))
    status = "FAIL"
    if gis_ready and plan_ok:
        status = "PASS_WITH_WARNINGS" if warnings or geom.get("warnings") else "PASS"
    elif gis_ready:
        status = "FAIL"
        warnings = warnings + ["user Generate Plan did not complete"]
    if not val.get("postgisAvailable"):
        status = "FAIL"
    row["validationStatus"] = status
    row["technical"] = "TECHNICALLY_VALID" if gis_ready and plan_ok else "NOT_VALID"
    row["fishingQuality"] = "FISHING_QUALITY_NOT_YET_FIELD_VALIDATED"
    row["visualReview"] = "VISUAL_REVIEW_PENDING"
    row["gisReady"] = bool(gis_ready)
    row["planCompleted"] = plan_ok
    lakes.append(row)

overall = "PASS"
if any(l["validationStatus"] == "FAIL" for l in lakes):
    overall = "FAIL"
elif any(l["validationStatus"] == "PASS_WITH_WARNINGS" for l in lakes):
    overall = "PASS_WITH_WARNINGS"

payload = {
    "generatedAt": generated,
    "environment": env,
    "apiBase": api,
    "pipeline": pipeline,
    "runId": run_id,
    "subset": subset,
    "overallStatus": overall,
    "aws": "EXTERNAL_PREREQUISITE" if env != "prod" else "RUN",
    "s3CloudWatchRds": "NOT_RUN" if env != "prod" else "OPTIONAL",
    "cognitoAdminSplit": "CAN_FIELD_TEST_WITH_WARNING" if env == "local-live" else "RUN",
    "deviceGpsMaplibre": "UNVERIFIED",
    "visualReview": "VISUAL_REVIEW_PENDING",
    "fishingQuality": "FISHING_QUALITY_NOT_YET_FIELD_VALIDATED",
    "openaiConfigured": False,
    "lakes": lakes,
}
if subset:
    stem = f"lake-ops-{env}-{run_id}"
    json_path = os.path.join(report_dir, stem + ".json")
    md_path = os.path.join(report_dir, stem + ".md")
    report_title = "Lake ops subset run"
else:
    json_path = os.path.join(report_dir, "production-data-validation.json")
    md_path = os.path.join(report_dir, "production-data-validation.md")
    report_title = "Production data validation"
json.dump(payload, open(json_path, "w"), indent=2, default=str)

lines = [
    f"# {report_title}",
    "",
    f"- generatedAt: `{generated}`",
    f"- environment: `{env}`",
    f"- apiBase: `{api}`",
    f"- pipeline: `{pipeline}`",
    f"- runId: `{run_id}`",
    f"- subset: `{subset}`",
    f"- overall: **{overall}**",
    f"- visualReview: VISUAL_REVIEW_PENDING",
    f"- fishing quality: FISHING_QUALITY_NOT_YET_FIELD_VALIDATED",
    "",
    "Numbers below are observed from this run. Empty cells were not collected. Do not treat GIS READY as good fishing.",
    "",
    "| Lake | Phase2 | Bathymetry | Phase3 | Features | Strategy | Plan | Warnings |",
    "|---|---|---:|---|---:|---|---|---|",
]
for lake in lakes:
    val = lake.get("validation") or {}
    summary = (val.get("summary") or {})
    analysis = (summary.get("analysis") or {})
    datasets = summary.get("datasets") or []
    bathy = next((d for d in datasets if d.get("datasetType") == "BATHYMETRY_LINE"), {})
    feat = (analysis.get("featureCountByType") or {})
    feat_n = sum(feat.values()) if feat else ""
    plan = lake.get("plan") or {}
    trip = lake.get("trip") or {}
    species = lake.get("species") or {}
    if lake.get("planCompleted"):
        strat = "yes"
        plan_cell = plan.get("status") or "yes"
    elif isinstance(plan, dict) and plan.get("message"):
        strat = "no"
        plan_cell = plan.get("message")
    elif isinstance(trip, dict) and trip.get("parseError"):
        strat = "no"
        plan_cell = "trip create failed"
    elif species.get("species") is None:
        strat = "no"
        plan_cell = (species.get("warning") or "no species")[:80]
    else:
        strat = "no"
        plan_cell = "not completed"
    warn_n = len(val.get("warnings") or [])
    lines.append(
        f"| {lake['name']} | {summary.get('availableDatasets','')} avail / {summary.get('failedDatasets','')} fail | {bathy.get('recordCount','')} | {analysis.get('processingStatus','')} | {feat_n} | {strat} | {plan_cell} | {warn_n} |"
    )
lines += ["", "## IDENTITY", ""]
for lake in lakes:
    val = lake.get("validation") or {}
    imp = lake.get("import") or {}
    lines.append(
        f"- **{lake['name']}** ogfId={val.get('ogfId') or imp.get('ogfId')} "
        f"officialName={val.get('officialName') or imp.get('officialName')} "
        f"identityResolved={imp.get('identityResolved')} boundaryPresent={val.get('boundaryPresent')} "
        f"areaM2={val.get('lakeAreaM2')} tz={val.get('timeZoneId')}"
    )
lines += ["", "## Lakes", ""]
for lake in lakes:
    val = lake.get("validation") or {}
    geom = val.get("geometry") or {}
    plan = lake.get("plan") or {}
    species = lake.get("species") or {}
    lines += [
        f"### {lake['name']}",
        "",
        f"- id: `{lake['lakeId']}`",
        f"- status: **{lake['validationStatus']}**",
        f"- technical: {lake['technical']}",
        f"- gisReady: {lake.get('gisReady')}",
        f"- ogfId: {val.get('ogfId')}",
        f"- timezone: {val.get('timeZoneId')}",
        f"- boundaryPresent: {val.get('boundaryPresent')}",
        f"- mappedSpecies: {', '.join(val.get('mappedSpecies') or []) or '(none)'}",
        f"- configuredSpecies: {species.get('species')} ({species.get('source')})",
        f"- empiricalColdStart: {val.get('empiricalColdStart')} catchEvents={val.get('catchEventCount')} effortSegments={val.get('fishingEffortSegmentCount')}",
        f"- geometry QA: invalid={geom.get('invalidGeometryCount')} outsideBoundary={geom.get('outsideBoundaryCount')} "
        f"polygonAbsurd={geom.get('polygonAbsurdAreaCount')} lineAbsurd={geom.get('lineAbsurdLengthCount')} "
        f"pointsSkippedArea={geom.get('pointGeometriesSkippedForArea')} exactDupGroups={geom.get('exactDuplicateGroupCount')}",
        f"- importSeconds: {lake.get('importSeconds')}",
        f"- processSeconds: {lake.get('processSeconds')}",
        f"- planSeconds: {lake.get('planSeconds')}",
        f"- planError: {(plan or {}).get('message') or (plan or {}).get('parseError')}",
        f"- artifacts: `{json.dumps(lake.get('artifacts') or {})}`",
        "",
        "Pagination (pageCount / rawRecordCount / transferLimitObserved / paginationComplete / paginationWarning):",
        "",
    ]
    for ds in ((val.get("summary") or {}).get("datasets") or []):
        lines.append(
            f"- `{ds.get('datasetType')}` {ds.get('status')} records={ds.get('recordCount')} "
            f"pages={ds.get('pageCount')} raw={ds.get('rawRecordCount')} "
            f"transferLimitObserved={ds.get('transferLimitObserved')} "
            f"paginationComplete={ds.get('paginationComplete')} "
            f"warning={ds.get('paginationWarning')}"
        )
        if ds.get("errorMessage"):
            lines.append(f"  - error: {ds.get('errorMessage')[:300]}")
    lines.append("")
lines += [
    "## Operations",
    "",
    "- S3/CloudWatch/RDS size: **NOT_RUN** (local-live uses `file:` raw storage under `./data/raw`).",
    "- Cognito user-vs-admin split: **CAN FIELD TEST WITH WARNING** — `DevAuthenticationFilter` grants `ROLE_ADMIN` to every `X-User-Id` when `app.admin.enabled=true`. True split requires prod Cognito `ADMIN` group.",
    "- ALB idle timeout / Simcoe over 60s: **EXTERNAL PREREQUISITE** (no ALB in this workspace run). Local Simcoe import was 46s; GIS process was 873s.",
    "- Device/GPS/MapLibre: **UNVERIFIED** (no preview build against this `API_BASE`).",
    "- HTTPS frontend against deployed API: **EXTERNAL PREREQUISITE**.",
    "- `generatePlan()` in the app still POSTs `{}`.",
    "",
    "## Blockers",
    "",
    "- EXTERNAL PREREQUISITE: AWS was never deployed from this workspace (private S3 HEAD, CloudWatch 5xx/OOM, Cognito admin split, ALB 15-minute idle timeout, HTTPS FE).",
    "- OPENAI_API_KEY was unset on the local-live API process; user `POST /plan` returned VALIDATION_ERROR and no StrategyRun/TripPlan was persisted.",
    "- FIELD-TESTING: visual QA of GeoJSON/PNG in gitignored `build/validation-artifacts` (`visualReview: VISUAL_REVIEW_PENDING`).",
    "- FUTURE DATA IMPROVEMENT: vegetation / bottom substrate `NOT_AVAILABLE` by design; wetland GeoJSON parse FAILED on live LIO for some lakes.",
]
open(md_path, "w").write("\n".join(lines) + "\n")
print(md_path)
print(json_path)
PY
}

init_state
case "$CMD" in
  bootstrap-lakes) bootstrap_lakes ;;
  import-lake) bootstrap_lakes; queue_imports_only ;;
  process-lake) queue_processes_only ;;
  validate-lake) for id in $(lake_ids); do validate_one "$id"; done ;;
  generate-validation-trip) for id in $(lake_ids); do plan_one "$id"; done ;;
  generate-validation-report) write_report ;;
  all)
    bootstrap_lakes
    queue_imports_then_process
    for id in $(lake_ids); do
      validate_one "$id"
      plan_one "$id" || echo "plan failed for $id (recorded)"
    done
    write_report
    ;;
esac
