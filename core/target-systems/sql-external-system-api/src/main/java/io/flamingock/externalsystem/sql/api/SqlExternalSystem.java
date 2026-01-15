<<<<<<< HEAD:core/target-systems/sql-target-system/src/main/java/io/flamingock/targetsystem/sql/SqlExternalSystem.java
/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.targetsystem.sql;
=======
package io.flamingock.externalsystem.sql.api;
>>>>>>> 5cb03a89 (refactor: sql external system api):core/target-systems/sql-external-system-api/src/main/java/io/flamingock/externalsystem/sql/api/SqlExternalSystem.java

import io.flamingock.api.external.ExternalSystem;

import javax.sql.DataSource;

public interface SqlExternalSystem extends ExternalSystem {
    DataSource getDataSource();
}
