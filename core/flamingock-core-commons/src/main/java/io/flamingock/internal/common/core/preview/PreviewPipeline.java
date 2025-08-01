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
package io.flamingock.internal.common.core.preview;

import java.util.Collection;

//TODO Add validation
public class PreviewPipeline {

    private SystemPreviewStage systemStage;
    private Collection<PreviewStage> stages;

    public PreviewPipeline() {
        this(null, null);
    }

    public PreviewPipeline(Collection<PreviewStage> stages) {
        this(null, stages);
    }

    public PreviewPipeline(SystemPreviewStage systemStage, Collection<PreviewStage> stages) {
        this.systemStage = systemStage;
        this.stages = stages;
    }

    public PreviewStage getSystemStage() {
        return systemStage;
    }

    public void setSystemStage(SystemPreviewStage systemStage) {
        this.systemStage = systemStage;
    }

    public Collection<PreviewStage> getStages() {
        return stages;
    }

    /**
     * Necessary to be deserialized
     * @param stages pipeline's stages
     */
    public void setStages(Collection<PreviewStage> stages) {
        this.stages = stages;
    }

    @Override
    public String toString() {
        return "PreviewPipeline{" + "stages=" + stages + "}";
    }
}
