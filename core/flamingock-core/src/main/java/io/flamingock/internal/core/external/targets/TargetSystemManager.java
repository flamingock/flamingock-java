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
package io.flamingock.internal.core.external.targets;

import io.flamingock.api.external.ExternalSystem;
import io.flamingock.api.external.TargetSystem;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.context.ContextInitializable;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.core.external.targets.operations.TargetSystemOps;
import io.flamingock.internal.core.external.targets.operations.TargetSystemOpsImpl;
import io.flamingock.internal.core.external.targets.operations.TransactionalTargetSystemOpsImpl;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.slf4j.Logger;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    /**
     * Reserved id to real id. Deliberately an indirection rather than extra {@link #targetSystemMap}
     * entries: {@link #initialize} and {@link #getTransactionalTargetSystems} both iterate that map's
     * values, so a second entry pointing at the same instance would initialize it twice and count it twice.
     */
    private final Map<String, String> aliases = new HashMap<>();

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
    /**
     * Registers the audit store's own target system under the reserved
     * {@link PipelineHelper#AUDIT_STORE_TARGET_SYSTEM} id.
     * <p>
     * System changes that act on the audit store - compaction, journal backfill - have no target system of
     * their own to name, but every change is resolved against one. Declaring the reserved id lets them
     * reach the store's system without the user naming it.
     * <p>
     * Registers the <em>instance</em>, not a name to look up later. That matters: an audit store built with
     * {@code from(targetSystem)} does not require that target system to have been added to the builder, so
     * resolving by id alone could find nothing - or worse, find a different instance that happens to share
     * the id.
     * <p>
     * Must be called before {@link #initialize}: {@link #validateAndCast} rejects late registrations, and
     * registering earlier is also what gets an otherwise-unregistered system initialized along with the
     * rest.
     *
     * @param externalSystem the system the audit store was built from
     * @throws IllegalArgumentException if it is not an {@link AbstractTargetSystem}, has a blank id, or
     *                                  this manager is already initialized
     */
    public void registerAuditStoreTargetSystem(ExternalSystem externalSystem) {
        if (externalSystem == null) {
            // Checked here rather than left to validateAndCast, whose "Target system null not allowed"
            // says nothing about where the null came from. Every audit store must return a system - one
            // that keeps no data of its own returns a NonTransactionalTargetSystem stand-in.
            throw new IllegalArgumentException("The audit store returned no external system. "
                    + "AuditStore.getExternalSystem() must never return null; an audit store with no "
                    + "external system of its own should return a NonTransactionalTargetSystem instead.");
        }
        AbstractTargetSystem<?> targetSystem = validateAndCast(externalSystem);
        String id = targetSystem.getId();

        AbstractTargetSystem<?> alreadyRegistered = targetSystemMap.get(id);
        if (alreadyRegistered == null) {
            // The user never registered it, so register it here - otherwise it would neither resolve nor
            // be initialized.
            targetSystemMap.put(id, targetSystem);
        } else if (alreadyRegistered != targetSystem) {
            // Identity, not equality: the user's instance wins, because overwriting it would silently
            // change what their own changes run against. But the audit store then operates against a
            // different instance than it was built with, which is worth saying out loud.
            logger.warn("The audit store was built from a target system with id[{}], but a different "
                    + "instance is already registered under that id. The registered one will be used to "
                    + "resolve [{}]", id, PipelineHelper.AUDIT_STORE_TARGET_SYSTEM);
        }

        aliases.put(PipelineHelper.AUDIT_STORE_TARGET_SYSTEM, id);
        logger.debug("Registered [{}] as an alias of target system [{}]",
                PipelineHelper.AUDIT_STORE_TARGET_SYSTEM, id);
    }

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

        // A reserved alias resolves to the id it points at; anything else passes straight through.
        id = aliases.getOrDefault(id, id);

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
     * Returns all registered {@link TransactionalTargetSystem} instances.
     */
    public List<TransactionalTargetSystem<?>> getTransactionalTargetSystems() {
        return targetSystemMap.values().stream()
                .filter(ts -> ts instanceof TransactionalTargetSystem)
                .map(ts -> (TransactionalTargetSystem<?>) ts)
                .collect(Collectors.toList());
    }

    /**
     * Validates that the target system is non-null and has a valid ID.
     *
     * @param targetSystem the target system to validate
     * @throws IllegalArgumentException if validation fails
     */
    private AbstractTargetSystem<?> validateAndCast(ExternalSystem targetSystem) {
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
