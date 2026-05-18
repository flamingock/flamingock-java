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
package io.flamingock.internal.common.core.metadata;

import io.flamingock.internal.common.core.preview.CodePreviewChange;
import io.flamingock.internal.common.core.preview.PreviewPipeline;

import java.util.List;
import java.util.Map;

public class FlamingockMetadata {

    private PreviewPipeline pipeline;
    private String configFile;
    private Map<String, String> properties;
    private BuilderProviderInfo builderProvider;
    private List<CodePreviewChange> orphanChanges;
    private boolean strictStageMapping;

    public FlamingockMetadata() {
    }

    public FlamingockMetadata(PreviewPipeline pipeline, String configFile, Map<String, String> properties) {
        this.pipeline = pipeline;
        this.configFile = configFile;
        this.properties = properties;
    }

    public PreviewPipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(PreviewPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public String getPipelineFile() {
        return configFile;
    }

    public void setPipelineFile(String configFile) {
        this.configFile = configFile;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public BuilderProviderInfo getBuilderProvider() {
        return builderProvider;
    }

    public void setBuilderProvider(BuilderProviderInfo builderProvider) {
        this.builderProvider = builderProvider;
    }

    /**
     * Checks if a valid builder provider is configured.
     * @return true if builder provider exists and has both class and method names
     */
    public boolean hasValidBuilderProvider() {
        return builderProvider != null && builderProvider.isValid();
    }

    /**
     * Code-changes that have not yet been placed into any stage. Populated when an incremental
     * compilation round discovers a {@code @Change} whose package isn't covered by any stage in
     * the cached pipeline. They are rehomed by the merger when {@code @EnableFlamingock} is
     * processed in a subsequent round and a stage now covers their package.
     */
    public List<CodePreviewChange> getOrphanChanges() {
        return orphanChanges;
    }

    public void setOrphanChanges(List<CodePreviewChange> orphanChanges) {
        this.orphanChanges = orphanChanges;
    }

    /**
     * Whether {@code @EnableFlamingock.strictStageMapping} was true when the metadata was last
     * generated. Persisted so the runtime can fail when orphans remain.
     */
    public boolean isStrictStageMapping() {
        return strictStageMapping;
    }

    public void setStrictStageMapping(boolean strictStageMapping) {
        this.strictStageMapping = strictStageMapping;
    }

    @Override
    public String toString() {
        int orphans = orphanChanges == null ? 0 : orphanChanges.size();
        return "FlamingockMetadata{" + "pipeline=" + pipeline +
                ", configFile='" + configFile + '\'' +
                ", builderProvider=" + builderProvider +
                ", orphanChanges=" + orphans +
                ", strictStageMapping=" + strictStageMapping +
                '}';
    }
}
