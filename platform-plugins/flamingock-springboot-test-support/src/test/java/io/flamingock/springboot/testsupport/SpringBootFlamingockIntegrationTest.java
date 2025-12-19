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
package io.flamingock.springboot.testsupport;

import io.flamingock.springboot.testsupport.changes._001__SimpleTestChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static io.flamingock.support.domain.AuditEntryDefinition.APPLIED;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)  // Required for Spring Boot 2.0.x. For >= 2.1.0, not required
@FlamingockSpringBootTest(classes = {TestApplication.class, FlamingockTestConfiguration.class})
class SpringBootFlamingockIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private FlamingockSpringBootTestSupport testSupport;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void flamingockBuilderBeanExists() {
        assertThat(context.containsBean("flamingock-builder")).isTrue();
    }

    @Test
    void flamingockRunnerBeanNotExistsInDeferredMode() {
        // In DEFERRED mode, the runner bean is not created - user controls execution manually
        assertThat(context.containsBean("flamingock-runner")).isFalse();
    }

    @Test
    void shouldUseTestSupportAsExpected() {

        testSupport
                .givenBuilderFromContext()
                .whenRun()
                .thenExpectAuditSequenceStrict(
                        APPLIED(_001__SimpleTestChange.class)
                )
                .verify();
    }

}
