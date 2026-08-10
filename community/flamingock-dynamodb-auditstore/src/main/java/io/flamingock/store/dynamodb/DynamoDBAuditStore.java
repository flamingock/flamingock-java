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
package io.flamingock.store.dynamodb;

import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.dynamodb.entities.journal.JournalEventFieldConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.dynamodb.internal.DynamoDBAuditPersistence;
import io.flamingock.store.dynamodb.internal.DynamoDBAuditRepository;
import io.flamingock.store.dynamodb.internal.DynamoDBJournalEventStore;
import io.flamingock.store.dynamodb.internal.DynamoDBLockService;
import io.flamingock.externalsystem.dynamodb.api.DynamoDBExternalSystem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBAuditStore implements CommunityAuditStore {

    private final DynamoDBExternalSystem targetSystem;

    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBAuditPersistence persistence;
    private DynamoDBLockService lockService;
    private final DynamoDbClient client;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private String journalRepositoryName = JournalEventFieldConstants.DEFAULT_JOURNAL_REPOSITORY_NAME;
    private long readCapacityUnits = 5L;
    private long writeCapacityUnits = 5L;
    private boolean autoCreate = true;
    private DynamoDBAuditRepository auditRepository;
    private DynamoDBJournalEventStore journalEventStore;
    private JournalEventSequencerFactory journalEventSequencerFactory;

    private DynamoDBAuditStore(DynamoDBExternalSystem targetSystem) {
        this.targetSystem = targetSystem;
        this.client = targetSystem.getClient();
    }

    /**
     * Creates a {@link DynamoDBAuditStore} using the same DynamoDB client
     * configured in the given {@link DynamoDBExternalSystem}.
     * <p>
     * The DynamoDB client and transaction wrapper are reused from the target system.
     *
     * @param targetSystem the target system from which to derive the client
     * @return a new audit store bound to the same DynamoDB instance as the target system
     */
    public static DynamoDBAuditStore from(DynamoDBExternalSystem targetSystem) {
        return new DynamoDBAuditStore(targetSystem);
    }

    @Override
    public String getId() {
        return Constants.DEFAULT_DYNAMODB_AUDIT_STORE;
    }

    public DynamoDBAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public DynamoDBAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
        return this;
    }

    public DynamoDBAuditStore withJournalRepositoryName(String journalRepositoryName) {
        this.journalRepositoryName = journalRepositoryName;
        return this;
    }

    public DynamoDBAuditStore withReadCapacityUnits(long readCapacityUnits) {
        this.readCapacityUnits = readCapacityUnits;
        return this;
    }

    public DynamoDBAuditStore withWriteCapacityUnits(long writeCapacityUnits) {
        this.writeCapacityUnits = writeCapacityUnits;
        return this;
    }

    public DynamoDBAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        communityConfiguration = baseContext.getRequiredDependencyValue(CommunityConfigurable.class);
        auditRepository = new DynamoDBAuditRepository(client, auditRepositoryName, readCapacityUnits, writeCapacityUnits);
        journalEventStore = new DynamoDBJournalEventStore(
                client,
                journalRepositoryName,
                readCapacityUnits,
                writeCapacityUnits
        );
        journalEventSequencerFactory = new JournalEventSequencerFactory(journalEventStore);

        lockService = new DynamoDBLockService(
            client,
            lockRepositoryName,
            readCapacityUnits,
            writeCapacityUnits,
            TimeService.getDefault()
        );
        lockService.initialize(autoCreate);
        this.validate();
    }

    @Override
    public AuditPersistenceFactory<CommunityAuditPersistence> getPersistenceFactory() {
        return stageId -> {
            JournalEventSequencer journalEventSequencer = journalEventSequencerFactory.forStream(stageId);
            persistence = new DynamoDBAuditPersistence(
                communityConfiguration,
                auditRepository,
                journalEventStore,
                journalEventSequencer,
                targetSystem.getTxWrapper(),
                autoCreate
            );
            persistence.initialize(runnerId);
            return persistence;
        };
    }

    @Override
    public AuditReader getAuditReader() {
        auditRepository.initialize(autoCreate);
        return () -> auditRepository.getAuditHistory();
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        return lockService;
    }

    private void validate() {

        if (client == null) {
            throw new FlamingockException("The 'client' instance is required.");
        }

        if (auditRepositoryName == null || auditRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'auditRepositoryName' property is required.");
        }

        if (lockRepositoryName == null || lockRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'lockRepositoryName' property is required.");
        }

        if (journalRepositoryName == null || journalRepositoryName.trim().isEmpty()) {
            throw new FlamingockException("The 'journalRepositoryName' property is required.");
        }

        if (readCapacityUnits <= 0) {
            throw new FlamingockException("The 'readCapacityUnits' property must be greater than zero.");
        }

        if (writeCapacityUnits <= 0) {
            throw new FlamingockException("The 'writeCapacityUnits' property must be greater than zero.");
        }

        if (auditRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'auditRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }

        if (journalRepositoryName.trim().equalsIgnoreCase(auditRepositoryName.trim())) {
            throw new FlamingockException("The 'journalRepositoryName' and 'auditRepositoryName' properties must not be the same.");
        }

        if (journalRepositoryName.trim().equalsIgnoreCase(lockRepositoryName.trim())) {
            throw new FlamingockException("The 'journalRepositoryName' and 'lockRepositoryName' properties must not be the same.");
        }
    }

}
