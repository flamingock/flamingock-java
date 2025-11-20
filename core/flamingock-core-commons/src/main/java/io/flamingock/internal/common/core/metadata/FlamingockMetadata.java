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

import java.util.HashMap;
import java.util.Map;

public class FlamingockMetadata {
    
    private PreviewPipeline pipeline;
    private String setup;
    private String configFile;
    private Map<String, String> properties;
    
    public FlamingockMetadata() {
    }
    
    public FlamingockMetadata(PreviewPipeline pipeline, String setup, String configFile, Map<String, String> properties) {
        this.pipeline = pipeline;
        this.setup = setup;
        this.configFile = configFile;
        this.properties = properties != null ? properties : new HashMap<>();
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

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        return "FlamingockMetadata{" + "pipeline=" + pipeline +
                ", setup='" + setup + '\'' +
                ", configFile='" + configFile + '\'' +
                ", properties=" + properties +
                '}';
    }
}