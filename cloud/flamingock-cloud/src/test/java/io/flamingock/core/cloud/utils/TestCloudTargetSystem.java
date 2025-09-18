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
package io.flamingock.core.cloud.utils;

import io.flamingock.internal.common.core.context.ContextResolver;
import io.flamingock.internal.core.runtime.ExecutionRuntime;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMark;
import io.flamingock.internal.core.targets.mark.TargetSystemAuditMarker;
import io.flamingock.internal.core.targets.TransactionalTargetSystem;
import io.flamingock.internal.core.transaction.TransactionWrapper;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class TestCloudTargetSystem extends TransactionalTargetSystem<TestCloudTargetSystem> {


    private final TestTargetSystemAuditMarker ongoignRepo;
    private final TestCloudTxWrapper txWrapper;

    public TestCloudTargetSystem(String id, TargetSystemAuditMark... statuses) {
        super(id);
        this.ongoignRepo = Mockito.spy(new TestTargetSystemAuditMarker(statuses));
        this.txWrapper = Mockito.spy(new TestCloudTxWrapper());
    }

    public TargetSystemAuditMarker getOnGoingTaskStatusRepository() {
        return ongoignRepo;
    }

    @Override
    public TransactionWrapper getTxWrapper() {
        return txWrapper;
    }

    @Override
    public void initialize(ContextResolver baseContext) {

    }

    @Override
    protected TestCloudTargetSystem getSelf() {
        return this;
    }


    public static class TestCloudTxWrapper implements TransactionWrapper {
        @Override
        public <T> T wrapInTransaction(ExecutionRuntime executionRuntime, Function<ExecutionRuntime, T> changeApplier) {
            return changeApplier.apply(executionRuntime);
        }
    }


    public static class TestTargetSystemAuditMarker implements TargetSystemAuditMarker {
        private final HashSet<TargetSystemAuditMark> ongoingStatuses;

        private TestTargetSystemAuditMarker(TargetSystemAuditMark... statuses) {
            ongoingStatuses = statuses != null ? new HashSet<>(Arrays.asList(statuses)) : new HashSet<>();

        }


        @Override
        public Set<TargetSystemAuditMark> listAll() {
            return ongoingStatuses;
        }

        @Override
        public void clearMark(String changeId) {
            ongoingStatuses.removeIf(status -> changeId.equals(status.getTaskId()));
        }

        @Override
        public void mark(TargetSystemAuditMark auditMark) {
            ongoingStatuses.add(auditMark);
        }

    }
}
