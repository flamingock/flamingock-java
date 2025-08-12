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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger logger = LoggerFactory.getLogger(TargetSystemManager.class);

    private boolean initialized = false;
    private AbstractTargetSystem<?> auditStoreTargetSystem;
    private final Map<String, AbstractTargetSystem<?>> targetSystemMap = new HashMap<>();

    @Override
    public void initialize(ContextResolver contextResolver) {
        if (auditStoreTargetSystem == null) {
            throw new IllegalArgumentException("Trying to initialize TargetSystemManager without AuditStore TargetSystem");
        }
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
     * Registers the default {@link TargetSystem} to be returned when a specific ID is not found.
     * <p>
     * Also adds it to the general registry.
     *
     * @param defaultTargetSystem the default target system
     * @throws IllegalArgumentException if the target system or its ID is null/blank
     */
    public void setAuditStoreTargetSystem(TargetSystem defaultTargetSystem) {
        add(defaultTargetSystem);
        this.auditStoreTargetSystem = (AbstractTargetSystem<?>) defaultTargetSystem;
    }


    public Optional<TargetSystemOps> getOrDefault(TargetSystemDescriptor tsd) {
        return Optional.ofNullable(getValueOrDefault(tsd));
    }

    public TargetSystemOps getValueOrDefault(TargetSystemDescriptor tsd) {
        return getValueOrDefault(tsd != null ? tsd.getId() : null);
    }

    /**
     * Returns the {@link TargetSystem} associated with the given ID,
     * or the default one if no match is found.
     *
     * @param id the target system ID
     * @return an {@link Optional} with the matching or default target system, or empty if none registered
     */
    public Optional<TargetSystemOps> getOrDefault(String id) {
        return Optional.ofNullable(getValueOrDefault(id));
    }

    public TargetSystemOps getValueOrDefault(String id) {
        //We do it this way(instead of getOrDefault) because although current implementation(HashMap) allows
        // nulls, we may change in the future(ConcurrentHashMap doesn't allow nulls, for instance)
        if (id == null || !targetSystemMap.containsKey(id)) {
            return toDecorator(auditStoreTargetSystem);
        } else {
            AbstractTargetSystem<?> targetSystem = targetSystemMap.getOrDefault(id, auditStoreTargetSystem);
            return toDecorator(targetSystem);
        }
    }

    /**
     * Returns the {@link TargetSystem} associated with the given descriptor.
     * 
     * @param tsd the target system descriptor
     * @param relaxed if true, falls back to audit store target system when not found; if false, throws exception
     * @return the target system operations
     * @throws FlamingockException if relaxed is false and target system is not found
     */
    public TargetSystemOps getTargetSystem(TargetSystemDescriptor tsd, boolean relaxed) {
        String targetSystemId = tsd != null ? tsd.getId() : null;
        return getTargetSystem(targetSystemId, relaxed);
    }

    /**
     * Returns the {@link TargetSystem} associated with the given ID.
     * 
     * @param id the target system ID
     * @param relaxed if true, falls back to audit store target system when not found; if false, throws exception
     * @return the target system operations
     * @throws FlamingockException if relaxed is false and target system is not found
     */
    public TargetSystemOps getTargetSystem(String id, boolean relaxed) {
        logger.debug("Resolving target system with id: [{}], relaxed: [{}]", id, relaxed);
        
        if (id == null || !targetSystemMap.containsKey(id)) {
            if (relaxed) {
                logger.warn("Target system with id [{}] not found, falling back to audit store target system [{}]", 
                           id, auditStoreTargetSystem.getId());
                return toDecorator(auditStoreTargetSystem);
            } else {
                String availableTargetSystems = String.join(", ", targetSystemMap.keySet());
                String message = String.format(
                    "ChangeUnit requires a valid targetSystem. Found: [%s]. Available target systems: [%s]",
                    id, availableTargetSystems
                );
                logger.error(message);
                throw new FlamingockException(message);
            }
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
            TransactionalTargetSystem<?> txInstance = (TransactionalTargetSystem<?>) instance;
            boolean isAuditStoreTransactionResource = auditStoreTargetSystem instanceof TransactionalTargetSystem
                    && ((TransactionalTargetSystem<?>) auditStoreTargetSystem).isSameTxResourceAs(txInstance);

            return new TransactionalTargetSystemOpsImpl(txInstance, isAuditStoreTransactionResource);
        } else {
            return new TargetSystemOpsImpl(instance);
        }
    }
}
