#!/usr/bin/env bash
#
# Update ONLY the Frontcache Java code (the ROOT.war web app) on a remote EC2 host.
#
# This is the lightweight counterpart to install-frontcache-server-remote.sh. It rebuilds
# frontcache-server's ROOT.war locally, ships just that one file, hot-swaps it, and restarts
# the systemd service. Use it for code-only redeploys (e.g. a core bugfix) when the host is
# already provisioned and configured.
#
# It deliberately DOES NOT touch:
#   - Frontcache configs (FRONTCACHE_HOME/conf: frontcache.properties, fallbacks.conf, bots.conf, ...)
#   - the L2 Lucene cache, logs, or frontcache.id
#   - nginx (front door / gzip / TLS)
#   - the frontcache-console web app
#
# The remote host must already have been set up with install-frontcache-server-remote.sh
# (i.e. ~/$REMOTE_DIR/frontcache-server exists and the '$SERVICE_NAME' systemd unit is installed).
#
# Example:
#   REMOTE_HOST=ec2-54-208-212-231.compute-1.amazonaws.com \
#   PEM_FILE=~/.ssh/coins-2023.pem ./update-frontcache-code-aws-ec2.sh
#
set -euo pipefail

# ---- configuration (env-overridable; defaults match install-frontcache-server-remote.sh)
REMOTE_HOST="${REMOTE_HOST:-ec2-123-456-789-123.compute-1.amazonaws.com}"
REMOTE_USER="${REMOTE_USER:-ubuntu}"
PEM_FILE="${PEM_FILE:-$HOME/.ssh/your-keys.pem}"
REMOTE_DIR="${REMOTE_DIR:-opt}"                  # ~/opt/frontcache-server on the remote
SERVICE_NAME="${SERVICE_NAME:-frontcache}"       # systemd unit name

# local frontcache-server dir + the war that build.finalizedBy(copyWar) produces
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_SERVER_DIR="$SCRIPT_DIR/frontcache-server"
ROOT_WAR="$LOCAL_SERVER_DIR/server/frontcache-base/webapps/ROOT.war"
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

  sudo systemctl start $SERVICE_NAME
  sleep 3
  sudo systemctl --no-pager status $SERVICE_NAME | head -5 || true
  echo \"   (previous war kept at \$WEBAPPS/ROOT.war.bak-\$TS)\"
"

echo ">>> Done. Frontcache Java code updated on $REMOTE_HOST (configs, cache & nginx untouched)."
echo "    Verify:   curl -sI https://$REMOTE_HOST/ | head"
echo "    Logs:     ssh -i $PEM_FILE $SSH_TARGET 'sudo journalctl -u $SERVICE_NAME -f'"
echo "    Rollback: on the remote, restore the newest ROOT.war.bak-* and 'sudo systemctl restart $SERVICE_NAME'"
