package io.flamingock.common.test.pipeline;

import io.flamingock.internal.common.core.preview.AbstractPreviewTask;

public abstract class ChangeUnitTestDefinition {


    private final String id;
    private final String order;
    private final boolean transactional;


    public ChangeUnitTestDefinition(String id,
                                    String order,
                                    boolean transactional) {
        this.id = id;
        this.order = order;
        this.transactional = transactional;
    }

    public String getId() {
        return id;
    }

    public String getOrder() {
        return order;
    }

    public boolean isTransactional() {
        return transactional;
    }

    public abstract AbstractPreviewTask toPreview();
}
