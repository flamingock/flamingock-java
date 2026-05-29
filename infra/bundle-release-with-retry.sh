#!/bin/bash

validate_bundle() {
  case "$1" in
    all|core|cloud|community|plugins|targetSystems|templates|utils)
      return 0
      ;;
    *)
      echo "Error: Invalid bundle name [$1]. Myst be one of the following values[all, core, auditStore, transactioner, template]"
      exit 1
      ;;
  esac
}


maxAttempts=${2:-3}
waitingSeconds=${3:-20}

validate_bundle "$1"

echo "Releasing bundle[$1] to Central Portal with max attempts[$maxAttempts] and $waitingSeconds seconds delay"
for (( i=1; i<=maxAttempts; i++ )); do
  # A bundle spans multiple projects, so we deliberately use the unqualified task name and
  # let the release-management convention plugin's projectsToRelease (driven by -PreleaseBundle)
  # decide which projects participate. The onlyIf gate inside that plugin skips projects
  # outside the bundle cleanly, so the unqualified fan-out is safe.
  if ./gradlew jreleaserFullRelease -PreleaseBundle="$1" --no-daemon --stacktrace; then
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
