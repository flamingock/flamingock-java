package io.flamingock.internal.common.core.preview;

import java.util.Collection;

//TODO Add validation
public class PreviewPipeline {

    private Collection<PreviewStage> stages;

    public PreviewPipeline() {
    }

    public PreviewPipeline(Collection<PreviewStage> stages) {
        this.stages = stages;
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
