package io.flamingock.internal.common.core.metadata;

import io.flamingock.internal.common.core.preview.PreviewPipeline;

public class FlamingockMetadata {
    
    private PreviewPipeline pipeline;
    private String setup;
    private String pipelineFile;
    
    public FlamingockMetadata() {
    }
    
    public FlamingockMetadata(PreviewPipeline pipeline, String setup, String pipelineFile) {
        this.pipeline = pipeline;
        this.setup = setup;
        this.pipelineFile = pipelineFile;
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
        return pipelineFile;
    }
    
    public void setPipelineFile(String pipelineFile) {
        this.pipelineFile = pipelineFile;
    }

    @Override
    public String toString() {
        return "FlamingockMetadata{" + "pipeline=" + pipeline +
                ", setup='" + setup + '\'' +
                ", pipelineFile='" + pipelineFile + '\'' +
                '}';
    }
}