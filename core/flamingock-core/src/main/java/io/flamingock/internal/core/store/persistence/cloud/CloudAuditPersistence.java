/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.store.persistence.cloud;

import io.flamingock.internal.core.plan.ExecutionPlanner;
import io.flamingock.internal.util.id.EnvironmentId;
import io.flamingock.internal.util.id.ServiceId;
import io.flamingock.internal.core.store.audit.AuditPersistence;

public interface CloudAuditPersistence extends AuditPersistence {

    //TODO remove this when cloudBuilder moved to cloud module
    ExecutionPlanner getExecutionPlanner();

    EnvironmentId getEnvironmentId();

    ServiceId getServiceId();

    String getJwt();
}
