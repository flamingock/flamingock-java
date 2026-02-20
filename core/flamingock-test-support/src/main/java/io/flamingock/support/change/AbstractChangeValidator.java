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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Base class for Flamingock change validators.
 *
 * <p>Provides the soft-assertion engine: assertions are queued via
 * {@link #addAssertion(Supplier)} and executed together by {@link #validate()}, which
 * collects all failures and throws a single {@link AssertionError} listing every problem.</p>
 *
 * <p>Shared assertions that apply to both code-based and template-based changes
 * ({@code withId}, {@code withAuthor}, {@code withOrder}, {@code withTargetSystem},
 * {@code withRecovery}, {@code isTransactional}, {@code isNotTransactional}) are declared
 * here so that concrete subclasses inherit them without duplication.</p>
 *
 * <p>Subclasses must implement the metadata accessors ({@link #getId()},
 * {@link #getAuthor()}, etc.) to supply the values that each assertion checks.</p>
 *
 * <p>Subclasses should return {@code this} from their own assertion methods to allow
 * fluent chaining.</p>
 *
 * @param <SELF> the concrete subclass type, used to preserve the fluent return type
 * @see ChangeValidator
 */
public abstract class AbstractChangeValidator<SELF extends AbstractChangeValidator<SELF>> {

    /** Display name used in error messages (class simple name or file name). */
    protected final String displayName;

    /**
     * Order extracted from the name at construction time via {@link ChangeNamingConvention}.
     * {@code null} when the name does not follow the {@code _ORDER__Name} convention.
     */
    protected final String extractedOrder;

    private final List<Supplier<Optional<String>>> assertions = new ArrayList<>();

    protected AbstractChangeValidator(String displayName, String extractedOrder) {
        this.displayName = displayName;
        this.extractedOrder = extractedOrder;
    }

    protected abstract String getId();

    protected abstract String getAuthor();

    protected abstract boolean isTransactionalValue();

    protected abstract String getTargetSystemId();

    protected abstract RecoveryStrategy getRecovery();

    /**
     * Asserts that the change id matches the expected value.
     *
     * @param expected the expected id
     * @return this validator for chaining
     */
    public SELF withId(String expected) {
        addAssertion(() -> {
            String actual = getId();
            return actual.equals(expected)
                    ? Optional.empty()
                    : Optional.of(String.format("withId: expected \"%s\" but was \"%s\"", expected, actual));
        });
        return self();
    }

    /**
     * Asserts that the change author matches the expected value.
     *
     * @param expected the expected author
     * @return this validator for chaining
     */
    public SELF withAuthor(String expected) {
        addAssertion(() -> {
            String actual = getAuthor();
            return actual.equals(expected)
                    ? Optional.empty()
                    : Optional.of(String.format("withAuthor: expected \"%s\" but was \"%s\"", expected, actual));
        });
        return self();
    }

    /**
     * Asserts that the order extracted from the name matches the expected value.
     *
     * <p>Order is derived from the naming convention {@code _ORDER__DescriptiveName}.
     * For code-based changes the class simple name is used; for template-based changes
     * the file name (without extension) is used.</p>
     *
     * @param expected the exact expected order string (e.g. {@code "0002"}, {@code "20250101_01"})
     * @return this validator for chaining
     */
    public SELF withOrder(String expected) {
        addAssertion(() -> {
            if (extractedOrder == null) {
                return Optional.of(String.format(
                        "withOrder: could not extract order from \"%s\". "
                                + "Name must follow the _ORDER__Name convention (e.g. _0001__MyChange).",
                        displayName));
            }
            return extractedOrder.equals(expected)
                    ? Optional.empty()
                    : Optional.of(String.format(
                            "withOrder: expected \"%s\" but extracted order was \"%s\"",
                            expected, extractedOrder));
        });
        return self();
    }

    /**
     * Asserts that a target system is declared with the given id.
     *
     * @param expectedId the expected target system id
     * @return this validator for chaining
     */
    public SELF withTargetSystem(String expectedId) {
        addAssertion(() -> {
            String actual = getTargetSystemId();
            if (actual == null) {
                return Optional.of(String.format(
                        "withTargetSystem: expected target system \"%s\" but none is declared",
                        expectedId));
            }
            return actual.equals(expectedId)
                    ? Optional.empty()
                    : Optional.of(String.format(
                            "withTargetSystem: expected \"%s\" but was \"%s\"", expectedId, actual));
        });
        return self();
    }

    /**
     * Asserts that the recovery strategy matches the expected value.
     *
     * <p>When no recovery is explicitly declared, {@link RecoveryStrategy#MANUAL_INTERVENTION}
     * is assumed, consistent with the Flamingock runtime default.</p>
     *
     * @param expected the expected {@link RecoveryStrategy}
     * @return this validator for chaining
     */
    public SELF withRecovery(RecoveryStrategy expected) {
        addAssertion(() -> {
            RecoveryStrategy actual = getRecovery();
            return actual == expected
                    ? Optional.empty()
                    : Optional.of(String.format(
                            "withRecovery: expected %s but was %s", expected.name(), actual.name()));
        });
        return self();
    }

    /**
     * Asserts that the change is transactional.
     *
     * @return this validator for chaining
     */
    public SELF isTransactional() {
        addAssertion(() -> isTransactionalValue()
                ? Optional.empty()
                : Optional.of("isTransactional: expected transactional=true but was false"));
        return self();
    }

    /**
     * Asserts that the change is not transactional.
     *
     * @return this validator for chaining
     */
    public SELF isNotTransactional() {
        addAssertion(() -> !isTransactionalValue()
                ? Optional.empty()
                : Optional.of("isNotTransactional: expected transactional=false but was true"));
        return self();
    }


    /**
     * Queues an assertion to be evaluated when {@link #validate()} is called.
     *
     * @param assertion a supplier that returns an error message if the assertion fails,
     *                  or {@link Optional#empty()} if it passes
     */
    protected final void addAssertion(Supplier<Optional<String>> assertion) {
        assertions.add(assertion);
    }

    /**
     * Runs all queued assertions and throws an {@link AssertionError} if any fail.
     *
     * <p>All assertions are always evaluated; failures are collected and reported together
     * so every problem is visible in a single test run.</p>
     *
     * @throws AssertionError if one or more assertions failed, listing all failure messages
     */
    public final void validate() {
        List<String> errors = assertions.stream()
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (!errors.isEmpty()) {
            throw new AssertionError(
                    getClass().getSimpleName() + " failed for " + displayName + ":\n  - "
                            + String.join("\n  - ", errors));
        }
    }

    @SuppressWarnings("unchecked")
    private SELF self() {
        return (SELF) this;
    }
}
