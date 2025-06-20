/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.task.ChangeCategory;
import io.flamingock.api.task.ChangeCategoryAware;
import io.flamingock.internal.common.core.error.validation.Validatable;

import io.flamingock.internal.common.core.task.AbstractTaskDescriptor;
import io.flamingock.internal.core.pipeline.loaded.stage.StageValidationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import java.util.Optional;

public abstract class AbstractLoadedTask extends AbstractTaskDescriptor implements Validatable<StageValidationContext>, ChangeCategoryAware {

    public AbstractLoadedTask(String id,
                              String order,
                              String source,
                              boolean runAlways,
                              boolean transactional,
                              boolean system) {
        super(id, order, source, runAlways, transactional, system);
    }

    public abstract Constructor<?> getConstructor();

    public abstract Method getExecutionMethod();

    public abstract Optional<Method> getRollbackMethod();

    @Override
    public abstract boolean hasCategory(ChangeCategory property);


}
