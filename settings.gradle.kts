rootProject.name = "flamingock-project"



//////////////////////////////////////
// CORE
//////////////////////////////////////
include("flamingock-core-template")
project(":flamingock-core-template").projectDir = file("flamingock-core-template")
project(":flamingock-core-template").name = "flamingock-core-template"


include("cloud-transactioners:sql-cloud-transactioner")
project(":cloud-transactioners:sql-cloud-transactioner").projectDir = file("cloud-transactioners/sql-cloud-transactioner")
project(":cloud-transactioners:sql-cloud-transactioner").name = "sql-cloud-transactioner"

include("cloud-transactioners:mongodb-sync-v4-cloud-transactioner")
project(":cloud-transactioners:mongodb-sync-v4-cloud-transactioner").projectDir = file("cloud-transactioners/mongodb-sync-v4-cloud-transactioner")
project(":cloud-transactioners:mongodb-sync-v4-cloud-transactioner").name = "mongodb-sync-v4-cloud-transactioner"

include("cloud-transactioners:dynamodb-cloud-transactioner")
project(":cloud-transactioners:dynamodb-cloud-transactioner").projectDir = file("cloud-transactioners/dynamodb-cloud-transactioner")
project(":cloud-transactioners:dynamodb-cloud-transactioner").name = "dynamodb-cloud-transactioner"


include("flamingock-core-api")
project(":flamingock-core-api").name = "flamingock-core-api"
project(":flamingock-core-api").projectDir = file("flamingock-core-api")

include("flamingock-core")
project(":flamingock-core").projectDir = file("flamingock-core")
project(":flamingock-core").name = "flamingock-core"

include("flamingock-springboot-v2-runner")
project(":flamingock-springboot-v2-runner").projectDir = file("flamingock-springboot-v2-runner")
project(":flamingock-springboot-v2-runner").name = "flamingock-springboot-v2-runner"

include("flamingock-springboot-v3-runner")
project(":flamingock-springboot-v3-runner").projectDir = file("flamingock-springboot-v3-runner")
project(":flamingock-springboot-v3-runner").name = "flamingock-springboot-v3-runner"

include("flamingock-graalvm")
project(":flamingock-graalvm").projectDir = file("flamingock-graalvm")
project(":flamingock-graalvm").name = "flamingock-graalvm"

//////////////////////////////////////
// DRIVERS
//////////////////////////////////////


include("local-drivers:mongodb:mongodb-facade")
project(":local-drivers:mongodb:mongodb-facade").name = "mongodb-facade"
project(":local-drivers:mongodb:mongodb-facade").projectDir = file("local-drivers/mongodb/mongodb-facade")

include("local-drivers:mongodb:mongodb-sync-v4-driver")
project(":local-drivers:mongodb:mongodb-sync-v4-driver").name = "mongodb-sync-v4-driver"
project(":local-drivers:mongodb:mongodb-sync-v4-driver").projectDir = file("local-drivers/mongodb/mongodb-sync-v4-driver")

include("local-drivers:mongodb:mongodb-v3-driver")
project(":local-drivers:mongodb:mongodb-v3-driver").name = "mongodb-v3-driver"
project(":local-drivers:mongodb:mongodb-v3-driver").projectDir = file("local-drivers/mongodb/mongodb-v3-driver")

include("local-drivers:mongodb:mongodb-springdata-v3-driver")
project(":local-drivers:mongodb:mongodb-springdata-v3-driver").name = "mongodb-springdata-v3-driver"
project(":local-drivers:mongodb:mongodb-springdata-v3-driver").projectDir = file("local-drivers/mongodb/mongodb-springdata-v3-driver")

include("local-drivers:mongodb:mongodb-springdata-v2-driver")
project(":local-drivers:mongodb:mongodb-springdata-v2-driver").name = "mongodb-springdata-v2-driver"
project(":local-drivers:mongodb:mongodb-springdata-v2-driver").projectDir = file("local-drivers/mongodb/mongodb-springdata-v2-driver")

include("local-drivers:mongodb:mongodb-springdata-v4-driver")
project(":local-drivers:mongodb:mongodb-springdata-v4-driver").name = "mongodb-springdata-v4-driver"
project(":local-drivers:mongodb:mongodb-springdata-v4-driver").projectDir = file("local-drivers/mongodb/mongodb-springdata-v4-driver")

include("local-drivers:couchbase:couchbase-driver")
project(":local-drivers:couchbase:couchbase-driver").name = "couchbase-driver"
project(":local-drivers:couchbase:couchbase-driver").projectDir = file("local-drivers/couchbase/couchbase-driver")

include("local-drivers:couchbase:couchbase-springboot-v2-driver")
project(":local-drivers:couchbase:couchbase-springboot-v2-driver").name = "couchbase-springboot-v2-driver"
project(":local-drivers:couchbase:couchbase-springboot-v2-driver").projectDir = file("local-drivers/couchbase/couchbase-springboot-v2-driver")

include("local-drivers:dynamodb:dynamodb-driver")
project(":local-drivers:dynamodb:dynamodb-driver").name = "dynamodb-driver"
project(":local-drivers:dynamodb:dynamodb-driver").projectDir = file("local-drivers/dynamodb/dynamodb-driver")

//////////////////////////////////////
// TEMPLATES
//////////////////////////////////////

//SQL
include("templates:sql-template")
project(":templates:sql-template").name = "sql-template"
project(":templates:sql-template").projectDir = file("templates/sql-template")


include("templates:sql-springboot-template")
project(":templates:sql-springboot-template").name = "sql-springboot-template"
project(":templates:sql-springboot-template").projectDir = file("templates/sql-springboot-template")


//////////////////////////////////////
// UTILS
//////////////////////////////////////
include("utils-test")
project(":utils-test").name = "utils-test"
project(":utils-test").projectDir = file("utils-test")

include("utils")
project(":utils").name = "utils"
project(":utils").projectDir = file("utils")


//////////////////////////////////////
// INTERNAL
//////////////////////////////////////
include("cloud-importers:importer-common")
project(":cloud-importers:importer-common").name = "importer-common"
project(":cloud-importers:importer-common").projectDir = file("cloud-importers/importer-common")

include("cloud-importers:mongodb:mongodb-sync4-cloud-importer-legacy")
project(":cloud-importers:mongodb:mongodb-sync4-cloud-importer-legacy").name = "mongodb-sync4-cloud-importer-legacy"
project(":cloud-importers:mongodb:mongodb-sync4-cloud-importer-legacy").projectDir = file("cloud-importers/mongodb/mongodb-sync4-cloud-importer-legacy")

include("cloud-importers:mongodb:mongodb-sync4-cloud-importer-local")
project(":cloud-importers:mongodb:mongodb-sync4-cloud-importer-local").name = "mongodb-sync4-cloud-importer-local"
project(":cloud-importers:mongodb:mongodb-sync4-cloud-importer-local").projectDir = file("cloud-importers/mongodb/mongodb-sync4-cloud-importer-local")

include("cloud-importers:dynamodb:dynamodb-cloud-importer-legacy")
project(":cloud-importers:dynamodb:dynamodb-cloud-importer-legacy").name = "dynamodb-cloud-importer-legacy"
project(":cloud-importers:dynamodb:dynamodb-cloud-importer-legacy").projectDir = file("cloud-importers/dynamodb/dynamodb-cloud-importer-legacy")
