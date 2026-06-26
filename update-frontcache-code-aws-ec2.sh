#!/usr/bin/env bash
#
# Update ONLY the Frontcache Java code (the ROOT.war web app) on a remote EC2 host.
#
# This is the lightweight counterpart to install-frontcache-server-remote.sh. It rebuilds
# frontcache-server's ROOT.war locally, ships just that one file, hot-swaps it, and restarts
# the systemd service. Use it for code-only redeploys (e.g. a core bugfix) when the host is
# already provisioned and configured.
#
# By default it DOES NOT touch:
#   - Frontcache configs (FRONTCACHE_HOME/conf: hystrix.properties, fc-logback.xml, fallbacks.conf, ...)
#   - the L2 Lucene cache, logs, or frontcache.id
#   - nginx (front door / gzip / TLS)
#   - the frontcache-console web app
#
# Pass --with-config to ALSO sync FRONTCACHE_HOME/conf from this repo to the remote (e.g. after
# changing hystrix.properties or fc-logback.xml). nginx is still never touched. The two
# host-specific files are always preserved on the remote and never overwritten:
#   - frontcache.id          (node identity, e.g. fc-us.hobbyray.com)
#   - frontcache.properties  (origin host/port, domains, management port)
# Each overwritten config is backed up on the remote as <name>.bak-<timestamp> for rollback.
#
# The remote host must already have been set up with install-frontcache-server-remote.sh
# (i.e. ~/$REMOTE_DIR/frontcache-server exists and the '$SERVICE_NAME' systemd unit is installed).
#
# Examples:
#   # code only:
#   REMOTE_HOST=ec2-123-456-789-123.compute-1.amazonaws.com \
#   PEM_FILE=~/.ssh/your-keys.pem ./update-frontcache-code-aws-ec2.sh
#
#   # code + frontcache configs (no nginx):
#   REMOTE_HOST=ec2-123-456-789-123.compute-1.amazonaws.com \
#   PEM_FILE=~/.ssh/your-keys.pem ./update-frontcache-code-aws-ec2.sh --with-config
#
set -euo pipefail

# ---- configuration (env-overridable; defaults match install-frontcache-server-remote.sh)
REMOTE_HOST="${REMOTE_HOST:-ec2-123-456-789-123.compute-1.amazonaws.com}"
REMOTE_USER="${REMOTE_USER:-ubuntu}"
PEM_FILE="${PEM_FILE:-$HOME/.ssh/your-keys.pem}"
REMOTE_DIR="${REMOTE_DIR:-opt}"                  # ~/opt/frontcache-server on the remote
SERVICE_NAME="${SERVICE_NAME:-frontcache}"       # systemd unit name

# ---- args
WITH_CONFIG=false
for arg in "$@"; do
  case "$arg" in
    --with-config) WITH_CONFIG=true ;;
    -h|--help) grep -E '^#( |$)' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "ERROR: unknown argument: $arg (use --with-config or --help)" >&2; exit 1 ;;
  esac
done

# local frontcache-server dir + the war that build.finalizedBy(copyWar) produces
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_SERVER_DIR="$SCRIPT_DIR/frontcache-server"
ROOT_WAR="$LOCAL_SERVER_DIR/server/frontcache-base/webapps/ROOT.war"

# FRONTCACHE_HOME/conf synced only with --with-config. Host-specific files are never overwritten.
LOCAL_CONF_DIR="$LOCAL_SERVER_DIR/FRONTCACHE_HOME/conf"
CONFIG_EXCLUDES=(frontcache.id frontcache.properties)
# ------------------------------------------------------------------------------

SSH_TARGET="$REMOTE_USER@$REMOTE_HOST"
SSH_OPTS=(-i "$PEM_FILE" -o StrictHostKeyChecking=accept-new)

if [ ! -f "$PEM_FILE" ]; then
  echo "ERROR: pem file not found: $PEM_FILE" >&2
  exit 1
fi

# ---- build a fresh ROOT.war (code only) --------------------------------------
# build.finalizedBy(copyWar) places the war at $ROOT_WAR. Build before touching the
# remote so a compile/test failure aborts the deploy without stopping the live service.
echo ">>> Building ROOT.war (./gradlew :frontcache-server:build) ..."
( cd "$SCRIPT_DIR" && ./gradlew :frontcache-server:build )

if [ ! -f "$ROOT_WAR" ]; then
  echo "ERROR: build did not produce $ROOT_WAR" >&2
  exit 1
fi
echo "    war: $ROOT_WAR ($(du -h "$ROOT_WAR" | cut -f1))"

# ---- upload, then hot-swap the war and restart the service -------------------
echo ">>> Uploading ROOT.war to $SSH_TARGET ..."
scp "${SSH_OPTS[@]}" "$ROOT_WAR" "$SSH_TARGET:/tmp/ROOT.war.new"

