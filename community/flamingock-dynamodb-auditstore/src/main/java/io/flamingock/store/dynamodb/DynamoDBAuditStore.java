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
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.common.core.pipeline.PipelineHelper;
import io.flamingock.internal.common.core.transaction.TransactionWrapper;
import io.flamingock.internal.core.configuration.community.CommunityConfigurable;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.TimeService;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.dynamodb.internal.DynamoDBAuditPersistence;
import io.flamingock.store.dynamodb.internal.DynamoDBJournalEventStore;
import io.flamingock.store.dynamodb.internal.DynamoDBLockService;
import io.flamingock.externalsystem.dynamodb.api.DynamoDBExternalSystem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBAuditStore implements CommunityAuditStore {

    private static final String DEFAULT_JOURNAL_REPOSITORY_NAME = "flamingockJournalEvents";

    private final DynamoDbClient client;
    private final DynamoDBExternalSystem targetSystem;
    private RunnerId runnerId;
    private CommunityConfigurable communityConfiguration;
    private DynamoDBAuditPersistence legacyPersistence;
    private DynamoDBLockService lockService;
    private TransactionWrapper txWrapper;
    private DynamoDBJournalEventStore journalEventStore;
    private JournalEventSequencerFactory journalEventSequencerFactory;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private String journalRepositoryName = DEFAULT_JOURNAL_REPOSITORY_NAME;
    private long readCapacityUnits = 5L;
    private long writeCapacityUnits = 5L;
    private boolean autoCreate = true;

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
        txWrapper = targetSystem.getTxWrapper();
        journalEventStore = new DynamoDBJournalEventStore(
                client,
                journalRepositoryName,
                readCapacityUnits,
                writeCapacityUnits);
        journalEventSequencerFactory = new JournalEventSequencerFactory(journalEventStore);
        this.validate();
    }

    @Override
    public synchronized CommunityAuditPersistence getPersistence() {
        if (legacyPersistence == null) {
            if (FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
                journalEventStore.initialize(autoCreate);
                JournalEventSequencer journalEventSequencer =
                        journalEventSequencerFactory.forStream(PipelineHelper.LEGACY_STAGE_ID);
                legacyPersistence = new DynamoDBAuditPersistence(
                        client,
                        txWrapper,
                        journalEventStore,
                        journalEventSequencer,
                        journalEventSequencerFactory,
                        auditRepositoryName,
                        readCapacityUnits,
                        writeCapacityUnits,
                        autoCreate,
                        communityConfiguration);
            } else {
                legacyPersistence = new DynamoDBAuditPersistence(
                        client,
                        auditRepositoryName,
                        readCapacityUnits,
                        writeCapacityUnits,
                        autoCreate,
                        communityConfiguration);
            }
            legacyPersistence.initialize(runnerId);
        }
        return legacyPersistence;
    }

    @Override
    public AuditPersistenceFactory<CommunityAuditPersistence> getPersistenceFactory() {
        return stageId -> {
            if (!FeatureFlag.isEnabled(Features.JOURNAL_EVENTS)) {
                return getPersistence();
            }

            journalEventStore.initialize(autoCreate);
            JournalEventSequencer journalEventSequencer = journalEventSequencerFactory.forStream(stageId);
            DynamoDBAuditPersistence persistence = new DynamoDBAuditPersistence(
                    client,
                    txWrapper,
                    journalEventStore,
                    journalEventSequencer,
                    journalEventSequencerFactory,
                    auditRepositoryName,
                    readCapacityUnits,
                    writeCapacityUnits,
                    autoCreate,
                    communityConfiguration);
            persistence.initialize(runnerId);
            return persistence;
        };
    }

    @Override
    public synchronized CommunityLockService getLockService() {
        if (lockService == null) {
            lockService = new DynamoDBLockService(client, TimeService.getDefault());
            lockService.initialize(
                    autoCreate,
                    lockRepositoryName,
                    readCapacityUnits,
                    writeCapacityUnits);
        }
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
