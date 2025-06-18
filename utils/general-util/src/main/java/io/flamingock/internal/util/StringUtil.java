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

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class StringUtil {
    private StringUtil() {
    }

    public static String executionId() {
        return String.format(
                "%s-%s",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd-HH_mm_ss_SSSSSSSSS")),
                String.valueOf(UUID.randomUUID().getMostSignificantBits()).replace("-", ""));
    }

    public static String getBeforeExecutionId(String baseId) {
        return String.format("%s_%s", baseId, "before");
    }



    public static String hostname() {
        return hostname("");
    }

    public static String hostname(String serviceIdentifier) {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown-host";
        }

        if (!isEmpty(serviceIdentifier)) {
            hostname += "-";
            hostname += serviceIdentifier;
        }
        return hostname;
    }

    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }
}
