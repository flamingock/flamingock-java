#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="io.flamingock"
BASE_URL="https://central.sonatype.com/api/internal/browse/component/versions"

get_version() {
  local artifact="$1"
  local version
  version=$(curl -s "${BASE_URL}?sortField=normalizedVersion&sortDirection=desc&page=0&size=1&filter=namespace:${NAMESPACE},name:${artifact}" \
    | grep -o '"version":"[^"]*"' \
    | head -1 \
    | cut -d'"' -f4)
  echo "${version:-NOT FOUND}"
}

printf "%-40s %s\n" "ARTIFACT" "LATEST VERSION"
printf "%-40s %s\n" "----------------------------------------" "---------------"

printf "%-40s %s\n" "General utils"            "$(get_version flamingock-general-util)"
printf "%-40s %s\n" "Template API"             "$(get_version flamingock-template-api)"
printf "%-40s %s\n" "Core API"                 "$(get_version flamingock-core-api)"
printf "%-40s %s\n" "SQL tools"                "$(get_version flamingock-sql-template)"
printf "%-40s %s\n" "MongoDB template"         "$(get_version flamingock-mongodb-sync-template)"
echo
printf "%-40s %s\n" "Core BOM"                 "$(get_version flamingock-bom)"
