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

package io.flamingock.core.task.descriptor.change;

import io.flamingock.commons.utils.ReflectionUtil;
import io.flamingock.core.api.annotations.FlamingockConstructor;
import io.flamingock.core.api.exception.FlamingockException;
import io.flamingock.core.task.descriptor.ReflectionLoadedTask;
import io.mongock.api.annotations.ChangeUnitConstructor;

import java.lang.reflect.Constructor;

public abstract class AbstractLoadedChangeUnit extends ReflectionLoadedTask {

    private final boolean isNewChangeUnit;

    public AbstractLoadedChangeUnit(String id,
                                    String order,
                                    Class<?> source,
                                    boolean runAlways,
                                    boolean transactional,
                                    boolean isNewChangeUnit) {
        super(id, order, source, runAlways, transactional);
        this.isNewChangeUnit = isNewChangeUnit;
    }

    @Override
    public Constructor<?> getConstructor() {
        if (isNewChangeUnit()) {
            try {
                return ReflectionUtil.getConstructorWithAnnotationPreference(source, FlamingockConstructor.class);
            } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
                throw new FlamingockException("Found multiple constructors for class[%s] annotated with %s." +
                        " Annotate the one you want Flamingock to use to instantiate your changeUnit",
                        source.getName(),
                        FlamingockConstructor.class.getName());
            } catch (ReflectionUtil.MultipleConstructorsFound ex) {
                throw new FlamingockException("Found multiple constructors, please provide at least one  for class[%s].\n" +
                        "When more than one constructor, exactly one of them must be annotated. And it will be taken as default "
                        , FlamingockConstructor.class.getSimpleName()
                        , source.getName()
                );
            } catch (ReflectionUtil.ConstructorNotFound ex) {
                throw new FlamingockException("Cannot find a valid constructor for class[%s]", source.getName());
            }
        } else {
            try {
                return ReflectionUtil.getConstructorWithAnnotationPreference(source, ChangeUnitConstructor.class);
            } catch (ReflectionUtil.MultipleAnnotatedConstructorsFound ex) {
                throw new FlamingockException("Found multiple LEGACY constructors for class[%s] annotated with %s." +
                        " Annotate the one you want Flamingock to use to instantiate your changeUnit.\n" +
                        "Note: It's highly recommended to use the new API(@FlamingockConstructor)",
                        source.getName(),
                        ChangeUnitConstructor.class.getName());
            } catch (ReflectionUtil.MultipleConstructorsFound ex) {
                throw new FlamingockException("Found multiple constructors, please provide at least one for class[%s].\n" +
                        "When more than one constructor, exactly one of them must be annotated. And it will be taken as default\n" +
                        "Note: It's highly recommended to use the new API(@FlamingockConstructor)"
                        , ChangeUnitConstructor.class.getSimpleName()
                        , source.getName()
                );
            } catch (ReflectionUtil.ConstructorNotFound ex) {
                throw new FlamingockException("Cannot find a valid constructor for class[%s]", source.getName());
            }
        }
    }

    public boolean isNewChangeUnit() {
        return isNewChangeUnit;
    }
}
