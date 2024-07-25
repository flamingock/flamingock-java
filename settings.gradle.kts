rootProject.name = "flamingock-project"



//////////////////////////////////////
// CORE
//////////////////////////////////////
include("flamingock-template")
project(":flamingock-template").projectDir = file("flamingock-template")
project(":flamingock-template").name = "flamingock-template"


include("cloud-transactioners:sql-cloud-transactioner")
project(":cloud-transactioners:sql-cloud-transactioner").projectDir = file("cloud-transactioners/sql-cloud-transactioner")
project(":cloud-transactioners:sql-cloud-transactioner").name = "sql-cloud-transactioner"

include("flamingock-core")
project(":flamingock-core").projectDir = file("flamingock-core")
project(":flamingock-core").name = "flamingock-core"

include("flamingock-springboot-v2-runner")
project(":flamingock-springboot-v2-runner").projectDir = file("flamingock-springboot-v2-runner")
project(":flamingock-springboot-v2-runner").name = "flamingock-springboot-v2-runner"

include("flamingock-springboot-v3-runner")
project(":flamingock-springboot-v3-runner").projectDir = file("flamingock-springboot-v3-runner")
project(":flamingock-springboot-v3-runner").name = "flamingock-springboot-v3-runner"

//////////////////////////////////////
// COMMUNITY
//////////////////////////////////////
include("local-drivers:driver-common")
project(":local-drivers:driver-common").name = "driver-common"
project(":local-drivers:driver-common").projectDir = file("local-drivers/driver-common")


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
// EXAMPLES
//////////////////////////////////////

include("examples:databases:mongodb-sync-standalone")
project(":examples:databases:mongodb-sync-standalone").name = "mongodb-sync-standalone"
project(":examples:databases:mongodb-sync-standalone").projectDir = file("examples/databases/mongodb-sync-standalone")

include("examples:databases:mongodb-springboot-sync")
project(":examples:databases:mongodb-springboot-sync").name = "mongodb-springboot-sync"
project(":examples:databases:mongodb-springboot-sync").projectDir = file("examples/databases/mongodb-springboot-sync")

include("examples:databases:mongodb-springboot-springdata")
project(":examples:databases:mongodb-springboot-springdata").name = "mongodb-springboot-springdata"
project(":examples:databases:mongodb-springboot-springdata").projectDir = file("examples/databases/mongodb-springboot-springdata")

include("examples:databases:mongodb-springboot-v3-springdata-v4")
project(":examples:databases:mongodb-springboot-v3-springdata-v4").name = "mongodb-springboot-v3-springdata-v4"
project(":examples:databases:mongodb-springboot-v3-springdata-v4").projectDir = file("examples/databases/mongodb-springboot-v3-springdata-v4")

include("examples:databases:mysql-springboot")
project(":examples:databases:mysql-springboot").name = "mysql-springboot"
project(":examples:databases:mysql-springboot").projectDir = file("examples/databases/mysql-springboot")

include("examples:databases:couchbase-standalone")
project(":examples:databases:couchbase-standalone").name = "couchbase-standalone"
project(":examples:databases:couchbase-standalone").projectDir = file("examples/databases/couchbase-standalone")

include("examples:databases:couchbase-springboot-v2")
project(":examples:databases:couchbase-springboot-v2").name = "couchbase-springboot-v2"
project(":examples:databases:couchbase-springboot-v2").projectDir = file("examples/databases/couchbase-springboot-v2")
include("examples:databases:mysql-standalone")
findProject(":examples:databases:mysql-standalone")?.name = "mysql-standalone"

