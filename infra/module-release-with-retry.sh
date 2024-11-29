#!/bin/bash

maxAttempts=${2:-3}
waitingSeconds=${3:-20}

echo "Releasing bundle[$1] to Central Portal with max attempts[$maxAttempts] and $waitingSeconds seconds delay"
for (( i=1; i<=maxAttempts; i++ )); do
  if ./gradlew jreleaserFullRelease -Pmodule="$1" --no-daemon --stacktrace; then
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