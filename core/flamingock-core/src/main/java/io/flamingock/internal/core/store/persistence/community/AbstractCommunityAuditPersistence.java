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
package io.flamingock.internal.core.store.persistence.community;

import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;

public abstract class AbstractCommunityAuditPersistence implements CommunityAuditPersistence {

    protected final CommunityConfigurable localConfiguration;

    protected AbstractCommunityAuditPersistence(CommunityConfigurable localConfiguration) {
        this.localConfiguration = localConfiguration;
    }

    abstract protected void doInitialize(RunnerId runnerId);

    public void initialize(RunnerId runnerId) {
        doInitialize(runnerId);
    }
}
