#!/usr/bin/env bash
#===============================================================================
# smoke-test.sh — file-server smoke test against the released file-client (docker)
#
# The inverse of file-client/smoke-test.sh: the file-server is built from this
# repository (the code under test) and run locally, while a released file-client
# 2.2.0 (docker) exercises it. This verifies the file-server can serve a real,
# versioned file-client end-to-end. It uses the same REST calls as
#   file-server/resources/file-server.rest   (users, files, GB reference)
#   file-client/resources/file-client.rest   (upload via tus, download)
#
#  Server (file-server, local — the code under test, "StartGB -hsqldb -noAuthentication")
#    Launched from the shaded file-server jar plus the flyway-database-hsqldb jar
#    (that plugin is "provided" scope, so it is not in the shaded jar):
#
#      java -Djavax.net.ssl.trustStore= \
#           -cp <file-server jar>:<flyway-database-hsqldb jar> \
#           dev.luin.file.server.StartGB -hsqldb -noAuthentication
#
#      REST   : https://localhost:8080/service/rest/v1   (self-signed TLS)
#      Files  : https://localhost:8443/files             (tus + download, mTLS)
#      DB     : embedded file-based HSQLDB in a throw-away working directory
#
#    -noAuthentication is used so the header-less .rest requests can be replayed
#    verbatim (a dev build prompts for basic-auth credentials at boot without it).
#    The client certificate is registered via POST /users before uploading (mTLS).
#
#  Client (file-client 2.2.0 — docker, network_mode: host)
#    Built + started by ./docker-compose.yml from the released fat jar + the
#    flyway-database-hsqldb plugin (see Dockerfile.file-client). It runs with
#    network_mode: host, so it shares the host's network and reaches the server
#    directly at localhost:8443 (mTLS) — the same localhost/CN=localhost setup the
#    root smoke test uses, so no trust/hostname overrides are needed:
#
#      REST   : https://localhost:9002/service/rest/v1   (self-signed TLS; 9002 stays
#                                                        clear of the server's 8080/8443/9001
#                                                        and the client's HSQLDB, 9000)
#
#  Test steps
#    1. build (if needed) + start the local file-server, wait for its REST API
#    2. build (if needed) + start the released file-client (docker), wait for its REST API
#    3. server users: getUsers -> createUser -> getUser
#    4. server files: uploadFile -> getFiles -> getFileInfo -> downloadFile
#       -> getExternalDataReference (Digikoppeling senderUrl)
#    5. client upload: POST /upload (creationUrl + file) -> poll to SUCCEEDED
#       (the client tus-uploads to the server's :8443/files/upload via host.docker.internal)
#    6. client download: POST /download (url) -> poll to SUCCEEDED (the client
#       fetches from the server's :8443/files/download/... via host.docker.internal)
#    7. verify the file the client downloaded matches the server's by sha256
#
#  Usage
#    ./smoke-test.sh [--rebuild] [--keep] [--help]
#      --rebuild   force rebuild of the file-server jar and the file-client image
#      --keep      leave the file-server (and the file-client container) running
#
#  Environment
#    SMOKE_LOG_DIR  optional directory; when the test fails, the workdir
#                   (server.log, curl.err, files, ...) and the file-client
#                   container log are copied there
#
#  Requirements: JDK 17, Maven, Docker (daemon + compose), curl
#===============================================================================

set -u

#--- configuration -------------------------------------------------------------
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SERVER_DIR/.." && pwd)"
SERVER_REST="$SERVER_DIR/resources/file-server.rest"
CLIENT_REST="$REPO_ROOT/file-client/resources/file-client.rest"
COMPOSE_FILE="$SERVER_DIR/docker-compose.yml"
COMPOSE_PROJECT="file-server-smoke"

