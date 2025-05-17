rootProject.name = "flamingock-project"



//////////////////////////////////////
// CORE
//////////////////////////////////////
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

include("flamingock-processor")
project(":flamingock-processor").projectDir = file("flamingock-processor")
project(":flamingock-processor").name = "flamingock-processor"

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
// CLOUD
//////////////////////////////////////
include("cloud-edition")
project(":cloud-edition").name = "cloud-edition"
project(":cloud-edition").projectDir = file("cloud-edition")

//////////////////////////////////////
// DRIVERS
//////////////////////////////////////

include("community-edition-commons")
project(":community-edition-commons").name = "community-edition-commons"
project(":community-edition-commons").projectDir = file("community-edition-commons")

include("commons:mongodb-facade")
project(":commons:mongodb-facade").name = "mongodb-facade"
project(":commons:mongodb-facade").projectDir = file("commons/mongodb-facade")

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

include("commons:dynamodb-utils")
project(":commons:dynamodb-utils").name = "dynamodb-utils"
project(":commons:dynamodb-utils").projectDir = file("commons/dynamodb-utils")

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


//MONGODB
include("templates:mongodb-change-template")
project(":templates:mongodb-change-template").name = "mongodb-change-template"
project(":templates:mongodb-change-template").projectDir = file("templates/mongodb-change-template")

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
include("importers:mongodb-importer-sync-v4")
project(":importers:mongodb-importer-sync-v4").name = "mongodb-importer-sync-v4"
project(":importers:mongodb-importer-sync-v4").projectDir = file("importers/mongodb-importer-sync-v4")


include("importers:mongodb-importer-v3")
project(":importers:mongodb-importer-v3").name = "mongodb-importer-v3"
project(":importers:mongodb-importer-v3").projectDir = file("importers/mongodb-importer-v3")
