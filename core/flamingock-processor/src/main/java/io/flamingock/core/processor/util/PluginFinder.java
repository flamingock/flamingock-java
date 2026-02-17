/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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

import io.flamingock.internal.common.core.processor.AnnotationProcessorPlugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

public final class PluginFinder {

    private PluginFinder() {
        // No-op.
    }

    public static List<AnnotationProcessorPlugin> findAnnotationProcessorPlugins() {
        Set<String> seen = new LinkedHashSet<>();
        List<AnnotationProcessorPlugin> result = new ArrayList<>();

        ClassLoader[] loaders = new ClassLoader[] {
                Thread.currentThread().getContextClassLoader(),
                AnnotationProcessorPlugin.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };

        for (ClassLoader cl : loaders) {
            if (cl == null) continue;

            ServiceLoader<AnnotationProcessorPlugin> sl =
                    ServiceLoader.load(AnnotationProcessorPlugin.class, cl);

            for (AnnotationProcessorPlugin plugin : sl) {
                if (seen.add(plugin.getClass().getName())) {
                    result.add(plugin);
                }
            }
        }
        return result;
    }
}
