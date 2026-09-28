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
package io.flamingock.store.dynamodb.internal;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.util.Result;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

/** Stages journal events in a caller-owned transaction; the caller confirms only after commit. */
public class DynamoDBJournalWriter {
    private final DynamoDBJournalEventStore store;

    public DynamoDBJournalWriter(DynamoDBJournalEventStore store) {
        this.store = store;
    }

    public Result write(TransactWriteItemsEnhancedRequest.Builder builder,
                        JournalEventSequencer sequencer, AuditEntry entry) {
        return store.contributeToTransaction(builder, sequencer.newEvent(entry));
    }
}
