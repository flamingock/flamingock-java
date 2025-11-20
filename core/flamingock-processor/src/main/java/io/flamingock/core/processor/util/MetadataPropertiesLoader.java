/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.processor.util;

import io.flamingock.internal.common.core.metadata.MetadataPropertiesProvider;
import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import javax.annotation.processing.RoundEnvironment;
public final class MetadataPropertiesLoader {
    public static Map<String, String> loadAllProperties(RoundEnvironment roundEnv, LoggerPreProcessor logger) {
        Map<String, String> result = new LinkedHashMap<>();

        ServiceLoader<MetadataPropertiesProvider> loader =
                ServiceLoader.load(MetadataPropertiesProvider.class, MetadataPropertiesLoader.class.getClassLoader());

        for (MetadataPropertiesProvider provider : loader) {
            Map<String, String> props = provider.getProperties(roundEnv, logger);
            if (props == null || props.isEmpty()) {
                continue;
            }

            for (Map.Entry<String, String> entry : props.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                String previous = result.put(key, value);
                if (previous != null && !Objects.equals(previous, value)) {
                    String message = String.format("%s overrides key '%s' "
                                    + "previous value='%s' new value='%s'",
                            provider.getClass().getName(),
                            key,
                            previous,
                            value);
                    logger.warn(message);
                }
            }
        }

        return result;
    }
}
