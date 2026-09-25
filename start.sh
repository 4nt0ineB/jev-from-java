#!/usr/bin/env bash
# Starts every model the snake page can offer, waits until each one answers, then the Java app.
# Jev is TypeSafe's cloud API; Laya runs locally (laya-serve); Open-Jev runs on a rented Modal GPU.
#   LAYA=0 ./start.sh        skip Laya
#   OPENJEV=0 ./start.sh     skip Open-Jev (no GPU rented)
# Ctrl-C stops everything, including the Modal app, so no GPU keeps billing after the demo.
set -euo pipefail
cd "$(dirname "$0")"

log() { printf '[start %s] %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() { log "ERROR: $*" >&2; exit 1; }

: "${TYPESAFE_API_KEY:?export TYPESAFE_API_KEY first: the app needs it for Jev}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
java_version=$("$JAVA" -XshowSettings:properties -version 2>&1 | awk -F' = ' '/java.specification.version/ {print $2}')
[ "${java_version%%.*}" -ge 25 ] 2>/dev/null || fail "JDK 25 or newer needed, found '${java_version:-none}' ($JAVA). Set JAVA_HOME."
VENV=.venv
LAYA_PORT=8000
OPENJEV_APP=jev-snake-openjev

pids=()
openjev_deployed=
cleanup() {
    log "Shutting down."
    if [ ${#pids[@]} -gt 0 ]; then
        log "Stopping laya-serve (pid ${pids[*]})."
        kill "${pids[@]}" 2>/dev/null || true
    fi
    if [ -n "$openjev_deployed" ]; then
        log "Stopping Modal app $OPENJEV_APP, so the GPU stops billing."
        "$VENV/bin/modal" app stop --yes "$OPENJEV_APP" >/dev/null 2>&1 || log "Could not stop $OPENJEV_APP: check https://modal.com/apps"
    fi
    log "Done."
}
trap cleanup EXIT

# name url log [curl args...]: polls until 200. Modal answers a slow cold start with a redirect, hence -L.
wait_for() {
    local name=$1 url=$2 service_log=$3 start=$SECONDS
    shift 3
    log "Waiting for $name at $url (its output: $service_log)."
    for attempt in $(seq 120); do
        if curl -sfL -m 30 "$@" "$url" >/dev/null; then
            log "$name is ready after $((SECONDS - start))s."
            return
        fi
        if [ $((attempt % 6)) = 0 ]; then log "  $name still starting, $((SECONDS - start))s so far..."; fi
        sleep 5
    done
    fail "$name did not answer within 10 minutes, see $service_log."
}

log "Jev: TypeSafe cloud API, always available with TYPESAFE_API_KEY."

if [ ! -x "$VENV/bin/laya-serve" ]; then
    log "First run: creating $VENV with laya[serve] and the modal CLI (downloads torch, a few minutes)."
    python3.12 -m venv "$VENV"
    "$VENV/bin/pip" install -q "laya[serve]==0.3.20" "modal==1.5.5"
    log "$VENV ready."
fi

if [ "${LAYA:-1}" = 1 ]; then
    log "Laya: starting laya-serve on 127.0.0.1:$LAYA_PORT (Apple GPU, english and multilingual checkpoints)."
    # Bound to localhost: laya-serve has no authentication unless LAYA_API_KEY is set.
    LAYA_HOST=127.0.0.1 LAYA_PORT=$LAYA_PORT LAYA_DEVICE=mps LAYA_PRELOAD=1 LAYA_MODELS=english,multilingual \
        "$VENV/bin/laya-serve" > laya.log 2>&1 &
    pids+=($!)
    export LAYA_URL="http://127.0.0.1:$LAYA_PORT"
else
    log "Laya: skipped (LAYA=0)."
fi

if [ "${OPENJEV:-1}" = 1 ]; then
    log "Open-Jev: generating this run's endpoint token and storing it as a Modal secret."
    OPENJEV_API_KEY=$(openssl rand -hex 32)
    export OPENJEV_API_KEY
    # Through a private file rather than argv, where ps would show the key.
    secret=$(mktemp)
    chmod 600 "$secret"
    printf '{"OPENJEV_API_KEY": "%s"}' "$OPENJEV_API_KEY" > "$secret"
    "$VENV/bin/modal" secret create --force --from-json "$secret" jev-snake-openjev-key >/dev/null
    rm -f "$secret"
    log "Open-Jev: deploying $OPENJEV_APP to Modal (the first deploy builds the image, a few minutes; output: modal.log)."
    openjev_deployed=1
    OPENJEV_URL=$("$VENV/bin/modal" deploy selfhost/openjev_modal.py 2>&1 | tee modal.log | grep -o 'https://[^ ]*\.modal\.run' | head -1 || true)
    [ -n "$OPENJEV_URL" ] || fail "No web URL in modal deploy's output, see modal.log."
    export OPENJEV_URL
    log "Open-Jev: deployed at $OPENJEV_URL. No GPU runs until the first request."
else
    log "Open-Jev: skipped (OPENJEV=0)."
fi

log "Building the Java app with JDK $java_version ($JAVA)."
mvn -q package -DskipTests dependency:build-classpath -Dmdep.outputFile=cp.txt
log "Build done."

if [ -n "${LAYA_URL:-}" ]; then wait_for "Laya" "$LAYA_URL/health" laya.log; fi
if [ -n "${OPENJEV_URL:-}" ]; then
    log "Open-Jev: this request starts the L4 container and loads the weights (a few minutes on a cold volume)."
    wait_for "Open-Jev" "$OPENJEV_URL/health" "the Modal dashboard" -H "Authorization: Bearer $OPENJEV_API_KEY"
fi

log "Starting the Java app on http://localhost:${PORT:-7070}/snake (Ctrl-C stops everything)."
"$JAVA" -Dport="${PORT:-7070}" -cp "target/classes:$(cat cp.txt)" Main
