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

import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.internal.common.core.metadata.BuilderProviderInfo;
import io.flamingock.internal.common.core.preview.CodePreviewChange;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Snapshot of everything the annotation processor discovered in a single compilation round —
 * the canonical input for every processing phase. Carries no behaviour; just a typed bag.
 *
 * <p>Stores nullable fields directly (per Brian Goetz's guidance against {@link Optional} as
 * fields/parameters); getters expose the optional ones via {@code Optional} for ergonomic
 * call-site chaining.
 */
public final class RoundInputs {

    private final EnableFlamingock enableAnnotation;
    private final Collection<CodePreviewChange> roundChanges;
    private final BuilderProviderInfo builderProvider;
    private final Map<String, String> pluginProperties;

    public RoundInputs(EnableFlamingock enableAnnotation,
                       Collection<CodePreviewChange> roundChanges,
                       BuilderProviderInfo builderProvider,
                       Map<String, String> pluginProperties) {
        this.enableAnnotation = enableAnnotation;
        this.roundChanges = roundChanges;
        this.builderProvider = builderProvider;
        this.pluginProperties = pluginProperties;
    }

    public Optional<EnableFlamingock> getEnableAnnotation() {
        return Optional.ofNullable(enableAnnotation);
    }

    public Collection<CodePreviewChange> getRoundChanges() {
        return roundChanges;
    }

    public Optional<BuilderProviderInfo> getBuilderProvider() {
        return Optional.ofNullable(builderProvider);
    }

    public Map<String, String> getPluginProperties() {
        return pluginProperties;
    }

    /** True when the round has no Flamingock-relevant inputs at all. */
    public boolean isEmpty() {
        return enableAnnotation == null
                && roundChanges.isEmpty()
                && builderProvider == null
                && pluginProperties.isEmpty();
    }
}
