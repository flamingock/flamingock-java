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

package io.flamingock.internal.core.runtime;


import io.flamingock.core.error.FlamingockException;

public class MissingInjectedParameterException extends FlamingockException {

  private final Class<?> wrongParameter;
  private final String name;

  public MissingInjectedParameterException(Class<?> wrongParameter, String name) {
    super();
    this.wrongParameter = wrongParameter;
    this.name = name;
  }

  public Class<?> getWrongParameter() {
    return wrongParameter;
  }

  public String getName() {
    return name;
  }

  @Override
  public String getMessage() {


    StringBuilder sb = new StringBuilder("Wrong parameter[")
        .append(getWrongParameter().getSimpleName())
        .append("]");
    if (name != null) {
      sb.append(" with name: ")
          .append(name);
    }
    sb.append(". Dependency not found.");
    return sb.toString();
  }
}
