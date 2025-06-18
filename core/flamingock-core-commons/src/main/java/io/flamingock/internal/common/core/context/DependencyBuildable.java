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

package io.flamingock.internal.common.core.context;

import java.util.function.Function;


/**
 * This class it to hold dependencies that have as implementation a dependency from the DependencyContext itself. So they
 * need to retrieve the impl from the dependencyManager
 */
public class DependencyBuildable extends Dependency {

  private final Function<Object, Object> decoratorFunction;
  private final Class<?> implType;

  public DependencyBuildable(Class<?> type, Class<?> implType, Function<Object, Object> decoratorFunction, boolean implProxeable) {
    super(DEFAULT_NAME, type, implProxeable);
    this.decoratorFunction = decoratorFunction;
    this.implType = implType;
  }

  public void setImpl(Object impl) {
    this.instance = impl;
  }

  @Override
  public Object getInstance() {
    return decoratorFunction.apply(instance);
  }


  public Function<Object, Object> getDecoratorFunction() {
    return decoratorFunction;
  }

  public Class<?> getImplType() {
    return implType;
  }
}
