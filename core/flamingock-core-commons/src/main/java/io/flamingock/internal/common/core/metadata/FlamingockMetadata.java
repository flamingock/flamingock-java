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

import io.flamingock.internal.common.core.preview.PreviewPipeline;

public class FlamingockMetadata {

    private PreviewPipeline pipeline;
    private String configFile;
    private BuilderProviderInfo builderProvider;

    public FlamingockMetadata() {
    }

    public FlamingockMetadata(PreviewPipeline pipeline, String configFile) {
        this.pipeline = pipeline;
        this.configFile = configFile;
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

    @Override
    public String toString() {
        return "FlamingockMetadata{" + "pipeline=" + pipeline +
                ", configFile='" + configFile + '\'' +
                ", builderProvider=" + builderProvider +
                '}';
    }
}
