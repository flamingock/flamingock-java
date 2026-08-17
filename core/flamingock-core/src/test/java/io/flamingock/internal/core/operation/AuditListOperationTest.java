/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation;

import io.flamingock.internal.common.core.audit.AuditReader;
import io.flamingock.internal.core.operation.audit.AuditListArgs;
import io.flamingock.internal.core.operation.audit.AuditListOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditListOperationTest {

    @Mock
    private AuditReader auditReader;

    private AuditListOperation operation;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        operation = new AuditListOperation(auditReader);
    }

    @Test
    @DisplayName("Should reject execution as an enterprise-only feature")
    void shouldRejectExecutionAsEnterpriseFeature() {
        assertThrows(UnsupportedOperationException.class, () -> operation.execute(new AuditListArgs()));
        verifyNoInteractions(auditReader);
    }
}
