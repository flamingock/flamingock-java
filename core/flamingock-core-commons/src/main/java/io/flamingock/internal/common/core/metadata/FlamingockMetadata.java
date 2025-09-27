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
    private String setup;
    private String configFile;
    
    public FlamingockMetadata() {
    }
    
    public FlamingockMetadata(PreviewPipeline pipeline, String setup, String configFile) {
        this.pipeline = pipeline;
        this.setup = setup;
        this.configFile = configFile;
    }
    
    public PreviewPipeline getPipeline() {
        return pipeline;
    }
    
    public void setPipeline(PreviewPipeline pipeline) {
        this.pipeline = pipeline;
    }
    
    public String getSetup() {
        return setup;
    }
    
    public void setSetup(String setup) {
        this.setup = setup;
    }
    
    public String getPipelineFile() {
        return configFile;
    }
    
    public void setPipelineFile(String configFile) {
        this.configFile = configFile;
    }

    @Override
    public String toString() {
        return "FlamingockMetadata{" + "pipeline=" + pipeline +
                ", setup='" + setup + '\'' +
                ", configFile='" + configFile + '\'' +
                '}';
    }
}