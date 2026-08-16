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
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoDBReactiveTargetSystemTest {

    @Test
    void initializesReactiveDependencies() {
        MongoClient client = mock(MongoClient.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(client.getDatabase("test")).thenReturn(database);
        when(database.withReadConcern(ReadConcern.MAJORITY)).thenReturn(database);
        when(database.withReadPreference(ReadPreference.primary())).thenReturn(database);
        when(database.withWriteConcern(WriteConcern.MAJORITY.withJournal(true))).thenReturn(database);

        ContextResolver baseContext = mock(ContextResolver.class);
        when(baseContext.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.COMMUNITY));

        MongoDBReactiveTargetSystem targetSystem =
                new MongoDBReactiveTargetSystem("mongodb", client, "test");
        targetSystem.initialize(baseContext);

        assertSame(client, targetSystem.getContext().getRequiredDependencyValue(MongoClient.class));
        assertSame(database, targetSystem.getContext().getRequiredDependencyValue(MongoDatabase.class));
        assertSame(database, targetSystem.getMongoDatabase());
    }
}
