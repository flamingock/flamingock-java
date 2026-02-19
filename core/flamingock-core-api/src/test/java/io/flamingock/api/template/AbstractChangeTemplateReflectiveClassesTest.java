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
package io.flamingock.api.template;

import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.ChangeTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class AbstractChangeTemplateReflectiveClassesTest {

    // Simple test configuration class
    public static class TestConfig {
        public String configValue;
    }

    // Simple test apply payload class
    public static class TestApplyPayload {
        public String applyData;
    }

    // Simple test rollback payload class
    public static class TestRollbackPayload {
        public String rollbackData;
    }

    // Additional class for reflection
    public static class AdditionalClass {
        public String additionalData;
    }

    // Another additional class for reflection
    public static class AnotherAdditionalClass {
        public String moreData;
    }

    // Test template with custom generic types
    @ChangeTemplate(id = "test-custom-types")
    public static class TestTemplateWithCustomTypes
            extends AbstractChangeTemplate<TestConfig, TestApplyPayload, TestRollbackPayload> {

        public TestTemplateWithCustomTypes() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Test template with additional reflective classes
    @ChangeTemplate(id = "test-additional-classes")
    public static class TestTemplateWithAdditionalClasses
            extends AbstractChangeTemplate<TestConfig, TestApplyPayload, TestRollbackPayload> {

        public TestTemplateWithAdditionalClasses() {
            super(AdditionalClass.class, AnotherAdditionalClass.class);
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    // Test template with Void configuration
    @ChangeTemplate(id = "test-void-config")
    public static class TestTemplateWithVoidConfig
            extends AbstractChangeTemplate<Void, String, String> {

        public TestTemplateWithVoidConfig() {
            super();
        }

        @Apply
        public void apply() {
            // Test implementation
        }
    }

    @Test
    @DisplayName("getReflectiveClasses should return set containing configuration class")
    void getReflectiveClassesShouldContainConfigurationClass() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(TestConfig.class),
                "Should contain configuration class TestConfig");
    }

    @Test
    @DisplayName("getReflectiveClasses should return set containing apply payload class")
    void getReflectiveClassesShouldContainApplyPayloadClass() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(TestApplyPayload.class),
                "Should contain apply payload class TestApplyPayload");
    }

    @Test
    @DisplayName("getReflectiveClasses should return set containing rollback payload class")
    void getReflectiveClassesShouldContainRollbackPayloadClass() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(TestRollbackPayload.class),
                "Should contain rollback payload class TestRollbackPayload");
    }

    @Test
    @DisplayName("getReflectiveClasses should return set containing TemplateStep class")
    void getReflectiveClassesShouldContainTemplateStepClass() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(TemplateStep.class),
                "Should contain TemplateStep class");
    }

    @Test
    @DisplayName("getReflectiveClasses should include additional reflective classes passed to constructor")
    void getReflectiveClassesShouldIncludeAdditionalClasses() {
        TestTemplateWithAdditionalClasses template = new TestTemplateWithAdditionalClasses();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(AdditionalClass.class),
                "Should contain AdditionalClass");
        assertTrue(reflectiveClasses.contains(AnotherAdditionalClass.class),
                "Should contain AnotherAdditionalClass");
    }

    @Test
    @DisplayName("Multiple calls to getReflectiveClasses should return equivalent sets")
    void multipleCallsShouldReturnEquivalentSets() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> firstCall = template.getReflectiveClasses();
        Collection<Class<?>> secondCall = template.getReflectiveClasses();

        assertEquals(firstCall.size(), secondCall.size(),
                "Both calls should return sets of the same size");
        assertTrue(firstCall.containsAll(secondCall),
                "First call should contain all elements of second call");
        assertTrue(secondCall.containsAll(firstCall),
                "Second call should contain all elements of first call");
    }

    @Test
    @DisplayName("getReflectiveClasses with Void configuration should include Void class")
    void getReflectiveClassesWithVoidConfigShouldIncludeVoidClass() {
        TestTemplateWithVoidConfig template = new TestTemplateWithVoidConfig();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.contains(Void.class),
                "Should contain Void class for configuration");
        assertTrue(reflectiveClasses.contains(String.class),
                "Should contain String class for apply/rollback payloads");
    }

    @Test
    @DisplayName("getReflectiveClasses should return at least 4 classes (config, apply, rollback, TemplateStep)")
    void getReflectiveClassesShouldReturnAtLeast4Classes() {
        TestTemplateWithCustomTypes template = new TestTemplateWithCustomTypes();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.size() >= 4,
                "Should return at least 4 classes (config, apply, rollback, TemplateStep)");
    }

    @Test
    @DisplayName("getReflectiveClasses with additional classes should return more than 4 classes")
    void getReflectiveClassesWithAdditionalClassesShouldReturnMoreThan4() {
        TestTemplateWithAdditionalClasses template = new TestTemplateWithAdditionalClasses();

        Collection<Class<?>> reflectiveClasses = template.getReflectiveClasses();

        assertTrue(reflectiveClasses.size() >= 6,
                "Should return at least 6 classes (config, apply, rollback, TemplateStep, + 2 additional)");
    }
}
