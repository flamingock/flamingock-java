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
package io.flamingock.targetsystem.mongodb.springdata;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class MongoDBSpringDataTransactionCapabilityTest {

    @Container
    static final GenericContainer<?> standaloneMongo = new GenericContainer<>(DockerImageName.parse("mongo:6"))
            .withExposedPorts(27017);

    @Test
    @DisplayName("Should support transactions by default")
    void transactionsAreSupportedByDefault() {
        assertTrue(new MongoDBSpringDataTargetSystem("mongodb", mock(MongoTemplate.class))
                .supportsTransactions());
    }

    @Test
    @DisplayName("Should skip Spring transaction infrastructure when transactions are disabled")
    void disabledTransactionsSkipInfrastructure() {
        MongoTemplate template = mock(MongoTemplate.class);
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.CLOUD));

        MongoDBSpringDataTargetSystem target = new MongoDBSpringDataTargetSystem("mongodb", template)
                .withTransactionsSupported(false);
        target.initialize(context);

        assertFalse(target.supportsTransactions());
        assertInstanceOf(NoOpTargetSystemAuditMarker.class, target.getAuditMarker());
        assertThrows(FlamingockException.class, target::getTxWrapper);
    }

    @Test
    @DisplayName("Should work against standalone MongoDB when transactions are disabled")
    void disabledTransactionsWorkAgainstStandaloneMongoDB() {
        try (MongoClient client = MongoClients.create(standaloneConnectionString())) {
            MongoTemplate template = new MongoTemplate(client, "standalone");
            ContextResolver context = mock(ContextResolver.class);
            when(context.getDependencyValue(FlamingockEdition.class))
                    .thenReturn(Optional.of(FlamingockEdition.CLOUD));
            MongoDBSpringDataTargetSystem target = new MongoDBSpringDataTargetSystem("mongodb", template)
                    .withTransactionsSupported(false);
            target.initialize(context);

            template.insert(new Document("status", "applied"), "changes");

            assertEquals(1L, template.getCollection("changes").countDocuments());
        }
    }

    private static String standaloneConnectionString() {
        return "mongodb://" + standaloneMongo.getHost() + ":" + standaloneMongo.getMappedPort(27017);
    }
}
