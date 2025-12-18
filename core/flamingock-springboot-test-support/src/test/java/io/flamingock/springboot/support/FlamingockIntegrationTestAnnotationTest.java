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
package io.flamingock.springboot.support;

import io.flamingock.common.test.pipeline.CodeChangeTestDefinition;
import io.flamingock.common.test.pipeline.PipelineTestHelper;
import io.flamingock.internal.common.core.util.Deserializer;
import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.internal.core.runner.PipelineExecutionException;
import io.flamingock.springboot.support.changes.*;
import io.flamingock.support.FlamingockTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Collections;

import static io.flamingock.support.domain.AuditEntryDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

@FlamingockIntegrationTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Spring Boot Integration - FlamingockIntegrationTest Annotation")
class FlamingockIntegrationTestAnnotationTest {

    @Autowired
    private AbstractChangeRunnerBuilder<?, ?> builder;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Should inject Flamingock builder as Spring bean")
    void shouldInjectFlamingockBuilder() {
        assertNotNull(builder, "Builder should be injected by Spring");
    }

    @Test
    @DisplayName("Should provide test infrastructure via Spring beans")
    void shouldProvideTestInfrastructure() {
        assertNotNull(builder, "Builder bean should be provided by test configuration");
        assertNotNull(environment, "Environment should be available");
    }

    @Test
    @DisplayName("Should execute non-transactional change")
    void shouldExecuteNonTransactionalChange() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SpringBootTestChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(builder)
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            APPLIED(_001__SpringBootTestChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify multiple changes execute in correct sequence with complete audit flow")
    void shouldVerifyMultipleChangesInSequence() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SpringBootTestChange.class, Collections.emptyList()),
                            new CodeChangeTestDefinition(_002__SpringBootTransactionalChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(builder)
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            APPLIED(_001__SpringBootTestChange.class),
                            APPLIED(_002__SpringBootTransactionalChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify failing transactional change triggers rollback with correct audit trail")
    void shouldVerifyFailingTransactionalChangeTriggersRollback() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_003__SpringBootFailingChange.class, Collections.emptyList(), Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(builder)
                    .whenRun()
                    .thenExpectException(PipelineExecutionException.class, ex ->
                            assertTrue(ex.getMessage().contains("Intentional test failure"))
                    )
                    .andExpectAuditSequenceStrict(
                            FAILED(_003__SpringBootFailingChange.class),
                            ROLLED_BACK(_003__SpringBootFailingChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify already-applied changes are skipped on subsequent runs")
    void shouldVerifyAlreadyAppliedChangesAreSkipped() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_001__SpringBootTestChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(builder)
                    .andExistingAudit(
                            APPLIED(_001__SpringBootTestChange.class)
                    )
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            APPLIED(_001__SpringBootTestChange.class)
                    )
                    .verify();
        }
    }

    @Test
    @DisplayName("Should verify transactional change executes successfully with correct audit entries")
    void shouldVerifyTransactionalChangeExecutesSuccessfully() {
        try (MockedStatic<Deserializer> mocked = Mockito.mockStatic(Deserializer.class)) {
            mocked.when(Deserializer::readPreviewPipelineFromFile).thenReturn(
                    PipelineTestHelper.getPreviewPipeline(
                            new CodeChangeTestDefinition(_002__SpringBootTransactionalChange.class, Collections.emptyList())
                    )
            );

            FlamingockTestSupport
                    .given(builder)
                    .whenRun()
                    .thenExpectAuditSequenceStrict(
                            APPLIED(_002__SpringBootTransactionalChange.class)
                    )
                    .verify();
        }
    }
}

