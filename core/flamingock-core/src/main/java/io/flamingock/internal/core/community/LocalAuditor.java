/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.internal.core.community;


import io.flamingock.commons.utils.Result;
import io.flamingock.internal.core.engine.audit.AuditReader;
import io.flamingock.internal.core.engine.audit.ExecutionAuditWriter;
import io.flamingock.internal.core.engine.audit.domain.ExecutionAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.RollbackAuditContextBundle;
import io.flamingock.internal.core.engine.audit.domain.StartExecutionAuditContextBundle;

public interface LocalAuditor extends ExecutionAuditWriter, AuditReader {

    default Result writeStartExecution(StartExecutionAuditContextBundle auditContextBundle) {
        return Result.OK();
    }

    default Result writeExecution(ExecutionAuditContextBundle auditContextBundle) {
        return writeEntry(auditContextBundle.toAuditEntry());
    }

    default Result writeRollback(RollbackAuditContextBundle auditContextBundle) {
        return writeEntry(auditContextBundle.toAuditEntry());
    }

}
