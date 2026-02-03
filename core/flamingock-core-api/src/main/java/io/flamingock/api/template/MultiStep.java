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

import java.util.List;

/**
 * Represents a multistep payload that holds a list of {@link TemplateStep} objects.
 *
 * <p>This class is used when a template-based change defines multiple steps,
 * each with its own apply and optional rollback operation.</p>
 *
 * <h2>YAML Structure (MultiStep format)</h2>
 * <pre>{@code
 * id: create-orders-collection
 * template: MongoChangeTemplate
 * steps:
 *   - apply:
 *       type: createCollection
 *       collection: orders
 *     rollback:
 *       type: dropCollection
 *       collection: orders
 *   - apply:
 *       type: insert
 *       collection: orders
 *       parameters:
 *         documents:
 *           - orderId: "ORD-001"
 *     rollback:
 *       type: delete
 *       collection: orders
 *       parameters:
 *         filter: {}
 * }</pre>
 *
 * <h2>Rollback Behavior</h2>
 * <ul>
 *   <li>When a step fails, all previously successful steps are rolled back in reverse order</li>
 *   <li>Steps without rollback operations are skipped during rollback</li>
 *   <li>Rollback errors are logged but don't stop the rollback process</li>
 * </ul>
 *
 * @param <APPLY> the type of the apply payload
 * @param <ROLLBACK> the type of the rollback payload
 */
public class MultiStep<APPLY, ROLLBACK> {

    private List<TemplateStep<APPLY, ROLLBACK>> steps;

    public MultiStep() {
    }

    public MultiStep(List<TemplateStep<APPLY, ROLLBACK>> steps) {
        this.steps = steps;
    }

    /**
     * Returns the list of steps in this multi-step payload.
     *
     * @return the list of steps
     */
    public List<TemplateStep<APPLY, ROLLBACK>> getSteps() {
        return steps;
    }

    /**
     * Sets the list of steps for this multi-step payload.
     *
     * @param steps the list of steps
     */
    public void setSteps(List<TemplateStep<APPLY, ROLLBACK>> steps) {
        this.steps = steps;
    }

    /**
     * Checks if this multi-step payload is empty.
     *
     * @return true if steps is null or empty
     */
    public boolean isEmpty() {
        return steps == null || steps.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("MultiStep{");
        sb.append("steps=").append(steps);
        sb.append('}');
        return sb.toString();
    }
}