# ---- optionally stage Frontcache configs (--with-config) ---------------------
# Upload to a remote staging dir now; the swap block below applies them while the
# service is stopped (so one restart picks up both the war and the configs). The
# presence of /tmp/fc-conf-new on the remote is what signals the swap block to apply them.
if $WITH_CONFIG; then
  if [ ! -d "$LOCAL_CONF_DIR" ]; then
    echo "ERROR: config dir not found: $LOCAL_CONF_DIR" >&2
    exit 1
  fi
  echo ">>> Staging Frontcache configs to $SSH_TARGET (host-specific files preserved) ..."
  UPLOAD_FILES=()
  for f in "$LOCAL_CONF_DIR"/*; do
    [ -f "$f" ] || continue
    base="$(basename "$f")"
    skip=false
    for ex in "${CONFIG_EXCLUDES[@]}"; do [ "$base" = "$ex" ] && skip=true; done
    if $skip; then echo "    skip (host-specific): $base"; continue; fi
    UPLOAD_FILES+=("$f")
    echo "    upload: $base"
  done
  ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "rm -rf /tmp/fc-conf-new && mkdir -p /tmp/fc-conf-new"
  scp "${SSH_OPTS[@]}" "${UPLOAD_FILES[@]}" "$SSH_TARGET:/tmp/fc-conf-new/"
fi

echo ">>> Swapping war and restarting '$SERVICE_NAME' on $SSH_TARGET ..."
ssh "${SSH_OPTS[@]}" "$SSH_TARGET" "
  set -e
  REMOTE_HOME=\$(eval echo ~$REMOTE_USER)
  WEBAPPS=\"\$REMOTE_HOME/$REMOTE_DIR/frontcache-server/server/frontcache-base/webapps\"

  if [ ! -f \"\$WEBAPPS/ROOT.war\" ]; then
    echo \"ERROR: \$WEBAPPS/ROOT.war not found on remote - run install-frontcache-server-remote.sh first\" >&2
    rm -f /tmp/ROOT.war.new
    exit 1
  fi

  # stop so Jetty releases the war and its extracted copy before we swap
  sudo systemctl stop $SERVICE_NAME

  # back up the current war (timestamped) for quick rollback, then swap in the new one
  TS=\$(date +%Y%m%d-%H%M%S)
  cp -p \"\$WEBAPPS/ROOT.war\" \"\$WEBAPPS/ROOT.war.bak-\$TS\"
  mv /tmp/ROOT.war.new \"\$WEBAPPS/ROOT.war\"

  # ROOT.xml sets extractWAR=true: drop any stale exploded webapp + Jetty temp dirs
  # so the new war is re-extracted on start (avoids serving stale classes).
  rm -rf \"\$WEBAPPS/ROOT\"
  rm -rf /tmp/jetty-* 2>/dev/null || true

  # apply staged configs (only present when run with --with-config). Each overwritten
  # file is backed up as <name>.bak-\$TS. frontcache.id / frontcache.properties were never
  # staged, so they're left untouched on the remote.
  if [ -d /tmp/fc-conf-new ]; then
    CONF_DIR=\"\$REMOTE_HOME/$REMOTE_DIR/frontcache-server/FRONTCACHE_HOME/conf\"
    for nf in /tmp/fc-conf-new/*; do
      [ -e \"\$nf\" ] || continue
      base=\$(basename \"\$nf\")
      [ -f \"\$CONF_DIR/\$base\" ] && cp -p \"\$CONF_DIR/\$base\" \"\$CONF_DIR/\$base.bak-\$TS\"
      mv \"\$nf\" \"\$CONF_DIR/\$base\"
      echo \"   config updated: \$base (previous kept at \$base.bak-\$TS)\"
    done
    rm -rf /tmp/fc-conf-new
  fi

  sudo systemctl start $SERVICE_NAME
  sleep 3
  sudo systemctl --no-pager status $SERVICE_NAME | head -5 || true
  echo \"   (previous war kept at \$WEBAPPS/ROOT.war.bak-\$TS)\"
"

if $WITH_CONFIG; then
  echo ">>> Done. Frontcache code + configs updated on $REMOTE_HOST (frontcache.id, frontcache.properties, cache & nginx untouched)."
else
  echo ">>> Done. Frontcache Java code updated on $REMOTE_HOST (configs, cache & nginx untouched)."
fi
echo "    Verify:   curl -sI https://$REMOTE_HOST/ | head"
echo "    Logs:     ssh -i $PEM_FILE $SSH_TARGET 'sudo journalctl -u $SERVICE_NAME -f'"
echo "    Rollback: on the remote, restore the newest ROOT.war.bak-* and 'sudo systemctl restart $SERVICE_NAME'"
