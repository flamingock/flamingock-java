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
package io.flamingock.internal.util;

public final class Constants {
  public static final String PROXY_FLAMINGOCK_PREFIX = "_$$_flamingock_";

  public static long DEFAULT_LOCK_ACQUIRED_FOR_MILLIS = 60 * 1000L;//1 minute
  public static long DEFAULT_QUIT_TRYING_AFTER_MILLIS = 3 * 60 * 1000L;//3 minutes
  public static long DEFAULT_TRY_FREQUENCY_MILLIS = 1000L;//1 second


  public static final String DEFAULT_CLOUD_AUDIT_STORE = "cloud-audit-store";
  public static final String DEFAULT_MONGODB_AUDIT_STORE = "mongodb-audit-store";
  public static final String DEFAULT_DYNAMODB_AUDIT_STORE = "dynamodb-audit-store";
  public static final String DEFAULT_COUCHBASE_AUDIT_STORE = "couchbase-audit-store";
  public static final String DEFAULT_SQL_AUDIT_STORE = "sql-audit-store";
  public static final String DEFAULT_IN_MEMORY_AUDIT_STORE = "in_memory-audit-store";



  private Constants() {}



}
