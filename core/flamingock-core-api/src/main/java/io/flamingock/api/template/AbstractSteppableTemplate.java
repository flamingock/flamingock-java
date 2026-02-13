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

import io.flamingock.internal.util.NotThreadSafe;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for templates with multiple steps.
 *
 * <p>Use this class when your template processes multiple operations, each with
 * its own apply and optional rollback. The YAML structure for this template type is:
 *
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
 * <p>The framework will automatically parse the steps from the YAML and inject
 * them via {@link #setSteps}.
 *
 * <p><b>Rollback Behavior:</b>
 * <ul>
 *   <li>When a step fails, all previously successful steps are rolled back in reverse order</li>
 *   <li>Steps without rollback operations are skipped during rollback</li>
 *   <li>Rollback errors are logged but don't stop the rollback process</li>
 * </ul>
 *
 * @param <SHARED_CONFIG> the type of shared configuration
 * @param <APPLY>         the type of the apply payload for each step
 * @param <ROLLBACK>      the type of the rollback payload for each step
 */
@NotThreadSafe
public abstract class AbstractSteppableTemplate<SHARED_CONFIG, APPLY, ROLLBACK>
        extends AbstractChangeTemplate<SHARED_CONFIG, APPLY, ROLLBACK> {

    private List<TemplateStep<APPLY, ROLLBACK>> steps = new ArrayList<>();
    private int atStep = -1;

    public AbstractSteppableTemplate(Class<?>... additionalReflectiveClass) {
        super(additionalReflectiveClass);
    }

    /**
     * Sets the list of steps to execute.
     *
     * @param steps the list of template steps
     */
    public final void setSteps(List<TemplateStep<APPLY, ROLLBACK>> steps) {
        this.steps = steps;
    }

    public final boolean advance() {
        if (atStep + 1 >= steps.size()) {
            return false;
        }
        atStep++;
        TemplateStep<APPLY, ROLLBACK> currentStep = steps.get(atStep);
        this.setApplyPayload(currentStep.getApply());
        return true;
    }




}
