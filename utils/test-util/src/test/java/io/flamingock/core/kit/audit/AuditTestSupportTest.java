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

import io.flamingock.api.annotations.ChangeUnit;
import io.flamingock.common.test.pipeline.CodeChangeUnitTestDefinition;
import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static io.flamingock.core.kit.audit.AuditExpectation.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AuditTestSupport framework to ensure the testing framework itself works correctly.
 * 
 * <p>These tests verify that the fluent API and configuration work as expected
 * and provide proper validation of required parameters.</p>
 */
class AuditTestSupportTest {
    
    @Test
    void testFluentAPICreation() {
        // Test that the fluent API can be created without errors
        AuditTestSupport builder = AuditTestSupport.pipeline();
        assertNotNull(builder);
    }
    
    @Test
    void testBuilderConfiguration() {
        // Test that builder configuration methods work correctly
        AuditTestHelper mockHelper = Mockito.mock(AuditTestHelper.class);
        CodeChangeUnitTestDefinition testDef = new CodeChangeUnitTestDefinition(MockChangeClass.class, Collections.emptyList(), Collections.emptyList());
        
        AuditTestSupport builder = AuditTestSupport.pipeline()
            .withAuditHelper(mockHelper)
            .givenChangeUnits(testDef)
            .when(() -> {
                // Mock test execution
            })
            .thenVerifyAuditSequenceStrict(
                STARTED("test-change").withTxType(AuditTxType.NON_TX),
                EXECUTED("test-change").withTxType(AuditTxType.NON_TX)
            );
        
        assertNotNull(builder);
    }
    
    @Test
    void testExpectationFactoryMethods() {
        // Test that DSL factory methods create correct expectations
        AuditEntryExpectation started = STARTED("change1");
        assertEquals("change1", started.getExpectedTaskId());
        assertEquals(AuditEntry.Status.STARTED, started.getExpectedState());
        
        AuditEntryExpectation executed = EXECUTED("change2");
        assertEquals("change2", executed.getExpectedTaskId());
        assertEquals(AuditEntry.Status.EXECUTED, executed.getExpectedState());
        
        AuditEntryExpectation failed = EXECUTION_FAILED("change3");
        assertEquals("change3", failed.getExpectedTaskId());
        assertEquals(AuditEntry.Status.EXECUTION_FAILED, failed.getExpectedState());
        
        AuditEntryExpectation rolledBack = ROLLED_BACK("change4");
        assertEquals("change4", rolledBack.getExpectedTaskId());
        assertEquals(AuditEntry.Status.ROLLED_BACK, rolledBack.getExpectedState());
        
        AuditEntryExpectation rollbackFailed = ROLLBACK_FAILED("change5");
        assertEquals("change5", rollbackFailed.getExpectedTaskId());
        assertEquals(AuditEntry.Status.ROLLBACK_FAILED, rollbackFailed.getExpectedState());
    }
    
    @Test
    void testCustomExpectationCreation() {
        AuditEntryExpectation custom = AuditExpectation.withState("change1", AuditEntry.Status.STARTED);
        assertEquals("change1", custom.getExpectedTaskId());
        assertEquals(AuditEntry.Status.STARTED, custom.getExpectedState());
    }
    
    @Test
    void testMissingAuditHelperValidation() {
        // Test that missing audit helper throws appropriate error
        CodeChangeUnitTestDefinition testDef = new CodeChangeUnitTestDefinition(MockChangeClass.class, Collections.emptyList(), Collections.emptyList());
        
        AuditTestSupport builder = AuditTestSupport.pipeline()
            .givenChangeUnits(testDef)
            .when(() -> {
                // Mock test execution
            });
        
        IllegalStateException error = assertThrows(IllegalStateException.class, builder::run);
        assertTrue(error.getMessage().contains("AuditHelper must be configured"));
    }
    
    @Test
    void testMissingChangeUnitsValidation() {
        // Test that missing change units throws appropriate error
        AuditTestHelper mockHelper = Mockito.mock(AuditTestHelper.class);
        
        AuditTestSupport builder = AuditTestSupport.pipeline()
            .withAuditHelper(mockHelper)
            .when(() -> {
                // Mock test execution
            });
        
        IllegalStateException error = assertThrows(IllegalStateException.class, builder::run);
        assertTrue(error.getMessage().contains("Change units must be configured"));
    }
    
    @Test
    void testMissingTestCodeValidation() {
        // Test that missing test code throws appropriate error
        AuditTestHelper mockHelper = Mockito.mock(AuditTestHelper.class);
        CodeChangeUnitTestDefinition testDef = new CodeChangeUnitTestDefinition(MockChangeClass.class, Collections.emptyList(), Collections.emptyList());
        
        AuditTestSupport builder = AuditTestSupport.pipeline()
            .withAuditHelper(mockHelper)
            .givenChangeUnits(testDef);
        
        IllegalStateException error = assertThrows(IllegalStateException.class, builder::run);
        assertTrue(error.getMessage().contains("Test code must be configured"));
    }
    
    // Mock change class for testing
    @ChangeUnit(id = "test-mock-change", order = "001", author = "test-author")
    public static class MockChangeClass {
        public void execute() {
            // Mock change implementation
        }
    }
}