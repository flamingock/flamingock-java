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
 * Represents a single step payload that holds one apply/rollback pair.
 *
 * <p>This class is used when a template-based change defines a single apply and
 * optional rollback operation directly (not using the multi-step format).</p>
 *
 * <h2>YAML Structure (SingleStep format)</h2>
 * <pre>{@code
 * id: create-users-table
 * template: SqlTemplate
 * apply: "CREATE TABLE users ..."
 * rollback: "DROP TABLE users"
 * }</pre>
 *
 * @param <APPLY> the type of the apply payload
 * @param <ROLLBACK> the type of the rollback payload
 */
public class SingleStep<APPLY, ROLLBACK> {

    private APPLY apply;
    private ROLLBACK rollback;

    public SingleStep() {
    }

    public SingleStep(APPLY apply, ROLLBACK rollback) {
        this.apply = apply;
        this.rollback = rollback;
    }

    /**
     * Returns the apply payload for this step.
     *
     * @return the apply payload (required)
     */
    public APPLY getApply() {
        return apply;
    }

    /**
     * Sets the apply payload for this step.
     *
     * @param apply the apply payload
     */
    public void setApply(APPLY apply) {
        this.apply = apply;
    }

    /**
     * Returns the rollback payload for this step.
     *
     * @return the rollback payload, or null if no rollback is defined
     */
    public ROLLBACK getRollback() {
        return rollback;
    }

    /**
     * Sets the rollback payload for this step.
     *
     * @param rollback the rollback payload (optional)
     */
    public void setRollback(ROLLBACK rollback) {
        this.rollback = rollback;
    }

    /**
     * Checks if this step has a rollback payload defined.
     *
     * @return true if a rollback payload is defined
     */
    public boolean hasRollback() {
        return rollback != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SingleStep{");
        sb.append("apply=").append(apply);
        if (rollback != null) {
            sb.append(", rollback=").append(rollback);
        }
        sb.append('}');
        return sb.toString();
    }
}
