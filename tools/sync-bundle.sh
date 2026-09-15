#!/usr/bin/env sh
# Regenerates src/main/resources/org/sonar/l10n/core_fr.properties from the English
# reference bundle of the sonar.version pinned in pom.xml. See BundleSync for the why.
#
# Safe to run at any time: existing translations are carried over verbatim, and the
# report in target/bundle-sync-report.txt lists what is still untranslated.
set -eu
"$(dirname "$0")/mvn.sh" -B -Psync -q test-compile exec:java
