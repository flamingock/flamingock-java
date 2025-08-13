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
package io.flamingock.internal.core.targets.operations;

/**
 * @deprecated Use {@link io.flamingock.internal.common.core.targets.operations.OperationType} instead.
 * This enum will be removed in a future version.
 */
@Deprecated
public enum OperationType {
    NON_TX,
    TX_AUDIT_STORE_SHARED,
    TX_AUDIT_STORE_SYNC,
    TX_NON_SYNC;

    /**
     * Convert to the commons equivalent.
     */
    public io.flamingock.internal.common.core.targets.operations.OperationType toCommons() {
        return io.flamingock.internal.common.core.targets.operations.OperationType.valueOf(this.name());
    }

    /**
     * Convert from the commons equivalent.
     */
    public static OperationType fromCommons(io.flamingock.internal.common.core.targets.operations.OperationType commons) {
        return OperationType.valueOf(commons.name());
    }
}
