package io.flamingock.common.test.pipeline;

import io.flamingock.core.preview.AbstractPreviewTask;
import io.flamingock.core.preview.TemplatePreviewChangeUnit;

import java.util.Collections;
import java.util.Map;

public class TemplateChangeUnitTestDefinition extends ChangeUnitTestDefinition {


    private final String templateName;
    private final Map<String, Object> templateConfiguration;


    public TemplateChangeUnitTestDefinition(String id,
                                            String order,
                                            String templateName,
                                            boolean transactional,
                                            Map<String, Object> templateConfiguration) {
        super(id, order, transactional);
        this.templateName = templateName;
        this.templateConfiguration = templateConfiguration;
    }


    @Override
    public AbstractPreviewTask toPreview() {
        return new TemplatePreviewChangeUnit(
                getId(),
                getOrder(),
                templateName,
                Collections.emptyList(),
                isTransactional(),
                false,
                false,
                templateConfiguration
        );
    }

}
