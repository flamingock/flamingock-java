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
package io.flamingock.internal.core.targets.mark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

public class NoOpTargetSystemAuditMarker implements TargetSystemAuditMarker {
    private static final Logger logger = LoggerFactory.getLogger(NoOpTargetSystemAuditMarker.class);

    private final String targetSystemId;

    public NoOpTargetSystemAuditMarker(String targetSystemId) {
        this.targetSystemId = targetSystemId;
    }

    @Override
    public Set<TargetSystemAuditMark> listAll() {
        logger.debug("Ignoring 'getAll' operation: no-op repository for target system '{}'", targetSystemId);
        return Collections.emptySet();
    }

    @Override
    public void clear(String changeId) {
        logger.debug("Ignoring 'clean' operation for task '{}': no-op repository for target system '{}'", changeId, targetSystemId);
    }

    @Override
    public void mark(TargetSystemAuditMark auditMark) {
        logger.debug("Ignoring 'register' operation for task '{}': no-op repository for target system '{}'", auditMark.getTaskId(), targetSystemId);
    }
}

