/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.core.runner;

import io.flamingock.core.util.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.UUID;

public final class RunnerId extends Id {

    private static final Logger logger = LoggerFactory.getLogger(RunnerId.class);
    public static final String DELIMITER = "_";


    public static RunnerId generate() {
        return generate(null);
    }

    public static RunnerId generate(String prefix) {
        StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix).append(DELIMITER);
        }
        try {
            sb.append(Inet4Address.getLocalHost().getHostName()).append(DELIMITER);
        } catch (final UnknownHostException e) {
            logger.warn(e.getMessage(), e);
        }
        sb.append(UUID.randomUUID());

        return new RunnerId(sb.toString());
    }

    public static RunnerId fromString(String value) {
        return new RunnerId(value);
    }

    private RunnerId(String value) {
        super(value);
    }

}
