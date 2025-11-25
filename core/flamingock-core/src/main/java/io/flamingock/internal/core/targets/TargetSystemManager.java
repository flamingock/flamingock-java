/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.internal.core.targets;

import io.flamingock.api.targets.TargetSystem;
import io.flamingock.internal.common.core.context.ContextInitializable;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.core.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.targets.operations.TargetSystemOpsImpl;
import io.flamingock.internal.core.targets.operations.TransactionalTargetSystemOpsImpl;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Internal registry for managing {@link TargetSystem} instances.
 * <p>
 * It supports registering multiple target systems by ID, including a default one,
 * and retrieving them with fallback logic.
 * <p>
 * Used internally by Flamingock to manage runtime access to registered targets.
 */
@NotThreadSafe
public class TargetSystemManager implements ContextInitializable {

    private static final Logger logger = FlamingockLoggerFactory.getLogger("TargetSystem");

    private boolean initialized = false;

    //TODO if we decide to remove the TX_SHARED
    private AbstractTargetSystem<?> auditStoreTargetSystem;
    private final Map<String, AbstractTargetSystem<?>> targetSystemMap = new HashMap<>();

    @Override
    public void initialize(ContextResolver contextResolver) {
        initialized = true;
        targetSystemMap.values()
                .stream()
                .filter(targetSystem -> targetSystem instanceof ContextInitializable)
                .map(targetSystem -> (ContextInitializable) targetSystem)
                .forEach(targetSystem -> targetSystem.initialize(contextResolver));
    }

    /**
     * Registers a new {@link TargetSystem}.
     *
     * @param targetSystem the target system to register (must be non-null and have a non-empty ID)
     * @throws IllegalArgumentException if the target system or its ID is null/blank
     */
    public void add(TargetSystem targetSystem) {
        targetSystemMap.put(targetSystem.getId(), validateAndCast(targetSystem));
    }


    /**
     * Returns the {@link TargetSystem} associated with the given descriptor.
     * 
     * @param tsd the target system descriptor
     * @return the target system operations
     * @throws FlamingockException if relaxed is false and target system is not found
     */
    public TargetSystemOps getTargetSystem(TargetSystemDescriptor tsd) {
        String targetSystemId = tsd != null ? tsd.getId() : null;
        return getTargetSystem(targetSystemId);
    }

    /**
     * Returns the {@link TargetSystem} associated with the given ID.
     * 
     * @param id the target system ID
     * @return the target system operations
     * @throws FlamingockException if relaxed is false and target system is not found
     */
    public TargetSystemOps getTargetSystem(String id) {
        logger.debug("Resolving target system with id: [{}]", id);
        
        if (id == null || !targetSystemMap.containsKey(id)) {
            String availableTargetSystems = String.join(", ", targetSystemMap.keySet());
            String message = String.format(
                    "Not found targetSystem [%s] among available target systems: [ %s ]",
                    id, availableTargetSystems
            );
            throw new FlamingockException(message);
        } else {
            AbstractTargetSystem<?> targetSystem = targetSystemMap.get(id);
            logger.debug("Successfully resolved target system with id: [{}]", id);
            return toDecorator(targetSystem);
        }
    }


    /**
     * Validates that the target system is non-null and has a valid ID.
     *
     * @param targetSystem the target system to validate
     * @throws IllegalArgumentException if validation fails
     */
    private AbstractTargetSystem<?> validateAndCast(TargetSystem targetSystem) {
        if (targetSystem == null) {
            throw new IllegalArgumentException("Target system null not allowed");
        }
        if (targetSystem.getId() == null || targetSystem.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("TargetSystem ID must not be null or blank");
        }
        if (!(targetSystem instanceof AbstractTargetSystem)) {
            throw new IllegalArgumentException("TargetSystem must be an instance of AbstractTargetSystem");
        }
        if (initialized) {
            String message = String.format("Target system with id[%s] cannot be added after TargetSystemManager is initialized", targetSystem.getId());
            throw new IllegalArgumentException(message);
        }
        return (AbstractTargetSystem<?>) targetSystem;
    }

    private TargetSystemOps toDecorator(AbstractTargetSystem<?> instance) {
        if (instance instanceof TransactionalTargetSystem) {
            return new TransactionalTargetSystemOpsImpl((TransactionalTargetSystem<?>) instance, auditStoreTargetSystem);
        } else {
            return new TargetSystemOpsImpl(instance);
        }
    }
}
