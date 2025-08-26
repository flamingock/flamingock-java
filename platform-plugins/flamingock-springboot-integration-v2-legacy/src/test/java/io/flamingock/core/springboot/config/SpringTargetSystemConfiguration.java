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
package io.flamingock.core.springboot.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.springboot.SpringRunnerType;
import io.flamingock.springboot.SpringbootProperties;
import io.flamingock.targetystem.mongodb.sync.MongoSyncTargetSystem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration
public class SpringTargetSystemConfiguration {

    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6"))
            .withReuse(false);

    static {
        mongoDBContainer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(mongoDBContainer::stop));
    }

    @Bean
    @Primary
    public SpringbootProperties springbootProperties() {
        SpringbootProperties properties = new SpringbootProperties();
        properties.setEnabled(true);
        properties.setRunnerType(SpringRunnerType.ApplicationRunner);
        return properties;
    }

    @Bean
    @Primary
    public WriteConcern writeConcern() {
        return WriteConcern.MAJORITY.withJournal(true);
    }

    @Bean
    @Primary
    public ReadConcern readConcern() {
        return ReadConcern.MAJORITY;
    }

    @Bean
    @Primary
    public ReadPreference readPreference() {
        return ReadPreference.primary();
    }

    @Bean
    @Primary
    public MongoClient mongoClient(WriteConcern writeConcern, ReadConcern readConcern, ReadPreference readPreference) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoDBContainer.getConnectionString()))
                .writeConcern(writeConcern)
                .readConcern(readConcern)
                .readPreference(readPreference)
                .build();

        return MongoClients.create(settings);
    }

    @Bean
    @Primary
    public MongoDatabase mongoDatabase(MongoClient mongoClient) {
        return mongoClient.getDatabase("flamingock_test");
    }

    @Bean
    @Primary
    public MongoSyncTargetSystem mongoSyncTargetSystem(MongoClient mongoClient,
                                                       MongoDatabase mongoDatabase,
                                                       WriteConcern writeConcern,
                                                       ReadConcern readConcern,
                                                       ReadPreference readPreference) {
        return new MongoSyncTargetSystem("mongo-sync-target-system")
                .withMongoClient(mongoClient)
                .withDatabase(mongoDatabase)
                .withWriteConcern(writeConcern)
                .withReadConcern(readConcern)
                .withReadPreference(readPreference);
    }

    @Bean
    @Primary
    public MongoSyncAuditStore auditStore() {
        return new MongoSyncAuditStore();
    }
}