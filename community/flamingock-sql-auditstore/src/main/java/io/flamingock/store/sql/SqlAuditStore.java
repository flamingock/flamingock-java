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
package io.flamingock.store.sql;

import io.flamingock.internal.common.core.audit.AuditHistoryAppender;
import io.flamingock.internal.common.core.audit.JournalHistoryAppender;
import io.flamingock.internal.common.core.audit.AuditPersistenceFactory;
import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.common.core.error.FlamingockException;
import io.flamingock.internal.common.core.feature.Features;
import io.flamingock.internal.core.external.store.CommunityAuditStore;
import io.flamingock.internal.core.external.store.HistoryAppenderProvider;
import io.flamingock.internal.core.external.store.audit.community.CommunityAuditPersistence;
import io.flamingock.internal.core.external.store.lock.community.CommunityLockService;
import io.flamingock.internal.core.journal.JournalEventSequencer;
import io.flamingock.internal.core.journal.JournalEventSequencerFactory;
import io.flamingock.internal.util.Constants;
import io.flamingock.internal.util.FeatureFlag;
import io.flamingock.internal.util.constants.CommunityPersistenceConstants;
import io.flamingock.internal.util.id.RunnerId;
import io.flamingock.store.sql.internal.SqlAuditPersistence;
import io.flamingock.store.sql.internal.SqlAuditRepository;
import io.flamingock.store.sql.internal.SqlLockService;
import io.flamingock.store.sql.internal.SqlJournalEventStore;
import io.flamingock.store.sql.internal.SqlJournalHistoryAppender;
import io.flamingock.internal.core.transaction.TransactionManager;
import io.flamingock.targetsystem.sql.SqlTxWrapper;
import java.sql.Connection;
import java.sql.SQLException;
import io.flamingock.externalsystem.sql.api.SqlExternalSystem;

import javax.sql.DataSource;

public class SqlAuditStore implements CommunityAuditStore, HistoryAppenderProvider {

    private static final String SQL_IDENTIFIER_PATTERN = "[A-Za-z][A-Za-z0-9_]*";
    private static final String DEFAULT_JOURNAL_REPOSITORY_NAME = "flamingockJournalEvents";

    private final SqlExternalSystem targetSystem;
    private final DataSource dataSource;
    private RunnerId runnerId;
    private SqlLockService lockService;
    private SqlJournalEventStore journalEventStore;
    private SqlJournalHistoryAppender journalHistoryWriter;
    private JournalEventSequencerFactory journalEventSequencerFactory;
    private SqlAuditRepository auditRepository;
    private String auditRepositoryName = CommunityPersistenceConstants.DEFAULT_AUDIT_STORE_NAME;
    private String lockRepositoryName = CommunityPersistenceConstants.DEFAULT_LOCK_STORE_NAME;
    private String journalRepositoryName = DEFAULT_JOURNAL_REPOSITORY_NAME;
    private boolean autoCreate = true;

    private SqlAuditStore(SqlExternalSystem targetSystem) {
        this.targetSystem = targetSystem;
        this.dataSource = targetSystem.getDataSource();
    }

    /**
     * Creates a {@link SqlAuditStore} using the same SQL datasource
     * configured in the given {@link SqlExternalSystem}.
     * <p>
     * Only the underlying SQL datasource is reused.
     * No additional target-system configuration is carried over.
     *
     * @param targetSystem the target system from which to derive the datasource
     * @return a new audit store bound to the same SQL datasource as the target system
     */
    public static SqlAuditStore from(SqlExternalSystem targetSystem) {
        return new SqlAuditStore(targetSystem);
    }

    @Override
    public String getId() {
        return Constants.DEFAULT_SQL_AUDIT_STORE;
    }

    public SqlAuditStore withAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
        return this;
    }

    public SqlAuditStore withLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
        return this;
    }

    public SqlAuditStore withJournalRepositoryName(String journalRepositoryName) {
        this.journalRepositoryName = journalRepositoryName;
        return this;
    }

    public SqlAuditStore withAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
        return this;
    }

    @Override
    public void initialize(ContextResolver baseContext) {
        runnerId = baseContext.getRequiredDependencyValue(RunnerId.class);
        validate();
        auditRepository = new SqlAuditRepository(dataSource, auditRepositoryName);
        journalEventStore = new SqlJournalEventStore(
                dataSource,
                journalRepositoryName,
                new SqlTxWrapper(new TransactionManager<>(this::openAuditConnection)));
        journalEventSequencerFactory = new JournalEventSequencerFactory(journalEventStore);
        journalHistoryWriter = new SqlJournalHistoryAppender(journalEventStore, journalEventSequencerFactory,
                new SqlTxWrapper(new TransactionManager<>(this::openAuditConnection)));
        auditRepository.initialize(autoCreate);

        lockService = new SqlLockService(dataSource, lockRepositoryName);
        lockService.initialize(autoCreate);
        if (isJournalEventsEnabled()) {
            journalEventStore.initialize(autoCreate);
        }
    }

    @Override
    public AuditPersistenceFactory<CommunityAuditPersistence> getPersistenceFactory() {
        return stageId -> {
            boolean journalEventsEnabled = isJournalEventsEnabled();
            JournalEventSequencer journalEventSequencer = null;
            if (journalEventsEnabled) {
                journalEventSequencer = journalEventSequencerFactory.forStream(stageId);
            }

            SqlAuditPersistence persistence = new SqlAuditPersistence(
                    auditRepository,
                    journalEventStore,
                    journalEventSequencer,
                    new SqlTxWrapper(new TransactionManager<>(this::openAuditConnection)),
                    journalEventsEnabled,
                    journalHistoryWriter);
            persistence.initialize(runnerId);
            return persistence;
        };
    }

    @Override
    public AuditHistoryAppender getAuditHistoryAppender() {
        return auditRepository::append;
    }

    @Override
    public JournalHistoryAppender getJournalHistoryAppender() {
        return (streamId, entry) -> {
            if (!isJournalEventsEnabled()) {
                throw new IllegalStateException("Journal Events are disabled");
            }
            return journalHistoryWriter.append(streamId, entry);
        };
    }

    private Connection openAuditConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not open audit SQL connection", exception);
        }
    }

    @Override
    public synchronized AuditReader getAuditReader() {
        return () -> auditRepository.getAuditHistory();
    }


    @Override
    public synchronized CommunityLockService getLockService() {
        return lockService;
    }

    private void validate() {
        if (targetSystem == null || dataSource == null) {
            throw new FlamingockException("The 'SqlExternalSystem' and its 'DataSource' are required.");
        }
        validateRepositoryName(auditRepositoryName, "auditRepositoryName");
        validateRepositoryName(lockRepositoryName, "lockRepositoryName");
        validateRepositoryName(journalRepositoryName, "journalRepositoryName");
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

    private void validateRepositoryName(String repositoryName, String propertyName) {
        if (repositoryName == null || repositoryName.trim().isEmpty()) {
            throw new FlamingockException(propertyName + " must not be blank");
        }
        if (!repositoryName.matches(SQL_IDENTIFIER_PATTERN)) {
            throw new FlamingockException(propertyName + " must be a simple SQL identifier");
        }
    }

    private static boolean isJournalEventsEnabled() {
        try {
            return FeatureFlag.isEnabled(Features.JOURNAL_EVENTS, false);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
