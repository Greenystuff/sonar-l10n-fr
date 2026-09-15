#!/usr/bin/env sh
# Dumps the English labels of every settings definition a running SonarQube serves.
# The administration screens do not read those from the message catalogue -- see SettingsKeys.
#
# Usage: tools/settings-keys.sh <url> <admin token>
set -eu
if [ $# -lt 2 ]; then
  echo "Usage: $0 <url> <admin token>" >&2
  exit 2
fi
URL="$1"
TOKEN="$2"
# host.docker.internal: the tool runs inside the Maven container, the server does not.
URL=$(printf '%s' "$URL" | sed 's|//localhost|//host.docker.internal|; s|//127\.0\.0\.1|//host.docker.internal|')
"$(dirname "$0")/mvn.sh" -B -Psync -q test-compile exec:java \
  -Dl10n.tool=org.sonar.plugins.l10n.SettingsKeys \
  -Dexec.args="$URL $TOKEN"
