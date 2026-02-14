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
 * Represents a single step in a step-based change template.
 *
 * <p>Each step contains an {@code apply} operation that executes during the forward
 * migration, and an optional {@code rollback} operation that executes if the step
 * or a subsequent step fails.</p>
 *
 * <h2>YAML Structure</h2>
 * <pre>{@code
 * steps:
 *   - apply:
 *       type: createCollection
 *       collection: users
 *     rollback:
 *       type: dropCollection
 *       collection: users
 *   - apply:
 *       type: insert
 *       collection: users
 *       parameters:
 *         documents:
 *           - name: "John"
 *     rollback:
 *       type: delete
 *       collection: users
 *       parameters:
 *         filter: {}
 * }</pre>
 *
 * <h2>Rollback Behavior</h2>
 * <ul>
 *   <li>Rollback is optional - steps without rollback are skipped during rollback</li>
 *   <li>When a step fails, all previously successful steps are rolled back in reverse order</li>
 *   <li>Rollback errors are logged but don't stop the rollback process</li>
 * </ul>
 *
 * @param <APPLY> the type of the apply payload
 * @param <ROLLBACK> the type of the rollback payload
 */
public class TemplateStep<APPLY, ROLLBACK> {

    private APPLY applyPayload;
    private ROLLBACK rollbackPayload;

    public TemplateStep() {
    }

    public TemplateStep(APPLY applyPayload, ROLLBACK rollbackPayload) {
        this.applyPayload = applyPayload;
        this.rollbackPayload = rollbackPayload;
    }

    /**
     * Returns the apply payload for this step.
     *
     * @return the apply payload (required)
     */
    public APPLY getApplyPayload() {
        return applyPayload;
    }

    /**
     * Sets the apply payload for this step.
     *
     * @param applyPayload the apply payload
     */
    public void setApplyPayload(APPLY applyPayload) {
        this.applyPayload = applyPayload;
    }

    /**
     * Returns the rollback payload for this step.
     *
     * @return the rollback payload, or null if no rollback is defined
     */
    public ROLLBACK getRollbackPayload() {
        return rollbackPayload;
    }

    /**
     * Sets the rollback payload for this step.
     *
     * @param rollbackPayload the rollback payload (optional)
     */
    public void setRollbackPayload(ROLLBACK rollbackPayload) {
        this.rollbackPayload = rollbackPayload;
    }

    /**
     * Checks if this step has a rollback payload defined.
     *
     * @return true if a rollback payload is defined
     */
    public boolean hasRollback() {
        return rollbackPayload != null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TemplateStep{");
        sb.append("apply=").append(applyPayload);
        if (rollbackPayload != null) {
            sb.append(", rollback=").append(rollbackPayload);
        }
        sb.append('}');
        return sb.toString();
    }
}
