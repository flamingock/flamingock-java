package io.flamingock.core.task.descriptor.change;

import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.legacy.MongockLegacyIdGenerator;
import io.flamingock.core.utils.ExecutionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeUnitUtil {
    private static final Logger logger = LoggerFactory.getLogger(ChangeUnitDescriptor.class);


    static ChangeUnitDescriptor getChangeUnitDescriptor(Class<?> source) {
        if (ExecutionUtils.isNewChangeUnit(source)) {
            ChangeUnit changeUnitAnnotation = source.getAnnotation(ChangeUnit.class);
            return new ChangeUnitDescriptor(
                    changeUnitAnnotation.id(),
                    changeUnitAnnotation.order(),
                    source,
                    changeUnitAnnotation.runAlways(),
                    changeUnitAnnotation.transactional(),
                    true);
        } else if (ExecutionUtils.isLegacyChangeUnit(source)) {
            logger.warn("Detected legacy changeUnit[{}]. If it's an old changeUnit created for Mongock, it's fine. " +
                            "Otherwise, it's highly recommended us the new API[in package {}]",
                    source.getName(),
                    "io.flamingock.core.api.annotations");
            io.mongock.api.annotations.ChangeUnit changeUnitAnnotation = source.getAnnotation(io.mongock.api.annotations.ChangeUnit.class);
            return new ChangeUnitDescriptor(
                    MongockLegacyIdGenerator.getNewId(changeUnitAnnotation.id(), changeUnitAnnotation.author()),
                    changeUnitAnnotation.order(),
                    source,
                    changeUnitAnnotation.runAlways(),
                    changeUnitAnnotation.transactional(),
                    false);
        } else {
            throw new IllegalArgumentException(String.format(
                    "Task class[%s] should be annotate with %s",
                    source.getName(),
                    ChangeUnit.class.getName()
            ));
        }
    }
}
