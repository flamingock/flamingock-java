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

echo "    extra[\"generalUtilVersion\"] = \"$(get_version flamingock-general-util)\""
echo "    extra[\"templateApiVersion\"] = \"$(get_version flamingock-template-api)\""
echo "    extra[\"coreApiVersion\"] = \"$(get_version flamingock-core-api)\""
echo "    extra[\"sqlVersion\"] = \"$(get_version flamingock-sql-template)\""
echo "    extra[\"mongodbTemplateVersion\"] = \"$(get_version flamingock-mongodb-sync-template)\""
echo
echo "    Core BOM: $(get_version flamingock-bom)"
