package io.flamingock.cloud;

import io.flamingock.internal.core.builder.CloudFlamingockBuilder;
import io.flamingock.internal.core.builder.FlamingockFactory;

public class Flamingock {
    public static CloudFlamingockBuilder builder() {
        return FlamingockFactory.getCloudBuilder();
    }
}
