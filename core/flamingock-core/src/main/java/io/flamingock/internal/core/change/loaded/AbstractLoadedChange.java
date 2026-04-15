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
package io.flamingock.internal.core.change.loaded;

import io.flamingock.internal.common.core.error.validation.Validatable;

import io.flamingock.internal.common.core.change.AbstractChangeDescriptor;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.Optional;

public abstract class AbstractLoadedChange extends AbstractChangeDescriptor implements Validatable<StageValidationContext> {

    private final boolean transactional;

    public AbstractLoadedChange(String id,
                                String order,
                                String author,
                                String implementationSourceName,
                                String sourceFile,
                                boolean runAlways,
                                Boolean transactionalFlag,
                                boolean transactional,
                                boolean system,
                                TargetSystemDescriptor targetSystem,
                                RecoveryDescriptor recovery,
                                boolean legacy) {
        super(id, order, author, implementationSourceName, sourceFile, runAlways, transactionalFlag, system, targetSystem, recovery, legacy);
        this.transactional = transactional;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public abstract Constructor<?> getConstructor();

    public abstract Method getApplyMethod();

    public abstract Optional<Method> getRollbackMethod();



}
