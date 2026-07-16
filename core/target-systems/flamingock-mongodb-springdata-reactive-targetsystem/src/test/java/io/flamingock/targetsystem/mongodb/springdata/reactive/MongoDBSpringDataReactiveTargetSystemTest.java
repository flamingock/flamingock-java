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

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.builder.FlamingockEdition;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoDBSpringDataReactiveTargetSystemTest {

    @Test
    void initializesReactiveMongoTemplateDependency() {
        ReactiveMongoTemplate template = mock(ReactiveMongoTemplate.class);
        ReactiveMongoDatabaseFactory databaseFactory = mock(ReactiveMongoDatabaseFactory.class);
        MongoDatabase database = mock(MongoDatabase.class);
        when(template.getMongoDatabaseFactory()).thenReturn(databaseFactory);
        when(databaseFactory.getMongoDatabase()).thenReturn(Mono.just(database));

        ContextResolver baseContext = mock(ContextResolver.class);
        when(baseContext.getDependencyValue(FlamingockEdition.class))
                .thenReturn(Optional.of(FlamingockEdition.COMMUNITY));

        MongoDBSpringDataReactiveTargetSystem targetSystem =
                new MongoDBSpringDataReactiveTargetSystem("mongodb", template);
        targetSystem.initialize(baseContext);

        assertSame(template, targetSystem.getContext().getRequiredDependencyValue(ReactiveMongoTemplate.class));
        assertSame(database, targetSystem.getMongoDatabase());
    }
}