# server endpoints (local, self-signed TLS)
REST_S="https://localhost:8080/service/rest/v1"   # server REST
FILES_S="https://localhost:8443/files"            # server file store (tus + download)
# the same file store, as the docker client reaches it. The client runs with
# network_mode: host, so it shares the host's network and uses localhost.
FILES_CLIENT="https://localhost:8443/files"
# client REST (released file-client, docker, host network)
REST_C="https://localhost:9002/service/rest/v1"

# ports that must be free on the host before starting. 8080/8443/9001 are the
# local server's; 9000/9002 are the client's (it runs with network_mode: host, so its
# own HSQLDB port 9000 and REST port 9002 are on the host's network too)
NEEDED_PORTS="8080 8443 9000 9001 9002"

REBUILD=0
KEEP=0
for arg in "$@"; do
  case "$arg" in
    --rebuild) REBUILD=1 ;;
    --keep)    KEEP=1 ;;
    --help|-h) grep '^#' "$0" | sed 's/^#//;s/^ //' ; exit 0 ;;
    *) echo "Unknown option: $arg (use --help)" >&2; exit 2 ;;
  esac
done

PASS=0
FAIL=0
WORK_DIR=""
SERVER_HOME=""
SERVER_PID=""
CLIENT_STARTED=0

#--- helpers -------------------------------------------------------------------
info()  { echo -e "\033[1;34m==> $*\033[0m"; }
ok()    { echo -e "  \033[1;32m[PASS]\033[0m $1"; PASS=$((PASS+1)); }
bad()   { echo -e "  \033[1;31m[FAIL]\033[0m $1"; FAIL=$((FAIL+1)); }

die() {
  echo -e "\033[1;31mERROR: $*\033[0m" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "required command '$1' not found"
}

port_in_use() {
  # no bare "exec" redirects here: they persist in the main shell and would
  # silently move its stderr to /dev/null for the rest of the script
  local rc=1
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && rc=0
  return $rc
}

# wait_for <description> <timeout-seconds> <command...>
wait_for() {
  local desc="$1" timeout="$2"; shift 2
  local deadline=$(( $(date +%s) + timeout ))
  while true; do
    if "$@" >/dev/null 2>&1; then return 0; fi
    if (( $(date +%s) >= deadline )); then
      echo "  (timed out after ${timeout}s waiting: $desc)" >&2
      return 1
    fi
    sleep 2
  done
}

# http <method> <url> [extra curl args...] -> sets HTTP_CODE and HTTP_BODY
http() {
  local method="$1" url="$2"; shift 2
  local body_file; body_file=$(mktemp)
  HTTP_CODE=$(curl -sk -o "$body_file" -w '%{http_code}' -X "$method" "$@" "$url" 2>>"${WORK_DIR:-/tmp}/curl.err") || HTTP_CODE=000
  HTTP_BODY=$(cat "$body_file")
  rm -f "$body_file"
}

# jsonget <body> <key> -> value (empty if the key is absent).
# grep-based on purpose: this must work even where `python3` misbehaves, and it
# uses the same `grep -oP` already relied on for the certificate extraction.
jsonget() {
  local body="$1" key="$2"
  grep -oP "\"$key\"[[:space:]]*:[[:space:]]*(\"[^\"]*\"|[^,}[:space:]]+)" <<<"$body" | head -n1 |
    sed -E "s/^[[:space:]]*\"$key\"[[:space:]]*:[[:space:]]*//; s/[[:space:]]*$//; s/\"//g"
}

# poll_status <base-url> <task-id> -> sets TASK_STATUS to SUCCEEDED/FAILED/""
poll_status() {
  local base="$1" id="$2"
  TASK_STATUS=""
  for _ in $(seq 1 60); do
    http GET "$base/$id"
    TASK_STATUS="$(jsonget "$HTTP_BODY" status)"
    [[ "$TASK_STATUS" == "SUCCEEDED" || "$TASK_STATUS" == "FAILED" ]] && break
    sleep 1
  done
}

