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

package io.flamingock.core.api.error;


/**
 * Exception thrown when a Flamingock operation fails.
 */
public class FlamingockException extends RuntimeException {


  public FlamingockException() {
    super();
  }

  public FlamingockException(Throwable cause) {
    super(cause);
  }

  public FlamingockException(String message) {
    super(message);
  }

  public FlamingockException(String formattedMessage, Object... args) {
    super(String.format(formattedMessage, args));
  }

  public FlamingockException(Throwable cause, String formattedMessage, Object... args) {
    this(String.format(formattedMessage, args), cause);
  }

  public FlamingockException(Throwable cause, String message) {
    super(message, cause);
  }

}
