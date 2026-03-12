/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.api.template;

import java.util.Optional;

/**
 * Metadata that a {@link TemplatePayload} exposes to the framework so
 * centralized decisions can be made based on payload characteristics.
 *
 * <p><b>Binary-compatibility contract:</b> new fields are added as
 * getter/setter pairs.  A {@code null} value means "not specified" —
 * the payload makes no claim and the framework applies its own policy.
 * Older implementations that return a default-constructed instance
 * continue to work unchanged as new fields are introduced.
 *
 * <p>Current fields:
 * <ul>
 *   <li>{@code supportsTransactions} — whether the payload's target
 *       system supports transactional execution.  {@code null} (default)
 *       means the payload makes no claim.</li>
 * </ul>
 */
public class TemplatePayloadInfo {

    private Boolean supportsTransactions;

    /**
     * Creates an info instance with all fields set to {@code null}
     * (no claims made).
     */
    public TemplatePayloadInfo() {
    }

    /**
     * Creates an info instance with all fields
     */
    public TemplatePayloadInfo(boolean supportsTransactions) {
        this.supportsTransactions = supportsTransactions;
    }

    /**
     * Returns whether the payload's target system supports transactional
     * execution.
     *
     * @return an {@link Optional} containing {@code true} or {@code false}
     *         if the payload explicitly declares support; empty if the
     *         payload makes no claim
     */
    public Optional<Boolean> getSupportsTransactions() {
        return Optional.ofNullable(supportsTransactions);
    }

    /**
     * Sets whether the payload's target system supports transactional
     * execution.
     *
     * @param supportsTransactions {@code true} if transactions are supported,
     *                             {@code false} if not, or {@code null} to
     *                             make no claim
     */
    public void setSupportsTransactions(Boolean supportsTransactions) {
        this.supportsTransactions = supportsTransactions;
    }
}