#--- docker compose ------------------------------------------------------------
if docker compose version >/dev/null 2>&1; then
  DC=(docker compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE")
else
  DC=()
fi

cleanup() {
  if [[ $KEEP -eq 0 && -n "$SERVER_PID" ]]; then
    kill "$SERVER_PID" 2>/dev/null
    sleep 1
    kill -9 "$SERVER_PID" 2>/dev/null
  fi
  if [[ $KEEP -eq 0 && $CLIENT_STARTED -eq 1 && ${#DC[@]} -gt 0 ]]; then
    "${DC[@]}" down --remove-orphans >/dev/null 2>&1 || true
  fi
  if [[ -n "$WORK_DIR" && $KEEP -eq 0 ]]; then
    rm -rf "$WORK_DIR"
  fi
}
trap 'rc=$?; if (( rc != 0 )); then
  [[ -n "$SERVER_HOME" ]] && { echo; echo "--- server.log (last 40 lines) ---"; tail -n 40 "$SERVER_HOME/server.log" 2>/dev/null; }
  if [[ $CLIENT_STARTED -eq 1 && ${#DC[@]} -gt 0 ]]; then
    echo "--- docker logs: file-client (last 40 lines) ---"; "${DC[@]}" logs --tail 40 file-client 2>&1 || true
  fi
  if [[ -n "${SMOKE_LOG_DIR:-}" ]]; then
    mkdir -p "$SMOKE_LOG_DIR" 2>/dev/null || true
    [[ -n "$WORK_DIR" ]] && cp -a "$WORK_DIR/." "$SMOKE_LOG_DIR/" 2>/dev/null || true
    [[ $CLIENT_STARTED -eq 1 && ${#DC[@]} -gt 0 ]] && "${DC[@]}" logs file-client > "$SMOKE_LOG_DIR/file-client-docker.log" 2>&1 || true
    echo "smoke test logs saved to $SMOKE_LOG_DIR"
  fi
fi; cleanup' EXIT

#--- pre-flight ----------------------------------------------------------------
info "Pre-flight checks"
for c in java mvn curl; do require_cmd "$c"; done
require_cmd docker
docker info >/dev/null 2>&1 || die "docker daemon is not reachable"
# NOTE: use `[[ ]]` (not `(( ))`) here — `(( ${#DC[@]} ...))` errors under `set -u`.
[[ ${#DC[@]} -gt 0 ]] || die "docker compose is not available (need 'docker compose' or 'docker-compose')"
java_major=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
(( java_major >= 17 )) || die "JDK 17+ required, found $java_major"
for p in $NEEDED_PORTS; do
  port_in_use "$p" && die "port $p is already in use; stop the other process and retry"
done
[[ -f "$COMPOSE_FILE" ]] || die "compose file not found: $COMPOSE_FILE"
[[ -f "$SERVER_REST" ]] || die "server .rest not found: $SERVER_REST"
[[ -f "$CLIENT_REST" ]] || die "client .rest not found: $CLIENT_REST"

#--- working directory (throw-away, keeps repo clean) --------------------------
WORK_DIR=$(mktemp -d "${TMPDIR:-/tmp}/file-server-smoke.XXXXXX")
SERVER_HOME="$WORK_DIR/server"
# file.baseDir=files must pre-exist (the server app does not create the base dir)
mkdir -p "$SERVER_HOME/files"
info "Working directory: $WORK_DIR"

#--- artifacts: shaded file-server jar + flyway-hsqldb plugin ------------------
SERVER_JAR=$(ls "$SERVER_DIR"/target/file-server-*.jar 2>/dev/null | grep -v original- | grep -v sources | grep -v javadoc | head -1 || true)
if [[ $REBUILD -eq 1 || -z "$SERVER_JAR" ]]; then
  info "Building the file-server shaded jar (this can take a few minutes)..."
  # One reactor build of the shaded server jar and its in-repo deps. Static analysis
  # (checkstyle/pmd/spotbugs) is skipped: this is a runtime smoke test, not a lint
  # gate (mvn verify covers those in CI).
  mvn -B -q -DskipTests -Dcheckstyle.skip=true -Dpmd.skip=true -Dspotbugs.skip=true \
      -f "$REPO_ROOT/pom.xml" -pl file-server -am package || die "mvn build failed"
  SERVER_JAR=$(ls "$SERVER_DIR"/target/file-server-*.jar | grep -v original- | grep -v sources | grep -v javadoc | head -1)
fi
[[ -n "$SERVER_JAR" ]] || die "could not locate the file-server jar"
# flyway-database-hsqldb is "provided" scope, so it is NOT in the shaded jar, but the
# HSQLDB backend needs its Flyway DB plugin on the classpath (resolved into the local
# repo by the build above).
FLYWAY_HSQLDB=$(ls "$HOME"/.m2/repository/org/flywaydb/flyway-database-hsqldb/*/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | sort | tail -1 || true)
[[ -n "$FLYWAY_HSQLDB" ]] || die "could not locate flyway-database-hsqldb in ~/.m2"
info "Using server jar  : $SERVER_JAR"
info "Using flyway      : $FLYWAY_HSQLDB"

# ===========================================================================
# 0. server: StartGB -hsqldb -noAuthentication (local, under test)
# ===========================================================================
info "Starting file-server (StartGB -hsqldb -noAuthentication)..."
(
  cd "$SERVER_HOME" || exit 1
  exec java -Djavax.net.ssl.trustStore= \
    -cp "$SERVER_JAR:$FLYWAY_HSQLDB" \
    dev.luin.file.server.StartGB -hsqldb -noAuthentication \
    > "$SERVER_HOME/server.log" 2>&1
) &
SERVER_PID=$!

server_rest() { curl -sk -o /dev/null -w '%{http_code}' "$REST_S/users" | grep -q 200; }
wait_for "server REST API ($REST_S)" 180 server_rest || die "file-server did not start (see $SERVER_HOME/server.log)"
ok "file-server is up on $REST_S + $FILES_S"

# ===========================================================================
# 1. client: released file-client 2.2.0 (docker)
# ===========================================================================
CLIENT_IMAGE="file-client:2.2.0"
if [[ $REBUILD -eq 1 ]] || ! docker image inspect "$CLIENT_IMAGE" >/dev/null 2>&1; then
  info "Building file-client 2.2.0 image from the release jar..."
  "${DC[@]}" build file-client || die "docker compose build failed"
fi
info "Starting file-client 2.2.0 (docker compose)..."
"${DC[@]}" up -d file-client || die "docker compose up failed"
CLIENT_STARTED=1

client_rest() { curl -sk -o /dev/null -w '%{http_code}' "$REST_C/upload" | grep -q 200; }
wait_for "client REST API ($REST_C)" 180 client_rest || die "file-client did not start (see: ${DC[*]} logs file-client)"
ok "file-client 2.2.0 is up on $REST_C"

# ===========================================================================
# 2. server: users (file-server.rest)
# ===========================================================================
info "Server: users"
http GET "$REST_S/users"
[[ "$HTTP_CODE" == "200" && "$HTTP_BODY" == "[]" ]] \
  && ok "getUsers (empty) (HTTP $HTTP_CODE)" \
  || bad "getUsers (empty) (HTTP $HTTP_CODE, body: $HTTP_BODY)"

CERT=$(grep -oP '"certificate":\s*"\K[^"]+' "$SERVER_REST" | head -1)
[[ ${#CERT} -gt 100 ]] || die "could not extract certificate from $SERVER_REST"
http POST "$REST_S/users" -H 'Content-Type: application/json' --data "{\"name\": \"user\", \"certificate\": \"$CERT\"}"
case "$HTTP_CODE" in
  2*) ok "createUser (POST /users) (HTTP $HTTP_CODE)" ;;
  *)  bad "createUser (POST /users) (HTTP $HTTP_CODE) body: ${HTTP_BODY:0:300}";;
esac
http GET "$REST_S/users/0"
[[ "$HTTP_CODE" == "200" ]] && grep -q '"name":"user"' <<<"$HTTP_BODY" \
  && ok "getUser (GET /users/0) (HTTP $HTTP_CODE)" \
  || bad "getUser (GET /users/0) (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:200})"

# ===========================================================================
# 3. server: files (file-server.rest)
# ===========================================================================
info "Server: files"
# uploadFile — replay the EXACT multipart body from the .rest (from the opening
# '---<boundary>' line through the closing '---<boundary>---' line).
awk '/^---/{flag=1} flag{print} /^---.+=---$/{exit}' "$SERVER_REST" > "$SERVER_HOME/upload_body.txt"
[[ -s "$SERVER_HOME/upload_body.txt" ]] || die "could not extract the upload multipart body from $SERVER_REST"
http POST "$REST_S/files/user/0" \
  -H 'Content-Type: multipart/form-data; boundary=-=cTIBJ6SPK7J5=-' \
  --data-binary @"$SERVER_HOME/upload_body.txt"
UP_PATH=$(tr -d '[:space:]' <<<"$HTTP_BODY")
case "$HTTP_CODE" in
  2*) ok "uploadFile (multipart from .rest) (HTTP $HTTP_CODE)" ;;
  *)  bad "uploadFile (multipart from .rest) (HTTP $HTTP_CODE) body: ${HTTP_BODY:0:300}";;
esac
[[ ${#UP_PATH} -gt 40 ]] || die "uploadFile returned no virtual path: '$HTTP_BODY'"
info "  uploaded virtual path: $UP_PATH"

http GET "$REST_S/files"
[[ "$HTTP_CODE" == "200" ]] && grep -qF "$UP_PATH" <<<"$HTTP_BODY" \
  && ok "getFiles (lists uploaded path) (HTTP $HTTP_CODE)" \
  || bad "getFiles (HTTP $HTTP_CODE, body missing $UP_PATH)"
http GET "$REST_S/files/$UP_PATH/info"
[[ "$HTTP_CODE" == "200" ]] && grep -q '"name":"Lorem ipsum.txt"' <<<"$HTTP_BODY" \
  && ok "getFileInfo (HTTP $HTTP_CODE)" \
  || bad "getFileInfo (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:200})"
http GET "$REST_S/files/$UP_PATH"
[[ "$HTTP_CODE" == "200" ]] && (( ${#HTTP_BODY} > 100 )) \
  && ok "downloadFile (HTTP $HTTP_CODE, ${#HTTP_BODY} bytes)" \
  || bad "downloadFile (HTTP $HTTP_CODE, ${#HTTP_BODY} bytes)"
http GET "$REST_S/gb/externalDataReference/$UP_PATH"
[[ "$HTTP_CODE" == "200" ]] && grep -qF "$FILES_S/download/$UP_PATH" <<<"$HTTP_BODY" \
  && ok "getExternalDataReference (Digikoppeling senderUrl) (HTTP $HTTP_CODE)" \
  || bad "getExternalDataReference (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:300})"
# the docker client reaches the server on the host via host.docker.internal
SENDER_URL="$FILES_CLIENT/download/$UP_PATH"

# ===========================================================================
# 4. client: upload (file-client.rest) — tus to the server's :8443
# ===========================================================================
info "Client: upload"
# NOTE: the client's CXF multipart provider rejects the .rest's base64 file part
# ("No multipart with content id file"); the same operation works with a real
# file part, so it is driven with one here (creationUrl + file, as in the .rest).
# The creationUrl points at the host's file server, as the client sees it.
printf 'Mauris nisl smoke test payload.\n' > "$SERVER_HOME/mauris.txt"
http POST "$REST_C/upload" \
  -F "creationUrl=$FILES_CLIENT/upload" \
  -F "file=@$SERVER_HOME/mauris.txt;type=text/plain"
case "$HTTP_CODE" in
  2*) ok "uploadFile (client POST /upload) (HTTP $HTTP_CODE)" ;;
  *)  bad "uploadFile (client POST /upload) (HTTP $HTTP_CODE) body: ${HTTP_BODY:0:300}";;
esac
UP_TASK=$(jsonget "$HTTP_BODY" fileId)
[[ -n "$UP_TASK" ]] || { bad "could not read fileId from client upload response: ${HTTP_BODY:0:200}"; UP_TASK="-1"; }
poll_status "$REST_C/upload" "$UP_TASK"
[[ "$TASK_STATUS" == "SUCCEEDED" ]] \
  && ok "uploadTask SUCCEEDED (client tus-uploaded to $FILES_CLIENT/upload)" \
  || bad "uploadTask status=$TASK_STATUS"
http GET "$REST_C/upload"
[[ "$HTTP_CODE" == "200" ]] && grep -q '"status":"SUCCEEDED"' <<<"$HTTP_BODY" \
  && ok "getUploadTasks (HTTP $HTTP_CODE)" \
  || bad "getUploadTasks (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:200})"

# ===========================================================================
# 5. client: download (file-client.rest) — HTTP from the server's :8443
# ===========================================================================
info "Client: download"
# Same endpoint/shape as the .rest (a multipart 'url' part); use the server's
# download URL as the client reaches it (host.docker.internal).
http POST "$REST_C/download" -F "url=$SENDER_URL"
case "$HTTP_CODE" in
  2*) ok "downloadFile (client POST /download) (HTTP $HTTP_CODE)" ;;
  *)  bad "downloadFile (client POST /download) (HTTP $HTTP_CODE) body: ${HTTP_BODY:0:300}";;
esac
DL_TASK=$(jsonget "$HTTP_BODY" fileId)
[[ -n "$DL_TASK" ]] || { bad "could not read fileId from client download response: ${HTTP_BODY:0:200}"; DL_TASK="-1"; }
poll_status "$REST_C/download" "$DL_TASK"
[[ "$TASK_STATUS" == "SUCCEEDED" ]] \
  && ok "downloadTask SUCCEEDED (client fetched from $SENDER_URL)" \
  || bad "downloadTask status=$TASK_STATUS"
http GET "$REST_C/download"
[[ "$HTTP_CODE" == "200" ]] && grep -q '"status":"SUCCEEDED"' <<<"$HTTP_BODY" \
  && ok "getDownloadTasks (HTTP $HTTP_CODE)" \
  || bad "getDownloadTasks (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:200})"

# the file the client downloaded must match the one on the server (by sha256)
if [[ -n "$DL_TASK" && "$DL_TASK" != "-1" ]]; then
  http GET "$REST_C/files/$DL_TASK/info"
  [[ "$HTTP_CODE" == "200" ]] && grep -q '"name":"Lorem ipsum.txt"' <<<"$HTTP_BODY" \
    && ok "client getFileInfo (downloaded) (HTTP $HTTP_CODE)" \
    || bad "client getFileInfo (HTTP $HTTP_CODE, body: ${HTTP_BODY:0:200})"
  DL_SHA=$(jsonget "$HTTP_BODY" sha256Checksum)
else
  bad "client getFileInfo (skipped: no download task id)"
  DL_SHA=""
fi
http GET "$REST_S/files/$UP_PATH/info"
SRV_SHA=$(jsonget "$HTTP_BODY" sha256Checksum)
[[ -n "$DL_SHA" && "$DL_SHA" == "$SRV_SHA" ]] \
  && ok "round-trip integrity: client sha256 == server sha256 ($DL_SHA)" \
  || bad "round-trip integrity: client='$DL_SHA' server='$SRV_SHA'"

# ===========================================================================
# summary
# ===========================================================================
info "Summary"
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
if [[ $FAIL -eq 0 ]]; then
  echo -e "  \033[1;32mALL CHECKS PASSED\033[0m"
  exit 0
else
  echo -e "  \033[1;31mSOME CHECKS FAILED\033[0m"
  exit 1
fi
