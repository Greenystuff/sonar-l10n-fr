# Runs Maven inside a container, so no local Maven/JDK install is required.
#
# JDK 17 is not arbitrary: sonar-core and sonar-testing-harness 26.9 are compiled
# with class file major version 61 (Java 17). An older JDK cannot read them.
#
# The whole repository is mounted, .git included: maven-git-versioning-extension
# derives the artifact version from git metadata and fails without it.
#
# Usage: tools\mvn.ps1 clean verify
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
docker run --rm `
  -v "${repoRoot}:/workspace" `
  -w /workspace `
  -v sonar-l10n-fr-m2:/root/.m2 `
  maven:3.9-eclipse-temurin-17 `
  mvn @args
exit $LASTEXITCODE
