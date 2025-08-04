/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.targets;

import io.flamingock.internal.common.core.context.ContextInitializable;
import io.flamingock.internal.core.transaction.TransactionWrapper;

import java.util.Optional;

public abstract class TransactionalTargetSystem<HOLDER extends TransactionalTargetSystem<HOLDER>>
        extends AbstractTargetSystem<HOLDER>
        implements ContextInitializable {

    protected boolean autoCreate = true;

    public TransactionalTargetSystem(String id) {
        super(id);
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    abstract public Optional<OngoingTaskStatusRepository> getOnGoingTaskStatusRepository();

    abstract public TransactionWrapper getTxWrapper();
}
