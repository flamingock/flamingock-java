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

import io.flamingock.internal.util.dynamodb.entities.AuditEntryEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which attributes of {@code AuditEntryEntity} DynamoDB actually stores.
 * <p>
 * Added alongside audit compaction, which rewrites stored records and therefore has to be able to state
 * precisely what a record carries. Writing that down turned up something unrelated but worth locking:
 * {@code errorTrace} and {@code metadata} are <b>not persisted at all</b>. Their getters return
 * {@code String} while their setters take {@code Object}, so {@code java.beans.Introspector} does not pair
 * them, {@code BeanTableSchema} sees read-only properties and drops them. Nothing has noticed because every
 * test and factory leaves both null.
 * <p>
 * This test is therefore two things: documentation of a real gap, and a tripwire. If someone fixes those
 * setters, this fails — and at that point compaction's preservation guarantees need re-checking, because two
 * more attributes will start round-tripping through the store.
 */
class AuditEntryEntitySchemaTest {

    private static final List<String> EXPECTED_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(
            "author", "changeId", "changeOrder", "createdAt", "executionHostname", "executionId",
            "executionMillis", "invokedClass", "invokedMethod", "partitionKey", "recoveryStrategy",
            "sourceFile", "stageId", "state", "systemChange", "targetSystemId", "transactionFlag",
            "txStrategy", "type"));

    @Test
    @DisplayName("the stored attribute set is exactly the documented one")
    void storedAttributesAreExactlyTheDocumentedSet() {
        TreeSet<String> actual = new TreeSet<>(TableSchema.fromBean(AuditEntryEntity.class).attributeNames());

        assertEquals(new TreeSet<>(EXPECTED_ATTRIBUTES), actual,
                "the DynamoDB audit record shape changed; re-check what compaction claims to preserve");
    }

    @Test
    @DisplayName("errorTrace and metadata are silently not persisted")
    void errorTraceAndMetadataAreNotPersisted() {
        TreeSet<String> attributes = new TreeSet<>(TableSchema.fromBean(AuditEntryEntity.class).attributeNames());

        // Not an endorsement — a record of a pre-existing defect, so that compaction's javadoc can be honest
        // about it and so that fixing it is a deliberate, visible act rather than a silent behaviour change.
        assertTrue(attributes.contains("errorTrace") == attributes.contains("metadata"),
                "errorTrace and metadata share one root cause and should be fixed together");
        assertEquals(false, attributes.contains("errorTrace"),
                "errorTrace now persists: fix the getter/setter type mismatch deliberately and revisit"
                        + " DynamoDBAuditCompactor's preservation guarantees");
    }
}
