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
package io.flamingock.internal.common.core.journal;

import io.flamingock.internal.common.core.error.FlamingockException;

/**
 * Signals that a journal event could not be appended because its
 * {@code (streamId, streamSequence)} position was already taken.
 * <p>
 * Every audit store already rejects a duplicate position — MongoDB through a unique index on
 * {@code (streamId, streamSequence)} plus an insert that is never an upsert, SQL through
 * {@code PRIMARY KEY (stream_id, stream_sequence)} and a plain {@code INSERT}, Couchbase because the
 * document key <em>is</em> the position and it inserts rather than upserts, and DynamoDB because the pair
 * is the table's primary key, guarded by a condition expression. What they do not share is <em>how</em>
 * they say so: {@code DuplicateKeyException}, a {@code SQLException}, {@code DocumentExistsException}, or
 * a {@code ConditionalCheckFailedException} buried inside a transaction cancellation.
 * <p>
 * This is the common type they translate to, so a caller can tell "that position is taken" apart from
 * "the write failed for some other reason" without knowing which backend it is talking to. That
 * distinction is what makes a portable retry possible: retrying a genuine infrastructure failure would
 * be wrong, and retrying nothing at all would leave a recoverable collision as a hard failure.
 * <p>
 * Each store is responsible for translating only a positively identified journal position collision.
 * MongoDB sync performs this translation at the journal insert boundary.
 *
 * <h2>Why it carries the position</h2>
 * A retry needs to know which position was rejected, and an operator reading a log needs both halves:
 * {@code streamSequence} on its own identifies nothing, since every stream has its own sequence 1.
 */
public class DuplicateStreamSequenceException extends FlamingockException {

    private final String streamId;
    private final long streamSequence;

    /**
     * @param streamId       the stream whose position was taken
     * @param streamSequence the position within that stream
     * @param cause          the backend's own exception. Worth keeping rather than swallowing: without it,
     *                       a position collision and a database rejecting the write for an unrelated
     *                       reason are indistinguishable after the fact
     */
    public DuplicateStreamSequenceException(String streamId, long streamSequence, Throwable cause) {
        super(message(streamId, streamSequence), cause);
        this.streamId = streamId;
        this.streamSequence = streamSequence;
    }

    /**
     * For the case where a store detects the duplicate without an exception to wrap — for instance from an
     * affected-row count, or a conditional write that reports failure as a value.
     *
     * @param streamId       the stream whose position was taken
     * @param streamSequence the position within that stream
     */
    public DuplicateStreamSequenceException(String streamId, long streamSequence) {
        super(message(streamId, streamSequence));
        this.streamId = streamId;
        this.streamSequence = streamSequence;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getStreamSequence() {
        return streamSequence;
    }

    private static String message(String streamId, long streamSequence) {
        return "Journal event position already taken [streamId=" + streamId
                + " streamSequence=" + streamSequence + "]";
    }
}
