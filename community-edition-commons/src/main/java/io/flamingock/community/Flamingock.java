package io.flamingock.community;

import io.flamingock.internal.core.builder.CommunityFlamingockBuilder;
import io.flamingock.internal.core.builder.FlamingockFactory;

public class Flamingock {
    public static CommunityFlamingockBuilder builder() {
        return FlamingockFactory.getCommunityBuilder();
    }
}
