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
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Recovery;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Fluent assertion utility for validating that a code-based change class is correctly annotated.
 *
 * <p>Reads metadata from {@code @Change}, {@code @TargetSystem}, {@code @Recovery},
 * {@code @Apply}, and {@code @Rollback} via reflection and asserts that the values match
 * expectations. All assertions are soft: they are queued on each chained call and executed
 * together by {@link #validate()}, which collects every failure into a single
 * {@link AssertionError}.</p>
 *
 * <h2>Implicit validation at construction</h2>
 * <p>{@link #of(Class)} checks eagerly that:
 * <ul>
 *   <li>The class is annotated with {@code @Change}</li>
 *   <li>The class declares at least one method annotated with {@code @Apply}</li>
 * </ul>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * ChangeValidator.of(_0002__FeedClients.class)
 *     .withId("feed-clients")
 *     .withAuthor("john.doe")
 *     .withOrder("0002")
 *     .isTransactional()
 *     .withTargetSystem("mongodb")
 *     .hasRollbackMethod()
 *     .validate();
 * }</pre>
 *
 * @see AbstractChangeValidator
 * @see io.flamingock.api.annotations.Change
 * @see io.flamingock.api.annotations.Apply
 * @see io.flamingock.api.annotations.Rollback
 */
public final class ChangeValidator extends AbstractChangeValidator<ChangeValidator> {

    private final Class<?> changeClass;
    private final Change changeAnnotation;

    /**
     * Creates a {@code ChangeValidator} for the given change class.
     *
     * <p>Validates eagerly that the class is annotated with {@code @Change} and declares
     * at least one method annotated with {@code @Apply}.</p>
     *
     * @param changeClass the change class to validate; must not be {@code null}
     * @return a new validator ready for assertion chaining
     * @throws NullPointerException     if {@code changeClass} is {@code null}
     * @throws IllegalArgumentException if {@code @Change} or {@code @Apply} is absent
     */
    public static ChangeValidator of(Class<?> changeClass) {
        return new ChangeValidator(changeClass);
    }

    private ChangeValidator(Class<?> changeClass) {
        super(
                Objects.requireNonNull(changeClass, "changeClass must not be null").getSimpleName(),
                ChangeNamingConvention.extractOrder(changeClass.getSimpleName())
        );
        this.changeClass = changeClass;

        this.changeAnnotation = changeClass.getAnnotation(Change.class);
        if (changeAnnotation == null) {
            throw new IllegalArgumentException(
                    String.format("Class [%s] must be annotated with @Change", changeClass.getName()));
        }

        boolean hasApplyMethod = Arrays.stream(changeClass.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Apply.class));
        if (!hasApplyMethod) {
            throw new IllegalArgumentException(
                    String.format("Class [%s] must declare a method annotated with @Apply", changeClass.getName()));
        }
    }

    @Override
    protected String getId() {
        return changeAnnotation.id();
    }

    @Override
    protected String getAuthor() {
        return changeAnnotation.author();
    }

    @Override
    protected boolean isTransactionalValue() {
        return changeAnnotation.transactional();
    }

    @Override
    protected String getTargetSystemId() {
        TargetSystem ts = changeClass.getAnnotation(TargetSystem.class);
        return ts != null ? ts.id() : null;
    }

    @Override
    protected RecoveryStrategy getRecovery() {
        Recovery rec = changeClass.getAnnotation(Recovery.class);
        return rec != null ? rec.strategy() : RecoveryStrategy.MANUAL_INTERVENTION;
    }

    /**
     * Asserts that the class declares a method annotated with {@code @Rollback}.
     *
     * <p>Only methods directly declared in the class are considered; inherited methods
     * are not scanned.</p>
     *
     * @return this validator for chaining
     */
    public ChangeValidator hasRollbackMethod() {
        addAssertion(() -> {
            boolean found = Arrays.stream(changeClass.getDeclaredMethods())
                    .anyMatch(m -> m.isAnnotationPresent(Rollback.class));
            return found
                    ? Optional.empty()
                    : Optional.of(String.format(
                            "hasRollbackMethod: no method annotated with @Rollback found in %s",
                            changeClass.getSimpleName()));
        });
        return this;
    }
}
