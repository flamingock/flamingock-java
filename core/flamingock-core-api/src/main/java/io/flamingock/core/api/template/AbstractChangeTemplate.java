package io.flamingock.core.api.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


public abstract class AbstractChangeTemplate<CONFIG extends ChangeTemplateConfig<?, ?, ?>> implements ChangeTemplate<CONFIG> {

    private final Logger logger = LoggerFactory.getLogger("AbstractChangeTemplate");


    private final Class<CONFIG> configClass;

    protected String changeId;
    protected boolean isTransactional;
    protected CONFIG configuration;

    private final Set<Class<?>> reflectiveClasses;


    public AbstractChangeTemplate(Class<CONFIG> configClass, Class<?>... additionalReflectiveClass) {
        if (configClass == null || additionalReflectiveClass == null) {
            throw new IllegalArgumentException("additionalReflectiveClass must not be null");
        }
        this.configClass = configClass;
        reflectiveClasses = new HashSet<>(Arrays.asList(additionalReflectiveClass));
        reflectiveClasses.add( this.configClass);

    }

    @Override
    public final Collection<Class<?>> getReflectiveClasses() {
        return reflectiveClasses;
    }

    @Override
    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }
    @Override
    public void setConfiguration(CONFIG configuration) {
        logger.trace("setting {} config[{}]: {}", getClass(), getConfigClass(), configuration);
        this.configuration = configuration;
    }

    @Override
    public Class<CONFIG> getConfigClass() {
        return configClass;
    }

    @Override
    public void setTransactional(boolean isTransactional) {
        this.isTransactional = isTransactional;
    }
}
