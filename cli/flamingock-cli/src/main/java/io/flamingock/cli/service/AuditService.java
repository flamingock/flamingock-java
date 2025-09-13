/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cli.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cli.config.ConfigLoader;
import io.flamingock.cli.config.DatabaseConfig;
import io.flamingock.cli.config.FlamingockConfig;
import io.flamingock.cli.factory.DynamoDBClientFactory;
import io.flamingock.cli.factory.MongoClientFactory;
import io.flamingock.community.dynamodb.driver.DynamoDBAuditStore;
import io.flamingock.community.mongodb.sync.driver.MongoSyncAuditStore;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.context.Context;
import io.flamingock.internal.common.core.context.Dependency;
import io.flamingock.internal.common.core.audit.issue.AuditEntryIssue;
import io.flamingock.internal.common.core.recovery.FixResult;
import io.flamingock.internal.common.core.recovery.Resolution;
import io.flamingock.internal.core.builder.ops.OpsClient;
import io.flamingock.internal.core.builder.ops.OpsClientBuilder;
import io.flamingock.internal.core.configuration.core.CoreConfiguration;
import io.flamingock.internal.core.configuration.community.CommunityConfiguration;
import io.flamingock.internal.core.context.SimpleContext;
import io.flamingock.internal.core.store.AuditStore;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class AuditService {
    private final FlamingockConfig config;
    private final ConfigLoader.DatabaseType databaseType;
    private OpsClient opsClient;

    public AuditService(FlamingockConfig config) {
        this.config = config;
        this.databaseType = ConfigLoader.detectDatabaseType(config);
        this.opsClient = createOpsClient();
    }

    /**
     * Get snapshot view - latest state per changeUnit (DEFAULT)
     *
     * @return list of audit entries representing the latest state of each change unit
     */
    public List<AuditEntry> listAuditEntriesSnapshot() {
        return opsClient.getAuditSnapshot();
    }

    /**
     * Get full chronological history
     *
     * @return list of audit entries in chronological order
     */
    public List<AuditEntry> listAuditEntriesHistory() {
        return opsClient.getAuditHistory();
    }

    /**
     * Get entries since a specific date
     *
     * @param since the date from which to retrieve audit entries
     * @return list of audit entries since the specified date
     */
    public List<AuditEntry> listAuditEntriesSince(LocalDateTime since) {
        return opsClient.getAuditSnapshotSince(since);
    }

    /**
     * Get only entries with issues
     *
     * @return list of audit entries that have issues requiring attention
     */
    public List<AuditEntryIssue> listAuditEntriesWithIssues() {
        return opsClient.getAuditIssues();
    }


    public FixResult fixAuditIssue(String changeId, Resolution resolution) {
        if (changeId == null || changeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Change ID is required");
        }
        return opsClient.fixAuditIssue(changeId.trim(), resolution);
    }
    /**
     * Get detailed information about a specific change unit that has issues.
     * 
     * @param changeId the change unit ID to inspect
     * @return detailed issue information including all audit entries, error details, etc.
     */
    public Optional<AuditEntryIssue> getAuditEntryIssue(String changeId) {
        if (changeId == null || changeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Change ID is required");
        }
        return opsClient.getAuditIssueByChange(changeId.trim());
    }

    private OpsClient createOpsClient() {
        try {
            // Create core configuration
            CoreConfiguration coreConfig = new CoreConfiguration();
            if (config.getServiceIdentifier() != null) {
                coreConfig.setServiceIdentifier(config.getServiceIdentifier());
            }

            // Create context with community configuration
            Context context = new SimpleContext();
            context.addDependency(new Dependency(CommunityConfiguration.class, new CommunityConfiguration()));

            // Create database-specific clients and audit store
            AuditStore<?> auditStore = createAuditStore(context);

            // Build and return OpsClient
            return new OpsClientBuilder(coreConfig, context, auditStore).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OpsClient: " + e.getMessage(), e);
        }
    }

    private AuditStore<?> createAuditStore(Context context) {
        switch (databaseType) {
            case MONGODB:
                return createMongoAuditStore(context);
            case DYNAMODB:
                return createDynamoAuditStore(context);
            default:
                throw new IllegalStateException("Unsupported database type: " + databaseType);
        }
    }

    private AuditStore<?> createMongoAuditStore(Context context) {
        DatabaseConfig.MongoDBConfig mongoConfig = config.getAudit().getMongodb();
        
        // Create MongoDB clients
        MongoClient mongoClient = MongoClientFactory.createMongoClient(mongoConfig);
        MongoDatabase mongoDatabase = MongoClientFactory.createMongoDatabase(mongoClient, mongoConfig);
        
        // Add clients to context as dependencies
        context.addDependency(new Dependency(MongoClient.class, mongoClient));
        context.addDependency(new Dependency(MongoDatabase.class, mongoDatabase));
        
        return new MongoSyncAuditStore();
    }

    private AuditStore<?> createDynamoAuditStore(Context context) {
        DatabaseConfig.DynamoDBConfig dynamoConfig = config.getAudit().getDynamodb();
        
        // Create DynamoDB client
        DynamoDbClient dynamoClient = DynamoDBClientFactory.createDynamoDbClient(dynamoConfig);
        
        // Add client to context as dependency
        context.addDependency(new Dependency(DynamoDbClient.class, dynamoClient));
        
        return new DynamoDBAuditStore();
    }
}