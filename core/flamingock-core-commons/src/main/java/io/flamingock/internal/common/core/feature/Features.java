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
package io.flamingock.internal.common.core.feature;

/**
 * Names of the features that can be toggled through {@code io.flamingock.internal.util.FeatureFlag}.
 * <p>
 * These are internal, undocumented switches for work that is not ready to ship, not user-facing
 * configuration. {@code FeatureFlag} keeps its state in a process-local in-memory map with no system-property
 * or environment lookup, and an unknown flag reads as disabled — so every feature named here is off in
 * production unless something calls {@code FeatureFlag.enable(...)}, which today only test code does.
 * <p>
 * Do not register this class with the GraalVM {@code RegistrationFeature}: it is never used reflectively, and
 * its {@code static final String} members are compile-time constants inlined into their call sites.
 */
public final class Features {

    /**
     * The local journal that mirrors change-state transitions as events, pending synchronization to
     * Flamingock Cloud (see {@code docs/ADR-0001}). When disabled, no journal event is written and the
     * journal collection/table is never created.
     * <p>
     * Not safe as an operational toggle across runs of a live deployment — only across whole environments.
     * Audit entries written while it is off produce no journal events, so the journal keeps handing out
     * contiguous {@code streamSequence} values but stops being a complete log of what changed.
     */
    public static final String JOURNAL_EVENTS = "journal-events";

    private Features() {
    }
}
