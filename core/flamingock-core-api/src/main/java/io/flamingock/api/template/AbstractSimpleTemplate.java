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
package io.flamingock.api.template;

/**
 * Abstract base class for templates with a single apply/rollback step.
 *
 * <p>Use this class when your template processes a single operation that may have
 * an optional rollback. The YAML structure for this template type is:
 *
 * <pre>{@code
 * id: create-users-table
 * template: SqlTemplate
 * apply: "CREATE TABLE users ..."
 * rollback: "DROP TABLE users"
 * }</pre>
 *
 * <p>The framework will automatically create a {@link TemplateStep} from the
 * apply/rollback fields in the YAML and inject it via {@link #setStep}.
 *
 * @param <SHARED_CONFIG> the type of shared configuration
 * @param <APPLY> the type of the apply payload
 * @param <ROLLBACK> the type of the rollback payload
 */
public abstract class AbstractSimpleTemplate<SHARED_CONFIG, APPLY, ROLLBACK>
        extends AbstractChangeTemplate<SHARED_CONFIG, APPLY, ROLLBACK> {

    protected TemplateStep<APPLY, ROLLBACK> step;

    public AbstractSimpleTemplate(Class<?>... additionalReflectiveClass) {
        super(additionalReflectiveClass);
    }

    /**
     * Sets the step containing the apply and optional rollback payloads.
     *
     * @param step the template step
     */
    public void setStep(TemplateStep<APPLY, ROLLBACK> step) {
        this.step = step;
    }


    /**
     * Convenience method to get the apply payload from the step.
     *
     * @return the apply payload, or null if no step is set
     */
    public APPLY getApply() {
        return step != null ? step.getApply() : null;
    }

    /**
     * Convenience method to get the rollback payload from the step.
     *
     * @return the rollback payload, or null if no step is set or no rollback defined
     */
    public ROLLBACK getRollback() {
        return step != null ? step.getRollback() : null;
    }

    /**
     * Checks if this template has a rollback payload defined.
     *
     * @return true if a step is set and it has a rollback payload
     */
    public boolean hasRollback() {
        return step != null && step.hasRollback();
    }
}
