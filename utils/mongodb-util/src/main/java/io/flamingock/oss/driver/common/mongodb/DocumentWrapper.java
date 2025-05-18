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

package io.flamingock.oss.driver.common.mongodb;

import io.flamingock.commons.utils.TimeUtil;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;

public interface DocumentWrapper {


    DocumentWrapper append(String key, Object value);
    Object get(String key);
    String getString(String key);
    boolean containsKey(String key);
    Boolean getBoolean(String key);
    boolean getBoolean(Object key, boolean defaultValue);

    DocumentWrapper getWithWrapper(String key);

    int size();

    default LocalDateTime getDate(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        } else if (value.getClass().equals(Date.class)) {
            return TimeUtil.toLocalDateTime(value);

        } else if (value.getClass().equals(LocalDateTime.class)) {
            return (LocalDateTime) value;

        } if (Map.class.isAssignableFrom(value.getClass())) {
            return TimeUtil.fromIso8601((String)get("$date"));

        } else {
            throw new RuntimeException(String.format("%s cannot be cast to %s", value.getClass().getName(), Date.class.getName()));
        }
    }
}
