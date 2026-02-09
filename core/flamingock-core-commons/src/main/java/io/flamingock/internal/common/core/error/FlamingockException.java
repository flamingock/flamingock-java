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
package io.flamingock.internal.common.core.error;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Exception thrown when a Flamingock operation fails.
 */
public class FlamingockException extends RuntimeException {


  private static final int SAFE_GUARD = 32;

  public FlamingockException(Throwable cause) {
    super(cause);
  }

  public FlamingockException(String message) {
    super(message);
  }

  public FlamingockException(String message, Throwable cause) {
    super(message, cause);
  }

  public FlamingockException(String message, Object... args) {
    super(String.format(message, args));
  }

  public static FlamingockException toFlamingockException(Throwable t) {
    if (t == null) return new FlamingockException(new NullPointerException("Throwable is null"));

    // Peel common wrapper layers
    Throwable cur = t;
    int guard = 0;
    while (guard++ < SAFE_GUARD) { // safety guard
      if (cur instanceof FlamingockException) {
        return (FlamingockException) cur;
      }
      if (cur instanceof InvocationTargetException ) {
        Throwable next = ((InvocationTargetException)cur).getTargetException();
        if (next != null && next != cur) { cur = next; continue; }
        break;
      }
      if (cur instanceof UndeclaredThrowableException ) {
        Throwable next = ((UndeclaredThrowableException)cur).getUndeclaredThrowable();
        if (next != null && next != cur) { cur = next; continue; }
        break;
      }
      if (cur instanceof ExecutionException || cur instanceof CompletionException) {
        Throwable next = cur.getCause();
        if (next != null && next != cur) { cur = next; continue; }
        break;
      }
      break;
    }

    // If after peeling we hit FlamingockException, return it
    if (cur instanceof FlamingockException) return (FlamingockException) cur;

    // Otherwise wrap once
    return new FlamingockException(cur);
  }


}
