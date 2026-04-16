rootProject.name = "flamingock-java"


//////////////////////////////////////
// CORE
//////////////////////////////////////
include("core:flamingock-core")
project(":core:flamingock-core").name = "flamingock-core"
project(":core:flamingock-core").projectDir = file("core/flamingock-core")

include("core:flamingock-processor")
project(":core:flamingock-processor").name = "flamingock-processor"
project(":core:flamingock-processor").projectDir = file("core/flamingock-processor")

include("core:flamingock-graalvm")
project(":core:flamingock-graalvm").name = "flamingock-graalvm"
project(":core:flamingock-graalvm").projectDir = file("core/flamingock-graalvm")


include("core:flamingock-core-commons")
project(":core:flamingock-core-commons").name = "flamingock-core-commons"
project(":core:flamingock-core-commons").projectDir = file("core/flamingock-core-commons")




include("core:flamingock-test-support")
project(":core:flamingock-test-support").name = "flamingock-test-support"
project(":core:flamingock-test-support").projectDir = file("core/flamingock-test-support")

include("core:flamingock-bom")
project(":core:flamingock-bom").name = "flamingock-bom"
project(":core:flamingock-bom").projectDir = file("core/flamingock-bom")


//////////////////////////////////////
// CLOUD
//////////////////////////////////////
include("cloud:flamingock-cloud")
project(":cloud:flamingock-cloud").name = "flamingock-cloud"
project(":cloud:flamingock-cloud").projectDir = file("cloud/flamingock-cloud")

include("cloud:flamingock-cloud-api")
project(":cloud:flamingock-cloud-api").name = "flamingock-cloud-api"
project(":cloud:flamingock-cloud-api").projectDir = file("cloud/flamingock-cloud-api")

//////////////////////////////////////
// COMMUNITY
//////////////////////////////////////

include("community:flamingock-community")
project(":community:flamingock-community").name = "flamingock-community"
project(":community:flamingock-community").projectDir = file("community/flamingock-community")

include("community:flamingock-community-bom")
project(":community:flamingock-community-bom").name = "flamingock-community-bom"
project(":community:flamingock-community-bom").projectDir = file("community/flamingock-community-bom")

include("community:flamingock-mongodb-sync-auditstore")
project(":community:flamingock-mongodb-sync-auditstore").name = "flamingock-mongodb-sync-auditstore"
project(":community:flamingock-mongodb-sync-auditstore").projectDir =
    file("community/flamingock-mongodb-sync-auditstore")

include("community:flamingock-couchbase-auditstore")
project(":community:flamingock-couchbase-auditstore").name = "flamingock-couchbase-auditstore"
project(":community:flamingock-couchbase-auditstore").projectDir = file("community/flamingock-couchbase-auditstore")

include("community:flamingock-dynamodb-auditstore")
project(":community:flamingock-dynamodb-auditstore").name = "flamingock-dynamodb-auditstore"
project(":community:flamingock-dynamodb-auditstore").projectDir = file("community/flamingock-dynamodb-auditstore")

include("community:flamingock-sql-auditstore")
project(":community:flamingock-sql-auditstore").name = "flamingock-sql-auditstore"
project(":community:flamingock-sql-auditstore").projectDir = file("community/flamingock-sql-auditstore")

//////////////////////////////////////
// PLUGINS
//////////////////////////////////////
include("platform-plugins:flamingock-springboot-integration")
project(":platform-plugins:flamingock-springboot-integration").name = "flamingock-springboot-integration"
project(":platform-plugins:flamingock-springboot-integration").projectDir =
    file("platform-plugins/flamingock-springboot-integration")

include("platform-plugins:flamingock-springboot-test-support")
project(":platform-plugins:flamingock-springboot-test-support").name = "flamingock-springboot-test-support"
project(":platform-plugins:flamingock-springboot-test-support").projectDir =
    file("platform-plugins/flamingock-springboot-test-support")
//////////////////////////////////////
// TARGET SYSTEMS
//////////////////////////////////////

include("core:target-systems:flamingock-nontransactional-targetsystem")

include("core:target-systems:flamingock-mongodb-externalsystem-api")

include("core:target-systems:flamingock-mongodb-sync-targetsystem")

include("core:target-systems:flamingock-mongodb-springdata-targetsystem")

include("core:target-systems:flamingock-sql-externalsystem-api")

include("core:target-systems:flamingock-sql-targetsystem")

include("core:target-systems:flamingock-dynamodb-externalsystem-api")

include("core:target-systems:flamingock-dynamodb-targetsystem")

include("core:target-systems:flamingock-couchbase-externalsystem-api")

include("core:target-systems:flamingock-couchbase-targetsystem")


//////////////////////////////////////
// UTILS
//////////////////////////////////////
include("utils:test-util")
project(":utils:test-util").name = "test-util"
project(":utils:test-util").projectDir = file("utils/test-util")


include("utils:mongodb-util")
project(":utils:mongodb-util").name = "mongodb-util"
project(":utils:mongodb-util").projectDir = file("utils/mongodb-util")

include("utils:mongodb-test-kit")
project(":utils:mongodb-test-kit").name = "mongodb-test-kit"
project(":utils:mongodb-test-kit").projectDir = file("utils/mongodb-test-kit")

include("utils:dynamodb-util")
project(":utils:dynamodb-util").name = "dynamodb-util"
project(":utils:dynamodb-util").projectDir = file("utils/dynamodb-util")

include("utils:dynamodb-test-kit")
project(":utils:dynamodb-test-kit").name = "dynamodb-test-kit"
project(":utils:dynamodb-test-kit").projectDir = file("utils/dynamodb-test-kit")

include("utils:couchbase-util")
project(":utils:couchbase-util").name = "couchbase-util"
project(":utils:couchbase-util").projectDir = file("utils/couchbase-util")

include("utils:couchbase-test-kit")
project(":utils:couchbase-test-kit").name = "couchbase-test-kit"
project(":utils:couchbase-test-kit").projectDir = file("utils/couchbase-test-kit")


include("utils:sql-test-kit")
project(":utils:sql-test-kit").name = "sql-test-kit"
project(":utils:sql-test-kit").projectDir = file("utils/sql-test-kit")

//////////////////////////////////////
// LEGACY
//////////////////////////////////////
include("legacy:mongock-support")
project(":legacy:mongock-support").name = "mongock-support"
project(":legacy:mongock-support").projectDir = file("legacy/mongock-support")

include("legacy:mongock-importer-mongodb")
project(":legacy:mongock-importer-mongodb").name = "mongock-importer-mongodb"
project(":legacy:mongock-importer-mongodb").projectDir = file("legacy/mongock-importer-mongodb")

include("legacy:mongock-importer-dynamodb")
project(":legacy:mongock-importer-dynamodb").name = "mongock-importer-dynamodb"
project(":legacy:mongock-importer-dynamodb").projectDir = file("legacy/mongock-importer-dynamodb")

include("legacy:mongock-importer-couchbase")
project(":legacy:mongock-importer-couchbase").name = "mongock-importer-couchbase"
project(":legacy:mongock-importer-couchbase").projectDir = file("legacy/mongock-importer-couchbase")


//////////////////////////////////////
// GRADLE PLUGIN
//////////////////////////////////////
include("flamingock-gradle-plugin")
project(":flamingock-gradle-plugin").projectDir = file("flamingock-gradle-plugin")


//////////////////////////////////////
// E2E TESTS
//////////////////////////////////////
include("e2e:core-e2e")
project(":e2e:core-e2e").name = "core-e2e"
project(":e2e:core-e2e").projectDir = file("e2e/core-e2e")
