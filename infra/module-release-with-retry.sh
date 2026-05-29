#!/bin/bash

module=$1
maxAttempts=${2:-3}
waitingSeconds=${3:-20}
shift 3 2>/dev/null || true
extraFlags="$*"

if [ -n "$module" ]; then
  MODULE_FLAG="-Pmodule=$module"
  echo "Releasing bundle[$module] to Central Portal with max attempts[$maxAttempts] and $waitingSeconds seconds delay"
else
  MODULE_FLAG=""
  echo "Releasing bundle to Central Portal with max attempts[$maxAttempts] and $waitingSeconds seconds delay"
fi
# The unqualified jreleaserDeploy fans out to every subproject that has the org.jreleaser
# plugin applied (it's applied broadly by flamingock.release-management). The release-management
# convention plugin's onlyIf gate skips projects outside projectsToRelease so the fan-out is safe.
for (( i=1; i<=maxAttempts; i++ )); do
  if ./gradlew jreleaserDeploy $MODULE_FLAG $extraFlags --no-daemon --stacktrace; then
    exit 0
  fi
  if [ "$i" -eq "$maxAttempts" ]; then
    echo "Failed release after $maxAttempts maxAttempts"
    exit 1
  fi
  echo "Retrying in $waitingSeconds seconds..."
  sleep "$waitingSeconds"
  echo
  echo "********************************************************************************** RELEASE ATTEMPT($((i + 1))) **********************************************************************************"
done
