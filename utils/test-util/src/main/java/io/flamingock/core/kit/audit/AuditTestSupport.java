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
package io.flamingock.core.kit.audit;

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.core.kit.TestKit;
import io.flamingock.internal.common.core.util.Deserializer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Fluent builder for audit testing that abstracts MockedStatic boilerplate.
 * 
 * <p>This builder simplifies the common pattern of mocking Deserializer,
 * executing test code, and verifying audit results in a clean, readable way.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Automatic MockedStatic lifecycle management</li>
 *   <li>Integration with existing AuditTestHelper</li>
 *   <li>Clean fluent API for test scenarios</li>
 *   <li>No shared state between test executions</li>
 * </ul>
 */
public class AuditTestSupport {

    private final TestKit testKit;
    private AuditTestHelper auditHelper;
    private CodeChangeTestDefinition[] changes;
    private Runnable testCode;
    private AuditEntryExpectation[] expectedAudits;

    public static AuditTestSupport withTestKit(TestKit testKit) {
        return new AuditTestSupport(testKit);
    }

    AuditTestSupport(TestKit testKit) {
        this.testKit = testKit;
        this.auditHelper = testKit.getAuditHelper();
    }
    
    /**
     * Configures the changes to include in the mocked pipeline.
     * 
     * @param changes varargs array of CodeTestDefinition instances
     * @return this builder for method chaining
     */
    public AuditTestSupport GIVEN_Changes(CodeChangeTestDefinition... changes) {
        this.changes = changes;
        return this;
    }
    
    /**
     * Configures the test code to execute within the mocked environment.
     * 
     * <p>This is typically the code that creates and runs the Flamingock builder.</p>
     * 
     * @param testCode the test code to execute
     * @return this builder for method chaining
     */
    public AuditTestSupport WHEN(Runnable testCode) {
        this.testCode = testCode;
        return this;
    }
    
    /**
     * Configures the expected audit sequence for verification.
     * 
     * <p>This delegates to the existing AuditTestHelper.verifyAuditSequenceStrict method.</p>
     * 
     * @param expectedAudits varargs array of expected audit entry expectations
     * @return this builder for method chaining
     */
    public AuditTestSupport THEN_VerifyAuditSequenceStrict(AuditEntryExpectation... expectedAudits) {
        this.expectedAudits = expectedAudits;
        return this;
    }
    
    /**
     * Executes the configured test scenario with proper MockedStatic lifecycle management.
     * 
     * <p>This method:</p>
     * <ul>
     *   <li>Sets up Deserializer mock with configured changes</li>
     *   <li>Executes the test code</li>
     *   <li>Verifies audit sequence if configured</li>
     *   <li>Ensures proper cleanup of MockedStatic</li>
     * </ul>
     * 
     * @throws IllegalStateException if required configuration is missing
     */
    public void run() {
        if (auditHelper == null) {
            throw new IllegalStateException("AuditHelper must be configured");
        }
        if (changes == null || changes.length == 0) {
            throw new IllegalStateException("Changes must be configured");
        }
        if (testCode == null) {
            throw new IllegalStateException("Test code must be configured");
        }
        
        try (MockedStatic<Deserializer> mockedDeserializer = Mockito.mockStatic(Deserializer.class)) {
            // Set up the Deserializer mock with the configured changes
            mockedDeserializer.when(Deserializer::readPreviewPipelineFromFile)
                .thenReturn(PipelineTestHelper.getPreviewPipeline(changes));
            
            // Execute the test code
            testCode.run();
            
            // Verify audit sequence if configured
            if (expectedAudits != null && expectedAudits.length > 0) {
                auditHelper.verifyAuditSequenceStrict(expectedAudits);
            }
        }
    }
}