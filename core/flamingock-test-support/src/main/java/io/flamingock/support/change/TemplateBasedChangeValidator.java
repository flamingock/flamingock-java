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
package io.flamingock.support.change;

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateFileContent;
import io.flamingock.internal.util.FileUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Fluent assertion utility for validating that a template-based change YAML file is correctly
 * structured.
 *
 * <p>Parses the YAML file using the same {@link ChangeTemplateFileContent} model that the
 * Flamingock runtime uses, then exposes assertions for the fields that are meaningful for
 * template changes: id, author, template name, transactionality, target system, recovery
 * strategy, step count, and rollback presence.</p>
 *
 * <p>All assertions use a <strong>soft-assertion pattern</strong>: each chained call queues an
 * assertion, and {@link #validate()} executes them all together, collecting every failure into a
 * single {@link AssertionError}. This means you see all problems at once rather than stopping at
 * the first mismatch.</p>
 *
 * <h2>Implicit validation at construction</h2>
 * <p>{@link ChangeValidator#of(Path)} checks eagerly that:
 * <ul>
 *   <li>The file exists</li>
 *   <li>The {@code id} field is present and non-empty</li>
 *   <li>The {@code template} field is present and non-empty</li>
 *   <li>Either an {@code apply} field or a {@code steps} list is present</li>
 * </ul>
 *
 * <h2>Usage example — simple template</h2>
 * <pre>{@code
 * ChangeValidator.of(Paths.get("src/test/java/.../changes/_0001__create_users_collection.yaml"))
 *     .withId("create-users-collection")
 *     .withOrder("0001")
 *     .withTemplateName("MongoChangeTemplate")
 *     .isNotTransactional()
 *     .hasRollback()
 *     .validate();
 * }</pre>
 *
 * <h2>Usage example — multi-step template</h2>
 * <pre>{@code
 * ChangeValidator.of(Paths.get("src/test/java/.../changes/_0005__step_based_change.yaml"))
 *     .withId("step-based-change")
 *     .withOrder("0005")
 *     .withStepCount(3)
 *     .hasRollbackForStep(0)
 *     .validate();
 * }</pre>
 *
 * @see ChangeValidator
 */
public final class TemplateBasedChangeValidator extends ChangeValidator<TemplateBasedChangeValidator> {

    private final ChangeTemplateFileContent content;

    TemplateBasedChangeValidator(Path yamlPath) {
        super(
                nameWithoutExtension(yamlPath),
                ChangeNamingConvention.extractOrder(nameWithoutExtension(yamlPath))
        );
        File file = yamlPath.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException(
                    String.format("YAML file does not exist: %s", yamlPath.toAbsolutePath()));
        }
        this.content = FileUtil.getFromYamlFile(file, ChangeTemplateFileContent.class);

        if (content.getId() == null || content.getId().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("YAML file [%s] must have a non-empty 'id' field", displayName));
        }
        if (content.getTemplate() == null || content.getTemplate().isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("YAML file [%s] must have a non-empty 'template' field", displayName));
        }
        if (content.getApply() == null && !(content.getSteps() instanceof List)) {
            throw new IllegalArgumentException(
                    String.format("YAML file [%s] must have either an 'apply' field or a 'steps' list", displayName));
        }
    }

    private static String nameWithoutExtension(Path yamlPath) {
        String fileName = yamlPath.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @Override
    protected String getId() {
        return content.getId();
    }

    @Override
    protected String getAuthor() {
        return content.getAuthor();
    }

    /**
     * Asserts that the author field in the YAML matches the expected value.
     *
     * <p>Overrides the base implementation to handle the case where no {@code author} field is
     * present in the YAML (in which case {@link #getAuthor()} returns {@code null}). Calling this
     * method with a non-null {@code expected} when the YAML has no author reports a clear failure
     * message rather than throwing a {@link NullPointerException}.</p>
     *
     * @param expected the expected author string, or {@code null} to assert no author is set
     * @return this validator for chaining
     */
    @Override
    public TemplateBasedChangeValidator withAuthor(String expected) {
        addAssertion(() -> {
            String actual = getAuthor();
            if (expected == null && actual == null) {
                return ChangeValidatorResult.OK();
            }
            if (actual == null) {
                return ChangeValidatorResult.error(String.format(
                        "withAuthor: expected \"%s\" but no 'author' field is set in the YAML", expected));
            }
            return actual.equals(expected)
                    ? ChangeValidatorResult.OK()
                    : ChangeValidatorResult.error(String.format(
                            "withAuthor: expected \"%s\" but was \"%s\"", expected, actual));
        });
        return this;
    }

    @Override
    protected boolean isTransactionalValue() {
        return content.getTransactional() == null || content.getTransactional();
    }

    @Override
    protected String getTargetSystemId() {
        return content.getTargetSystem() != null ? content.getTargetSystem().getId() : null;
    }

    @Override
    protected RecoveryStrategy getRecovery() {
        RecoveryDescriptor recovery = content.getRecovery();
        return recovery != null ? recovery.getStrategy() : RecoveryStrategy.MANUAL_INTERVENTION;
    }

    private boolean isMultiStep() {
        return content.getSteps() instanceof List;
    }

    /**
     * Asserts that the {@code template} field in the YAML matches the expected template name.
     *
     * @param expected the expected template simple name (e.g. {@code "MongoChangeTemplate"})
     * @return this validator for chaining
     */
    public TemplateBasedChangeValidator withTemplateName(String expected) {
        addAssertion(() -> {
            String actual = content.getTemplate();
            return actual.equals(expected)
                    ? ChangeValidatorResult.OK()
                    : ChangeValidatorResult.error(String.format(
                            "withTemplateName: expected \"%s\" but was \"%s\"", expected, actual));
        });
        return this;
    }

    /**
     * Asserts that the template has the given number of steps.
     *
     * <p>Reports a descriptive error if this is a simple (non-multi-step) template, i.e. the
     * YAML has an {@code apply} field rather than a {@code steps} list.</p>
     *
     * @param expected the expected step count
     * @return this validator for chaining
     */
    public TemplateBasedChangeValidator withStepCount(int expected) {
        addAssertion(() -> {
            if (!isMultiStep()) {
                return ChangeValidatorResult.error(
                        "withStepCount: this is a simple template (no 'steps' list found); "
                                + "withStepCount is only applicable to multi-step templates");
            }
            List<?> steps = (List<?>) content.getSteps();
            int actual = steps.size();
            return actual == expected
                    ? ChangeValidatorResult.OK()
                    : ChangeValidatorResult.error(String.format(
                            "withStepCount: expected %d steps but found %d", expected, actual));
        });
        return this;
    }

    /**
     * Asserts that a rollback is defined for the change.
     *
     * <ul>
     *   <li>For <strong>simple templates</strong>: the top-level {@code rollback} field must be
     *       non-null.</li>
     *   <li>For <strong>multi-step templates</strong>: every step must contain a {@code rollback}
     *       field. If any step is missing a rollback the assertion reports which step index is at
     *       fault.</li>
     * </ul>
     *
     * @return this validator for chaining
     */
    public TemplateBasedChangeValidator hasRollback() {
        addAssertion(() -> {
            if (isMultiStep()) {
                List<?> steps = (List<?>) content.getSteps();
                for (int i = 0; i < steps.size(); i++) {
                    Object step = steps.get(i);
                    if (!(step instanceof Map) || ((Map<?, ?>) step).get("rollback") == null) {
                        return ChangeValidatorResult.error(String.format(
                                "hasRollback: step %d is missing a 'rollback' field", i));
                    }
                }
                return ChangeValidatorResult.OK();
            } else {
                return content.getRollback() != null
                        ? ChangeValidatorResult.OK()
                        : ChangeValidatorResult.error("hasRollback: no top-level 'rollback' field found");
            }
        });
        return this;
    }

    /**
     * Asserts that the step at the given 0-based index has a {@code rollback} field defined.
     *
     * <p>Reports a descriptive error if this is a simple (non-multi-step) template, or if the
     * index is out of bounds.</p>
     *
     * @param stepIndex 0-based index of the step to check
     * @return this validator for chaining
     */
    public TemplateBasedChangeValidator hasRollbackForStep(int stepIndex) {
        addAssertion(() -> {
            if (!isMultiStep()) {
                return ChangeValidatorResult.error(
                        "hasRollbackForStep: this is a simple template (no 'steps' list found); "
                                + "hasRollbackForStep is only applicable to multi-step templates");
            }
            List<?> steps = (List<?>) content.getSteps();
            if (stepIndex < 0 || stepIndex >= steps.size()) {
                return ChangeValidatorResult.error(String.format(
                        "hasRollbackForStep: step index %d is out of bounds (template has %d steps)",
                        stepIndex, steps.size()));
            }
            Object step = steps.get(stepIndex);
            if (step instanceof Map && ((Map<?, ?>) step).get("rollback") != null) {
                return ChangeValidatorResult.OK();
            }
            return ChangeValidatorResult.error(String.format(
                    "hasRollbackForStep: step %d is missing a 'rollback' field", stepIndex));
        });
        return this;
    }
}
