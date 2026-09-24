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
package io.flamingock.internal.common.couchbase.journal;

/**
 * Default persistence name for the local event buffer, mirroring the (external, un-extendable)
 * {@code CommunityPersistenceConstants} defaults used for the audit and lock collections.
 */
public final class JournalEventPersistenceConstants {

    public static final String DEFAULT_JOURNAL_STORE_NAME = "flamingockJournalEvents";

    private JournalEventPersistenceConstants() {
    }
}
