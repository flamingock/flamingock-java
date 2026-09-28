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
package io.flamingock.store.couchbase.internal;

import com.couchbase.client.java.transactions.TransactionAttemptContext;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.journal.JournalEvent;
import io.flamingock.internal.util.Result;

/** Stages an event in the caller's transaction; confirmation belongs to the transaction owner. */
public class CouchbaseJournalWriter {

    private final CouchbaseJournalEventStore store;

    public CouchbaseJournalWriter(CouchbaseJournalEventStore store) {
        this.store = store;
    }

    public Result write(TransactionAttemptContext context, JournalEvent<AuditEntry> event) {
        store.contributeToTransaction(context, event);
        return Result.OK();
    }
}
