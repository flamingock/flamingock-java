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

package io.flamingock.core.task.descriptor;

import io.flamingock.core.api.annotations.ChangeUnit;

public class ReflectionTaskDescriptorBuilder {
    private static final ReflectionTaskDescriptorBuilder instance = new ReflectionTaskDescriptorBuilder();

    public static ReflectionTaskDescriptorBuilder recycledBuilder() {
        return instance;
    }

    private Class<?> source;

    private ReflectionTaskDescriptorBuilder() {
    }

    public ReflectionTaskDescriptorBuilder setSource(Class<?> source) {
        this.source = source;
        return this;
    }


    public ReflectionTaskDescriptor build() {
        if (isChangeUnit(source)) {
            return getDescriptorFromChangeUnit(source);
        } else {
            throw new IllegalArgumentException(String.format("Task type not recognised in class[%s]", source.getName()));
        }
    }

    private static boolean isChangeUnit(Class<?> source) {
        return source.isAnnotationPresent((ChangeUnit.class));
    }

    private static ReflectionTaskDescriptor getDescriptorFromChangeUnit(Class<?> source) {
        ChangeUnit changeUnitAnnotation = source.getAnnotation(ChangeUnit.class);

        return new ReflectionTaskDescriptor(
                changeUnitAnnotation.id(),
                changeUnitAnnotation.order(),
                source,
                changeUnitAnnotation.runAlways(),
                changeUnitAnnotation.transactional());
    }
}
