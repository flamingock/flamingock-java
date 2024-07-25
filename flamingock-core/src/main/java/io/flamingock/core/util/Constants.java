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

public final class Constants {
  public static final String LEGACY_MIGRATION_NAME = "legacy-migration";
  public static final String PROXY_MONGOCK_PREFIX = "_$$_mongock_";
  public static final String CLI_PROFILE = "mongock-cli-profile";
  public static final String NON_CLI_PROFILE = "!" + CLI_PROFILE;

  public static final String DEFAULT_MIGRATION_AUTHOR = "default_author";
  public static long DEFAULT_LOCK_ACQUIRED_FOR_MILLIS = 60 * 1000L;//1 minute
  public static long DEFAULT_QUIT_TRYING_AFTER_MILLIS = 3 * 60 * 1000L;//3 minutes
  public static long DEFAULT_TRY_FREQUENCY_MILLIS = 1000L;//1 second

  private Constants() {}



}
