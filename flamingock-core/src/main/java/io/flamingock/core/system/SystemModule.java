package io.flamingock.core.system;


import io.flamingock.core.context.ContextContributor;
import io.flamingock.core.context.ContextInitializable;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.context.Dependency;

import java.util.List;

public interface SystemModule extends Comparable<SystemModule>, ContextInitializable, ContextContributor {

    PreviewStage getStage();

    /**
     * Modules order
     */
    int getOrder();

    /**
     * @return the dependencies built by the module that are not in the application context
     */
    List<Dependency> getDependencies();

    /**
     * Indicates if this should
     */
    boolean isBeforeUserStages();

    @Override
    default int compareTo(SystemModule o) {
        return Integer.compare(this.getOrder(), o.getOrder());
    }


}
