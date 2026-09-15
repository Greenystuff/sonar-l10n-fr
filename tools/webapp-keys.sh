#!/usr/bin/env sh
# Dumps the message catalogue a running SonarQube web app actually ships.
# See WebappKeys for why core.properties is not enough.
#
# Usage: tools/webapp-keys.sh [url]    default http://localhost:9020
set -eu
URL="${1:-http://localhost:9020}"
# host.docker.internal: the tool runs inside the Maven container, the server does not.
URL=$(printf '%s' "$URL" | sed 's|//localhost|//host.docker.internal|; s|//127\.0\.0\.1|//host.docker.internal|')
"$(dirname "$0")/mvn.sh" -B -Psync -q test-compile exec:java \
  -Dl10n.tool=org.sonar.plugins.l10n.WebappKeys \
  -Dexec.args="$URL"
