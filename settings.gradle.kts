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



include("core:flamingock-core-api")
project(":core:flamingock-core-api").name = "flamingock-core-api"
project(":core:flamingock-core-api").projectDir = file("core/flamingock-core-api")

include("core:flamingock-test-support")
project(":core:flamingock-test-support").name = "flamingock-test-support"
project(":core:flamingock-test-support").projectDir = file("core/flamingock-test-support")


//////////////////////////////////////
// CLOUD
//////////////////////////////////////
include("cloud:flamingock-cloud")
project(":cloud:flamingock-cloud").name = "flamingock-cloud"
project(":cloud:flamingock-cloud").projectDir = file("cloud/flamingock-cloud")

include("cloud:flamingock-cloud-bom")
project(":cloud:flamingock-cloud-bom").name = "flamingock-cloud-bom"
project(":cloud:flamingock-cloud-bom").projectDir = file("cloud/flamingock-cloud-bom")

//////////////////////////////////////
// COMMUNITY
//////////////////////////////////////

include("community:flamingock-community")
project(":community:flamingock-community").name = "flamingock-community"
project(":community:flamingock-community").projectDir = file("community/flamingock-community")

include("community:flamingock-community-bom")
project(":community:flamingock-community-bom").name = "flamingock-community-bom"
project(":community:flamingock-community-bom").projectDir = file("community/flamingock-community-bom")

include("community:flamingock-auditstore-mongodb-sync")
project(":community:flamingock-auditstore-mongodb-sync").name = "flamingock-auditstore-mongodb-sync"
project(":community:flamingock-auditstore-mongodb-sync").projectDir =
    file("community/flamingock-auditstore-mongodb-sync")

include("community:flamingock-auditstore-couchbase")
project(":community:flamingock-auditstore-couchbase").name = "flamingock-auditstore-couchbase"
project(":community:flamingock-auditstore-couchbase").projectDir = file("community/flamingock-auditstore-couchbase")

include("community:flamingock-auditstore-dynamodb")
project(":community:flamingock-auditstore-dynamodb").name = "flamingock-auditstore-dynamodb"
project(":community:flamingock-auditstore-dynamodb").projectDir = file("community/flamingock-auditstore-dynamodb")

include("community:flamingock-auditstore-sql")
project(":community:flamingock-auditstore-sql").name = "flamingock-auditstore-sql"
project(":community:flamingock-auditstore-sql").projectDir = file("community/flamingock-auditstore-sql")

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

include("core:target-systems:nontransactional-target-system")
project(":core:target-systems:nontransactional-target-system").projectDir =
    file("core/target-systems/nontransactional-target-system")
project(":core:target-systems:nontransactional-target-system").name = "nontransactional-target-system"

include("core:target-systems:mongodb-external-system-api")
project(":core:target-systems:mongodb-external-system-api").projectDir =
    file("core/target-systems/mongodb-external-system-api")
project(":core:target-systems:mongodb-external-system-api").name = "mongodb-external-system-api"

include("core:target-systems:mongodb-sync-target-system")
project(":core:target-systems:mongodb-sync-target-system").projectDir =
    file("core/target-systems/mongodb-sync-target-system")
project(":core:target-systems:mongodb-sync-target-system").name = "mongodb-sync-target-system"

include("core:target-systems:mongodb-springdata-target-system")
project(":core:target-systems:mongodb-springdata-target-system").projectDir =
    file("core/target-systems/mongodb-springdata-target-system")
project(":core:target-systems:mongodb-springdata-target-system").name = "mongodb-springdata-target-system"

include("core:target-systems:sql-external-system-api")
project(":core:target-systems:sql-external-system-api").projectDir = file("core/target-systems/sql-external-system-api")
project(":core:target-systems:sql-external-system-api").name = "sql-external-system-api"

include("core:target-systems:sql-target-system")
project(":core:target-systems:sql-target-system").projectDir = file("core/target-systems/sql-target-system")
project(":core:target-systems:sql-target-system").name = "sql-target-system"

include("core:target-systems:dynamodb-external-system-api")
project(":core:target-systems:dynamodb-external-system-api").projectDir = file("core/target-systems/dynamodb-external-system-api")
project(":core:target-systems:dynamodb-external-system-api").name = "dynamodb-external-system-api"

include("core:target-systems:dynamodb-target-system")
project(":core:target-systems:dynamodb-target-system").projectDir = file("core/target-systems/dynamodb-target-system")
project(":core:target-systems:dynamodb-target-system").name = "dynamodb-target-system"

include("core:target-systems:couchbase-external-system-api")
project(":core:target-systems:couchbase-external-system-api").projectDir = file("core/target-systems/couchbase-external-system-api")
project(":core:target-systems:couchbase-external-system-api").name = "couchbase-external-system-api"

include("core:target-systems:couchbase-target-system")
project(":core:target-systems:couchbase-target-system").projectDir = file("core/target-systems/couchbase-target-system")
project(":core:target-systems:couchbase-target-system").name = "couchbase-target-system"

//////////////////////////////////////
// TEMPLATES
//////////////////////////////////////

//SQL
include("templates:flamingock-sql-template")
project(":templates:flamingock-sql-template").name = "flamingock-sql-template"
project(":templates:flamingock-sql-template").projectDir = file("templates/flamingock-sql-template")


//MONGODB
include("templates:flamingock-mongodb-sync-template")
project(":templates:flamingock-mongodb-sync-template").name = "flamingock-mongodb-sync-template"
project(":templates:flamingock-mongodb-sync-template").projectDir = file("templates/flamingock-mongodb-sync-template")


//////////////////////////////////////
// UTILS
//////////////////////////////////////
include("utils:general-util")
project(":utils:general-util").name = "general-util"
project(":utils:general-util").projectDir = file("utils/general-util")

include("utils:test-util")
project(":utils:test-util").name = "test-util"
project(":utils:test-util").projectDir = file("utils/test-util")


include("utils:mongodb-util")
project(":utils:mongodb-util").name = "mongodb-util"
project(":utils:mongodb-util").projectDir = file("utils/mongodb-util")


include("utils:dynamodb-util")
project(":utils:dynamodb-util").name = "dynamodb-util"
project(":utils:dynamodb-util").projectDir = file("utils/dynamodb-util")

include("utils:couchbase-util")
project(":utils:couchbase-util").name = "couchbase-util"
project(":utils:couchbase-util").projectDir = file("utils/couchbase-util")

include("utils:sql-util")
project(":utils:sql-util").name = "sql-util"
project(":utils:sql-util").projectDir = file("utils/sql-util")

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
// CLI
//////////////////////////////////////
include("cli:flamingock-cli")
project(":cli:flamingock-cli").name = "flamingock-cli"
project(":cli:flamingock-cli").projectDir = file("cli/flamingock-cli")

include("cli:flamingock-cli-executor")
project(":cli:flamingock-cli-executor").name = "flamingock-cli-executor"
project(":cli:flamingock-cli-executor").projectDir = file("cli/flamingock-cli-executor")

//////////////////////////////////////
// E2E TESTS
//////////////////////////////////////
include("e2e:core-e2e")
project(":e2e:core-e2e").name = "core-e2e"
project(":e2e:core-e2e").projectDir = file("e2e/core-e2e")
