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

include("community:flamingock-ce-mongodb-sync-v4")
project(":community:flamingock-ce-mongodb-sync-v4").name = "flamingock-ce-mongodb-sync-v4"
project(":community:flamingock-ce-mongodb-sync-v4").projectDir = file("community/flamingock-ce-mongodb-sync-v4")

include("community:flamingock-ce-mongodb-v3")
project(":community:flamingock-ce-mongodb-v3").name = "flamingock-ce-mongodb-v3"
project(":community:flamingock-ce-mongodb-v3").projectDir = file("community/flamingock-ce-mongodb-v3")

include("community:flamingock-ce-mongodb-springdata-v3")
project(":community:flamingock-ce-mongodb-springdata-v3").name = "flamingock-ce-mongodb-springdata-v3"
project(":community:flamingock-ce-mongodb-springdata-v3").projectDir = file("community/flamingock-ce-mongodb-springdata-v3")

include("community:flamingock-ce-mongodb-springdata-v2")
project(":community:flamingock-ce-mongodb-springdata-v2").name = "flamingock-ce-mongodb-springdata-v2"
project(":community:flamingock-ce-mongodb-springdata-v2").projectDir = file("community/flamingock-ce-mongodb-springdata-v2")

include("community:flamingock-ce-mongodb-springdata-v4")
project(":community:flamingock-ce-mongodb-springdata-v4").name = "flamingock-ce-mongodb-springdata-v4"
project(":community:flamingock-ce-mongodb-springdata-v4").projectDir = file("community/flamingock-ce-mongodb-springdata-v4")

include("community:flamingock-ce-couchbase")
project(":community:flamingock-ce-couchbase").name = "flamingock-ce-couchbase"
project(":community:flamingock-ce-couchbase").projectDir = file("community/flamingock-ce-couchbase")

include("community:flamingock-ce-dynamodb")
project(":community:flamingock-ce-dynamodb").name = "flamingock-ce-dynamodb"
project(":community:flamingock-ce-dynamodb").projectDir = file("community/flamingock-ce-dynamodb")

//////////////////////////////////////
// PLUGINS
//////////////////////////////////////
include("platform-plugins:flamingock-springboot-v2-integration")
project(":platform-plugins:flamingock-springboot-v2-integration").name = "flamingock-springboot-v2-integration"
project(":platform-plugins:flamingock-springboot-v2-integration").projectDir = file("platform-plugins/flamingock-springboot-v2-integration")

include("platform-plugins:flamingock-springboot-v3-integration")
project(":platform-plugins:flamingock-springboot-v3-integration").name = "flamingock-springboot-v3-integration"
project(":platform-plugins:flamingock-springboot-v3-integration").projectDir = file("platform-plugins/flamingock-springboot-v3-integration")

//////////////////////////////////////
// TRANSACTIONERS
//////////////////////////////////////

include("transactioners:sql-transactioner")
project(":transactioners:sql-transactioner").projectDir = file("transactioners/sql-transactioner")
project(":transactioners:sql-transactioner").name = "sql-transactioner"

include("transactioners:mongodb-sync-v4-transactioner")
project(":transactioners:mongodb-sync-v4-transactioner").projectDir = file("transactioners/mongodb-sync-v4-transactioner")
project(":transactioners:mongodb-sync-v4-transactioner").name = "mongodb-sync-v4-transactioner"

include("transactioners:dynamodb-transactioner")
project(":transactioners:dynamodb-transactioner").projectDir = file("transactioners/dynamodb-transactioner")
project(":transactioners:dynamodb-transactioner").name = "dynamodb-transactioner"



//////////////////////////////////////
// TEMPLATES
//////////////////////////////////////

//SQL
include("templates:flamingock-sql-template")
project(":templates:flamingock-sql-template").name = "flamingock-sql-template"
project(":templates:flamingock-sql-template").projectDir = file("templates/flamingock-sql-template")


//MONGODB
include("templates:flamingock-mongodb-change-template")
project(":templates:flamingock-mongodb-change-template").name = "flamingock-mongodb-change-template"
project(":templates:flamingock-mongodb-change-template").projectDir = file("templates/flamingock-mongodb-change-template")


//////////////////////////////////////
// INTERNAL
//////////////////////////////////////
include("importers:mongodb-importer-sync-v4")
project(":importers:mongodb-importer-sync-v4").name = "mongodb-importer-sync-v4"
project(":importers:mongodb-importer-sync-v4").projectDir = file("importers/mongodb-importer-sync-v4")


include("importers:mongodb-importer-v3")
project(":importers:mongodb-importer-v3").name = "mongodb-importer-v3"
project(":importers:mongodb-importer-v3").projectDir = file("importers/mongodb-importer-v3")


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