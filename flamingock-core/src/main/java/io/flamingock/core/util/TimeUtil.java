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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static LocalDateTime fromIso8601(String dateFormatted) {
        return LocalDateTime.parse(dateFormatted, DateTimeFormatter.ISO_DATE_TIME);
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        } else if (value.getClass().equals(Date.class)) {
            return toLocalDateTime((Date)value);
        } else if (value.getClass().equals(LocalDateTime.class)) {
            return (LocalDateTime) value;
        } else if (value.getClass().equals(Long.class)) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli((Long)value), TimeZone.getDefault().toZoneId());
        }
         else {
            throw new RuntimeException(String.format("%s cannot be cast to %s", value.getClass().getName(), Date.class.getName()));
        }
    }

    public static Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    public static Date toDate(long epochMillis) {
        LocalDateTime localDateTime = Instant.
                ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return toDate(localDateTime);
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Converts minutes to milliseconds
     *
     * @param minutes minutes to be converted
     * @return equivalent to the minutes passed in milliseconds
     */
    public static long millisToMinutes(long minutes) {
        return minutes / (60 * 1000);
    }

    public static long toMillis(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long diffInMillis(LocalDateTime dateTime1, LocalDateTime dateTime2) {
        return toMillis(dateTime1) - toMillis(dateTime2);
    }

}
