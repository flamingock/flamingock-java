/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.core.springboot;

import com.mongodb.client.MongoDatabase;
import io.flamingock.api.targets.TargetSystem;
import io.flamingock.core.springboot.config.SpringTargetSystemConfiguration;
import io.flamingock.springboot.SpringbootContext;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SpringTargetSystemIntegrationTest {

    @Nested
    @SpringBootTest(classes = SpringTargetSystemTestApplication.class)
    @Import({SpringbootContext.class, SpringTargetSystemConfiguration.class})
    @ActiveProfiles("mongo-test")
    class SingleTargetSystemTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Autowired
        private MongoDatabase mongoDatabase;

        @Test
        @DisplayName("SHOULD inject MongoDB target system and verify it works")
        void shouldInjectMongoTargetSystem() {
            Map<String, TargetSystem> targetSystems = applicationContext.getBeansOfType(TargetSystem.class);

            assertEquals(1, targetSystems.size(), "Should have exactly one target system");
            assertTrue(targetSystems.containsKey("mongoSyncTargetSystem"));

            MongoSyncTargetSystem mongoTargetSystem = (MongoSyncTargetSystem) targetSystems.get("mongoSyncTargetSystem");
            assertNotNull(mongoTargetSystem.getDatabase());
            assertEquals("mongo-sync-target-system", mongoTargetSystem.getId());
        }

        @Test
        @DisplayName("SHOULD execute change units successfully")
        void shouldExecuteChangeUnits() {
            boolean collectionExists = mongoDatabase.listCollectionNames()
                    .into(new java.util.ArrayList<>())
                    .contains("test_collection");

            assertTrue(collectionExists, "Change unit should have created test_collection");

            long documentCount = mongoDatabase.getCollection("test_collection").countDocuments();
            assertEquals(1, documentCount, "Change unit should have inserted one document");
        }
    }
}