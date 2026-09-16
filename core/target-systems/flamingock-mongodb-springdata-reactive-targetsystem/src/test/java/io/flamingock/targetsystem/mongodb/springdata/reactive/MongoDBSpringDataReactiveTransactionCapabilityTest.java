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
package io.flamingock.targetsystem.mongodb.springdata.reactive;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.builder.FlamingockEdition;
import io.flamingock.internal.core.external.targets.mark.NoOpTargetSystemAuditMarker;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
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
import static org.mockito.Mockito.when;

@Testcontainers
class MongoDBSpringDataReactiveTransactionCapabilityTest {

    @Container
    static final GenericContainer<?> standaloneMongo = new GenericContainer<>(DockerImageName.parse("mongo:6"))
            .withExposedPorts(27017);

    @Test
    @DisplayName("Should support transactions by default")
    void transactionsAreSupportedByDefault() {
        assertTrue(new MongoDBSpringDataReactiveTargetSystem("mongodb", mock(ReactiveMongoTemplate.class))
                .supportsTransactions());
    }

    @Test
    @DisplayName("Should skip reactive Spring transaction infrastructure when transactions are disabled")
    void disabledTransactionsSkipReactiveInfrastructure() {
        ReactiveMongoTemplate template = mock(ReactiveMongoTemplate.class);
        ContextResolver context = mock(ContextResolver.class);
        when(context.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.CLOUD));

        MongoDBSpringDataReactiveTargetSystem target =
                new MongoDBSpringDataReactiveTargetSystem("mongodb", template)
                        .withTransactionsSupported(false);
        target.initialize(context);

        assertFalse(target.supportsTransactions());
        assertInstanceOf(NoOpTargetSystemAuditMarker.class, target.getAuditMarker());
        assertThrows(FlamingockException.class, target::getTxWrapper);
    }

    @Test
    @DisplayName("Should work against standalone MongoDB when transactions are disabled")
    void disabledTransactionsWorkAgainstStandaloneMongoDB() {
        MongoClient client = MongoClients.create(standaloneConnectionString());
        try {
            ReactiveMongoTemplate template = new ReactiveMongoTemplate(client, "standalone");
            ContextResolver context = mock(ContextResolver.class);
            when(context.getDependencyValue(FlamingockEdition.class))
                    .thenReturn(Optional.of(FlamingockEdition.CLOUD));
            MongoDBSpringDataReactiveTargetSystem target =
                    new MongoDBSpringDataReactiveTargetSystem("mongodb", template)
                            .withTransactionsSupported(false);
            target.initialize(context);

            template.insert(new Document("status", "applied"), "changes").block();

            assertEquals(1L, template.getCollection("changes")
                    .flatMapMany(collection -> collection.countDocuments())
                    .blockFirst());
        } finally {
            client.close();
        }
    }

    private static String standaloneConnectionString() {
        return "mongodb://" + standaloneMongo.getHost() + ":" + standaloneMongo.getMappedPort(27017);
    }
}
