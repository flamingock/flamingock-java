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

package io.flamingock.core.util;

import java.net.ContentHandlerFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class JdkUtil {

  private JdkUtil() {
  }

  private static final List<String> jdkInternalPackages = Arrays.asList(
      "java.",
      "com.sun.",
      "javax.",
      "jdk.",
      "sun.",
      "netscape.javascript.",
      "org.ietf.jgss.",
      "org.w3c.",
      "org.xml.");

  public static boolean isInternalJdkClass(Class<?> clazz) {
    return clazz.isPrimitive()
        || isJdkNativeType(clazz)
        || isJdkDataStructure(clazz)
        || isInternalJdkPackage(clazz)
        || isOtherWellKnownClassesNonProxiable(clazz);
  }

  private static boolean isInternalJdkPackage(Class<?> clazz) {
    //Some JDK internal classes return null in method getPackage()
    String packageName = clazz.getPackage() != null ? clazz.getPackage().getName() : clazz.getName();
    return jdkInternalPackages.stream().anyMatch(packageName::startsWith);
  }

  //should be added all the extra classes that shouldn't be proxiable
  private static boolean isOtherWellKnownClassesNonProxiable(Class<?> clazz) {
    return ContentHandlerFactory.class.isAssignableFrom(clazz);
  }

  private static boolean isJdkNativeType(Class<?> clazz) {
    return Boolean.class.equals(clazz)
        || String.class.equals(clazz)
        || Class.class.equals(clazz)
        || Character.class.equals(clazz)
        || Byte.class.equals(clazz)
        || Short.class.equals(clazz)
        || Integer.class.equals(clazz)
        || Long.class.equals(clazz)
        || Float.class.equals(clazz)
        || Double.class.equals(clazz)
        || Void.class.equals(clazz);
  }

  private static boolean isJdkDataStructure(Class<?> clazz) {
    return Iterable.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz);
    //should be added all the JDK data structure that shouldn't be proxied
  }
}
