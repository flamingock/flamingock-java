rootProject.name = "flamingock-project"


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

include("community:flamingock-ce-commons")
project(":community:flamingock-ce-commons").name = "flamingock-ce-commons"
project(":community:flamingock-ce-commons").projectDir = file("community/flamingock-ce-commons")

include("community:flamingock-ce-bom")
project(":community:flamingock-ce-bom").name = "flamingock-ce-bom"
project(":community:flamingock-ce-bom").projectDir = file("community/flamingock-ce-bom")

include("community:flamingock-ce-mongodb-sync")
project(":community:flamingock-ce-mongodb-sync").name = "flamingock-ce-mongodb-sync"
project(":community:flamingock-ce-mongodb-sync").projectDir = file("community/flamingock-ce-mongodb-sync")

include("community:flamingock-ce-mongodb-springdata-v3-legacy")
project(":community:flamingock-ce-mongodb-springdata-v3-legacy").name = "flamingock-ce-mongodb-springdata-v3-legacy"
project(":community:flamingock-ce-mongodb-springdata-v3-legacy").projectDir =
    file("community/flamingock-ce-mongodb-springdata-v3-legacy")

include("community:flamingock-ce-mongodb-springdata")
project(":community:flamingock-ce-mongodb-springdata").name = "flamingock-ce-mongodb-springdata"
project(":community:flamingock-ce-mongodb-springdata").projectDir = file("community/flamingock-ce-mongodb-springdata")

include("community:flamingock-ce-couchbase")
project(":community:flamingock-ce-couchbase").name = "flamingock-ce-couchbase"
project(":community:flamingock-ce-couchbase").projectDir = file("community/flamingock-ce-couchbase")

include("community:flamingock-ce-dynamodb")
project(":community:flamingock-ce-dynamodb").name = "flamingock-ce-dynamodb"
project(":community:flamingock-ce-dynamodb").projectDir = file("community/flamingock-ce-dynamodb")

//////////////////////////////////////
// PLUGINS
//////////////////////////////////////
include("platform-plugins:flamingock-springboot-integration-v2-legacy")
project(":platform-plugins:flamingock-springboot-integration-v2-legacy").name =
    "flamingock-springboot-integration-v2-legacy"
project(":platform-plugins:flamingock-springboot-integration-v2-legacy").projectDir =
    file("platform-plugins/flamingock-springboot-integration-v2-legacy")

include("platform-plugins:flamingock-springboot-integration")
project(":platform-plugins:flamingock-springboot-integration").name = "flamingock-springboot-integration"
project(":platform-plugins:flamingock-springboot-integration").projectDir =
    file("platform-plugins/flamingock-springboot-integration")

//////////////////////////////////////
// TARGET SYSTEMS
//////////////////////////////////////

include("core:target-systems:mongodb-sync-target-system")
project(":core:target-systems:mongodb-sync-target-system").projectDir = file("core/target-systems/mongodb-sync-target-system")
project(":core:target-systems:mongodb-sync-target-system").name = "mongodb-sync-target-system"

include("core:target-systems:mongodb-springdata-target-system")
project(":core:target-systems:mongodb-springdata-target-system").projectDir = file("core/target-systems/mongodb-springdata-target-system")
project(":core:target-systems:mongodb-springdata-target-system").name = "mongodb-springdata-target-system"

include("core:target-systems:sql-target-system")
project(":core:target-systems:sql-target-system").projectDir = file("core/target-systems/sql-target-system")
project(":core:target-systems:sql-target-system").name = "sql-target-system"

include("core:target-systems:dynamodb-transactioner")
project(":core:target-systems:dynamodb-transactioner").projectDir = file("core/target-systems/dynamodb-transactioner")
project(":core:target-systems:dynamodb-transactioner").name = "dynamodb-transactioner"


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


//////////////////////////////////////
// IMPORTER
//////////////////////////////////////
include("core:importer:flamingock-importer")
project(":core:importer:flamingock-importer").name = "flamingock-importer"
project(":core:importer:flamingock-importer").projectDir = file("core/importer/flamingock-importer")

include("core:importer:flamingock-importer-mongodb-tests")
project(":core:importer:flamingock-importer-mongodb-tests").name = "flamingock-importer-mongodb-tests"
project(":core:importer:flamingock-importer-mongodb-tests").projectDir = file("core/importer/flamingock-importer-mongodb-tests")


include("core:importer:flamingock-importer-dynamodb-tests")
project(":core:importer:flamingock-importer-dynamodb-tests").name = "flamingock-importer-dynamodb-tests"
project(":core:importer:flamingock-importer-dynamodb-tests").projectDir = file("core/importer/flamingock-importer-dynamodb-tests")


include("core:importer:flamingock-importer-couchbase-tests")
project(":core:importer:flamingock-importer-couchbase-tests").name = "flamingock-importer-couchbase-tests"
project(":core:importer:flamingock-importer-couchbase-tests").projectDir = file("core/importer/flamingock-importer-couchbase-tests")
