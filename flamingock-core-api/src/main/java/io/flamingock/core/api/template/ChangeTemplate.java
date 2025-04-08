package io.flamingock.core.api.template;

import io.flamingock.core.api.metadata.ReflectionMetadataProvider;

public interface ChangeTemplate extends ReflectionMetadataProvider {

    @Override
    default boolean shouldRegisterSelf() {
        return true;
    }
}
