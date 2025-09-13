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
package io.flamingock.internal.common.core.task;

import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.RecoveryStrategy;

import java.util.Objects;

/**
 * Descriptor that holds recovery strategy configuration for a change unit or task.
 * This encapsulates the recovery strategy to allow for future extensions.
 */
public class RecoveryDescriptor {

    private static final RecoveryDescriptor DEFAULT_INSTANCE = new RecoveryDescriptor(RecoveryStrategy.MANUAL_INTERVENTION);

    private RecoveryStrategy strategy;

    public static RecoveryDescriptor getDefault() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Default constructor for Jackson deserialization.
     */
    public RecoveryDescriptor() {
    }

    /**
     * Creates a new recovery descriptor with the specified strategy.
     *
     * @param strategy the recovery strategy
     */
    public RecoveryDescriptor(RecoveryStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Factory method to create a RecoveryDescriptor from a strategy.
     * Returns a descriptor with MANUAL_INTERVENTION strategy if the strategy is null.
     *
     * @param strategy the recovery strategy
     * @return a new RecoveryDescriptor
     */
    public static RecoveryDescriptor fromStrategy(RecoveryStrategy strategy) {
        return new RecoveryDescriptor(strategy != null ? strategy : RecoveryStrategy.MANUAL_INTERVENTION);
    }


    /**
     * Gets the recovery strategy.
     *
     * @return the recovery strategy
     */
    public RecoveryStrategy getStrategy() {
        return strategy;
    }

    /**
     * Sets the recovery strategy.
     *
     * @param strategy the recovery strategy
     */
    public void setStrategy(RecoveryStrategy strategy) {
        this.strategy = strategy;
    }

    public boolean isAlwaysRetry() {
        return strategy == RecoveryStrategy.ALWAYS_RETRY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecoveryDescriptor that = (RecoveryDescriptor) o;
        return strategy == that.strategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(strategy);
    }

    @Override
    public String toString() {
        return "RecoveryDescriptor{" +
                "strategy=" + strategy +
                '}';
    }
}