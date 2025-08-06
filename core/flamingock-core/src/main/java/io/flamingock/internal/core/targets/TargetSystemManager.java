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
public class TargetSystemManager {

    private ContextDecoratorTargetSystem defaultTargetSystem;
    private final Map<String, ContextDecoratorTargetSystem> targetSystemMap = new HashMap<>();

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
    public void addDefault(TargetSystem defaultTargetSystem) {
        add(defaultTargetSystem);
        this.defaultTargetSystem = (ContextDecoratorTargetSystem) defaultTargetSystem;
    }

    /**
     * Returns the {@link TargetSystem} associated with the given ID,
     * or the default one if no match is found.
     *
     * @param id the target system ID
     * @return an {@link Optional} with the matching or default target system, or empty if none registered
     */
    public Optional<ContextDecoratorTargetSystem> getOrDefault(String id) {
        return Optional.ofNullable(getValueOrDefault(id));
    }

    public ContextDecoratorTargetSystem getValueOrDefault(String id) {
        //We do it this way(instead of getOrDefault) because although current implementation(HashMap) allows
        // nulls, we may change in the future(ConcurrentHashMap doesn't allow nulls, for instance)
        if(id == null || !targetSystemMap.containsKey(id)) {
            return defaultTargetSystem;
        } else {
            return targetSystemMap.getOrDefault(id, defaultTargetSystem);
        }

    }

    /**
     * Validates that the target system is non-null and has a valid ID.
     *
     * @param targetSystem the target system to validate
     * @throws IllegalArgumentException if validation fails
     */
    private ContextDecoratorTargetSystem validateAndCast(TargetSystem targetSystem) {
        if (targetSystem == null) {
            throw new IllegalArgumentException("Target system null not allowed");
        }
        if (targetSystem.getId() == null || targetSystem.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("TargetSystem ID must not be null or blank");
        }
        if (!(targetSystem instanceof ContextDecoratorTargetSystem)) {
            throw new IllegalArgumentException("TargetSystem must be an instance of ContextComposerTargetSystem");
        }
        return (ContextDecoratorTargetSystem) targetSystem;
    }
}
