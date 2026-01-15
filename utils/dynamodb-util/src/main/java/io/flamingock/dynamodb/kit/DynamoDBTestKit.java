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
package io.flamingock.dynamodb.kit;

import io.flamingock.core.kit.AbstractTestKit;
import io.flamingock.core.kit.audit.AuditStorage;
import io.flamingock.core.kit.lock.LockStorage;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;

public class DynamoDBTestKit extends AbstractTestKit {

    private final DynamoDbClient client;

    public DynamoDBTestKit(AuditStorage auditStorage, LockStorage lockStorage, CommunityAuditStore AuditStore, DynamoDbClient client) {
        super(auditStorage, lockStorage, AuditStore);
        this.client = client;
    }

    @Override
    public void cleanUp() {
        if (client != null) {
            // delete all tables created during the test
            client.listTables().tableNames().forEach(tableName -> {
                client.deleteTable(DeleteTableRequest.builder()
                        .tableName(tableName)
                        .build());
            });

            client.close();
        }
    }

    /**
     * Creates a new DynamoDBTestKit with DynamoDB client and auditStore using default configuration.
     * 
     * @param client the DynamoDB client for database operations
     * @param auditStore the local auditStore for Flamingock execution
     * @return configured TestKit instance
     */
    public static DynamoDBTestKit create(DynamoDbClient client, CommunityAuditStore auditStore) {
        return builder()
            .withClient(client)
            .withAuditStore(auditStore)
            .build();
    }
    
    /**
     * Creates a new DynamoDBTestKit builder for advanced configuration.
     * 
     * @return new DynamoDBTestKitBuilder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private DynamoDbClient client;
        private CommunityAuditStore AuditStore;
        private String auditTableName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
        private String lockTableName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
        private boolean autoCleanup = true;
        private boolean createTablesIfNotExist = false;
        private Long readCapacityUnits = 5L;
        private Long writeCapacityUnits = 5L;

        Builder() {
        }

        /**
         * Configures the DynamoDB client for this TestKit.
         *
         * @param client the DynamoDB client to use
         * @return this builder for method chaining
         */
        public Builder withClient(DynamoDbClient client) {
            this.client = client;
            return this;
        }

        /**
         * Configures the AuditStore for this TestKit.
         *
         * @param AuditStore the AuditStore to use
         * @return this builder for method chaining
         */
        public Builder withAuditStore(CommunityAuditStore AuditStore) {
            this.AuditStore = AuditStore;
            return this;
        }

        /**
         * Configures a custom audit table name.
         *
         * @param auditTableName the name of the audit table
         * @return this builder for method chaining
         */
        public Builder withAuditTableName(String auditTableName) {
            this.auditTableName = auditTableName;
            return this;
        }

        /**
         * Configures a custom lock table name.
         *
         * @param lockTableName the name of the lock table
         * @return this builder for method chaining
         */
        public Builder withLockTableName(String lockTableName) {
            this.lockTableName = lockTableName;
            return this;
        }

        /**
         * Configures whether to automatically clean up tables between tests.
         *
         * @param autoCleanup true to enable automatic cleanup
         * @return this builder for method chaining
         */
        public Builder withAutoCleanup(boolean autoCleanup) {
            this.autoCleanup = autoCleanup;
            return this;
        }

        /**
         * Configures whether to create tables if they don't exist.
         *
         * @param createTablesIfNotExist true to create tables automatically
         * @return this builder for method chaining
         */
        public Builder withCreateTablesIfNotExist(boolean createTablesIfNotExist) {
            this.createTablesIfNotExist = createTablesIfNotExist;
            return this;
        }

        /**
         * Configures the read capacity units for test tables.
         *
         * @param readCapacityUnits the read capacity units
         * @return this builder for method chaining
         */
        public Builder withReadCapacityUnits(Long readCapacityUnits) {
            this.readCapacityUnits = readCapacityUnits;
            return this;
        }

        /**
         * Configures the write capacity units for test tables.
         *
         * @param writeCapacityUnits the write capacity units
         * @return this builder for method chaining
         */
        public Builder withWriteCapacityUnits(Long writeCapacityUnits) {
            this.writeCapacityUnits = writeCapacityUnits;
            return this;
        }

        /**
         * Builds the configured DynamoDB TestKit.
         *
         * @return configured TestKit instance
         * @throws IllegalStateException if required configuration is missing
         */
        public DynamoDBTestKit build() {
            if (client == null) {
                throw new IllegalStateException("DynamoDB client must be configured");
            }
            if (AuditStore == null) {
                throw new IllegalStateException("AuditStore must be configured");
            }

            DynamoDBAuditStorage auditStorage = new DynamoDBAuditStorage(
                    client, auditTableName, autoCleanup,
                    readCapacityUnits, writeCapacityUnits
            );

            DynamoDBLockStorage lockStorage = new DynamoDBLockStorage(
                    client, lockTableName, createTablesIfNotExist, autoCleanup,
                    readCapacityUnits, writeCapacityUnits
            );

            return new DynamoDBTestKit(auditStorage, lockStorage, AuditStore, client);
        }
    }
}