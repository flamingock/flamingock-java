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
package io.flamingock.internal.util.id;

import io.flamingock.internal.util.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.UUID;

/**
 * {@code RunnerId} represents a unique identifier for a specific runner execution.
 * <p>
 * The ID format is: {@code <serviceId>@<hostname>#<uuid>}, where:
 * <ul>
 *   <li>{@code serviceId} identifies the logical service or module (e.g. "contract-service")</li>
 *   <li>{@code hostname} identifies the machine or container running the instance</li>
 *   <li>{@code uuid} ensures uniqueness per execution</li>
 * </ul>
 * <p>
 * This format is optimized for:
 * <ul>
 *   <li>Uniqueness across executions</li>
 *   <li>Traceability across services and instances</li>
 *   <li>Safe exposure in external systems and logs</li>
 *   <li>Persistence and log readability</li>
 * </ul>
 */
public final class RunnerId extends Id implements Property {

    private static final Logger logger = LoggerFactory.getLogger(RunnerId.class);
    private static final String PROPERTY_KEY = "runner.id";

    /**
     * Generates a new {@link RunnerId} using a randomly generated service identifier.
     *
     * @return a unique {@code RunnerId} for the current execution
     */
    public static RunnerId generate() {
        return generate(null);
    }

    /**
     * Generates a new {@link RunnerId} using the provided service identifier.
     * <p>
     * If the service identifier or hostname is null or empty, a UUID will be used instead.
     *
     * @param serviceIdentifier the logical name of the service (can be null)
     * @return a unique {@code RunnerId} for the current execution
     */
    public static RunnerId generate(String serviceIdentifier) {
        return new RunnerId(cleanOrUuid(serviceIdentifier) + "@" + resolveSafeHostname() + "#" + UUID.randomUUID());
    }

    /**
     * Creates a {@link RunnerId} from a raw string.
     *
     * @param value the raw identifier value
     * @return a {@code RunnerId} wrapping the given value
     */
    public static RunnerId fromString(String value) {
        return new RunnerId(value);
    }

    private RunnerId(String value) {
        super(value);
    }

    @Override
    public String getKey() {
        return PROPERTY_KEY;
    }


    /**
     * Returns the local hostname in a safe, sanitized format.
     * If resolution fails or is blank, returns a new UUID instead.
     */
    private static String resolveSafeHostname() {
        try {
            return cleanOrUuid(Inet4Address.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            logger.warn("Unable to resolve hostname: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Returns the given input if non-blank, replacing any invalid characters.
     * If the input is blank or null, returns a new UUID instead.
     */
    private static String cleanOrUuid(String input) {
        return (input == null || input.trim().isEmpty())
                ? UUID.randomUUID().toString()
                : input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}