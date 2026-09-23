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
package io.flamingock.targetsystem.mongodb.reactive;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import io.flamingock.reactive.util.PublisherSync;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
class MongoDBReactiveTransactionCapabilityTest {

    @Container
    static final GenericContainer<?> standaloneMongo = new GenericContainer<>(DockerImageName.parse("mongo:6"))
            .withExposedPorts(27017);

    @Test
    @DisplayName("Should support transactions by default")
    void transactionsAreSupportedByDefault() {
        assertTrue(new MongoDBReactiveTargetSystem("mongodb", mock(MongoClient.class), "test")
                .supportsTransactions());
    }

    @Test
    @DisplayName("Should skip reactive transaction infrastructure when transactions are disabled")
    void disabledTransactionsSkipReactiveInfrastructure() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(client.getDatabase("test")).thenReturn(database);
        when(database.withReadConcern(ReadConcern.MAJORITY)).thenReturn(database);
        when(database.withReadPreference(ReadPreference.primary())).thenReturn(database);
        when(database.withWriteConcern(WriteConcern.MAJORITY.withJournal(true))).thenReturn(database);
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.CLOUD));

        MongoDBReactiveTargetSystem target = new MongoDBReactiveTargetSystem("mongodb", client, "test")
                .withTransactionsSupported(false);
        target.initialize(context);

        assertFalse(target.supportsTransactions());
        assertInstanceOf(NoOpTargetSystemAuditMarker.class, target.getAuditMarker());
        assertThrows(FlamingockException.class, target::getTxWrapper);
        assertThrows(FlamingockException.class, target::getTxManager);
        verify(client, never()).startSession();
    }

    @Test
    @DisplayName("Should work against standalone MongoDB when transactions are disabled")
    void disabledTransactionsWorkAgainstStandaloneMongoDB() {
        MongoClient client = MongoClients.create(standaloneConnectionString());
        try {
            MongoDBReactiveTargetSystem target = new MongoDBReactiveTargetSystem("mongodb", client, "standalone")
                    .withTransactionsSupported(false);
            ContextResolver context = mock(ContextResolver.class);
            when(context.getDependencyValue(FlamingockEdition.class))
                    .thenReturn(Optional.of(FlamingockEdition.CLOUD));
            target.initialize(context);

            PublisherSync.first(target.getMongoDatabase().getCollection("changes")
                    .insertOne(new Document("status", "applied")));

            assertEquals(1L, PublisherSync.first(
                    target.getMongoDatabase().getCollection("changes").countDocuments()));
        } finally {
            client.close();
        }
    }

    private static String standaloneConnectionString() {
        return "mongodb://" + standaloneMongo.getHost() + ":" + standaloneMongo.getMappedPort(27017);
    }
}
