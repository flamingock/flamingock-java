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
