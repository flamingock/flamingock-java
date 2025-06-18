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

package io.flamingock.internal.util;

import org.jetbrains.annotations.TestOnly;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Extending `TimeUtil` to avoid hard coding instances(Instant.now, LocalDateTime.now(), etc.)
 */
public class TimeService {

  private static TimeService defaultInstance = new TimeService();

  public static TimeService getDefault() {
    return defaultInstance;
  }

  @TestOnly
  public static void setDefault(TimeService newDefault) {
    defaultInstance = newDefault;
  }

  /**
   * @param millis milliseconds to add to the Date
   * @return current date plus milliseconds passed as parameter
   */
  public LocalDateTime currentDatePlusMillis(long millis) {
    return LocalDateTime.now().plus(millis, ChronoUnit.MILLIS);
  }

  /**
   * @return current Date
   */
  @Deprecated
  public Date currentTimeOld() {
    return new Date(System.currentTimeMillis());
  }


  public LocalDateTime currentDateTime() {
    return LocalDateTime.now();
  }

  public long currentMillis() {
    return Instant.now().toEpochMilli();
  }




  private Instant nowInstant() {
    return Instant.now(Clock.systemDefaultZone());
  }

  public Instant nowPlusMillis(long millis) {
    return nowInstant().plusMillis(millis);
  }

  public boolean isPast(Instant moment) {
    return nowInstant().isAfter(moment);
  }

  public boolean isPast(LocalDateTime dateTime) {
    return currentDateTime().isAfter(dateTime);
  }

  public long daysToMills(int days) {
    return (long) days * 24 * 60 * 60 * 1000 ;
  }
}
