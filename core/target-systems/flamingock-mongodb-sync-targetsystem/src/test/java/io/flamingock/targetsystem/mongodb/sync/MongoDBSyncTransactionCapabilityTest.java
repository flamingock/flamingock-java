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
package io.flamingock.targetsystem.mongodb.sync;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.bson.Document;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class MongoDBSyncTransactionCapabilityTest {

    @Container
    static final GenericContainer<?> standaloneMongo = new GenericContainer<>(DockerImageName.parse("mongo:6"))
            .withExposedPorts(27017);

    @Test
    @DisplayName("Should support transactions by default")
    void transactionsAreSupportedByDefault() {
        assertTrue(new MongoDBSyncTargetSystem("mongodb", mock(MongoClient.class), "test")
                .supportsTransactions());
    }

    @Test
    @DisplayName("Should skip transaction infrastructure when transactions are disabled")
    void disabledTransactionsSkipInfrastructure() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(client.getDatabase("test")).thenReturn(database);
        when(database.withReadConcern(ReadConcern.MAJORITY)).thenReturn(database);
        when(database.withReadPreference(ReadPreference.primary())).thenReturn(database);
        when(database.withWriteConcern(WriteConcern.MAJORITY.withJournal(true))).thenReturn(database);
        ContextResolver context = cloudContext();

        MongoDBSyncTargetSystem target = new MongoDBSyncTargetSystem("mongodb", client, "test")
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
        try (MongoClient client = MongoClients.create(standaloneConnectionString())) {
            MongoDBSyncTargetSystem target = new MongoDBSyncTargetSystem("mongodb", client, "standalone")
                    .withTransactionsSupported(false);
            target.initialize(cloudContext());

            target.getMongoDatabase().getCollection("changes")
                    .insertOne(new Document("status", "applied"));

            assertEquals(1L, target.getMongoDatabase().getCollection("changes").countDocuments());
        }
    }

    private static String standaloneConnectionString() {
        return "mongodb://" + standaloneMongo.getHost() + ":" + standaloneMongo.getMappedPort(27017);
    }

    private static ContextResolver cloudContext() {
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.CLOUD));
        return context;
    }
}
