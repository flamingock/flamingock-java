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
 * Interface representing a reusable change template with configuration of type {@code CONFIG}.
 *
 * <p>This interface is commonly implemented by classes that act as templates for Changes
 * where a specific configuration needs to be injected and managed independently.
 */
public interface ChangeTemplate<SHARED_CONFIG_FIELD, APPLY_FIELD, ROLLBACK_FIELD> extends ReflectionMetadataProvider {

    void setChangeId(String changeId);

    void setTransactional(boolean isTransactional);

    void setConfiguration(SHARED_CONFIG_FIELD configuration);

    /**
     * @deprecated Use {@link #setStepsPayload(List)} instead. Will be removed in a future release.
     */
    @Deprecated
    void setApplyPayload(APPLY_FIELD applyPayload);

    /**
     * @deprecated Use {@link #setStepsPayload(List)} instead. Will be removed in a future release.
     */
    @Deprecated
    void setRollbackPayload(ROLLBACK_FIELD rollbackPayload);

    void setStepsPayload(List<TemplateStep<APPLY_FIELD, ROLLBACK_FIELD>> stepsPayload);

    List<TemplateStep<APPLY_FIELD, ROLLBACK_FIELD>> getStepsPayload();

    /**
     * @deprecated Use {@link #hasMultiStep()} instead. Will be removed in a future release.
     */
    @Deprecated
    default boolean hasStepsPayload() {
        return getStepsPayload() != null && !getStepsPayload().isEmpty();
    }

    /**
     * Sets the single step payload containing one apply/rollback pair.
     *
     * @param singleStep the single step payload
     */
    void setSingleStep(SingleStep<APPLY_FIELD, ROLLBACK_FIELD> singleStep);

    /**
     * Returns the single step payload.
     *
     * @return the single step payload, or null if not set
     */
    SingleStep<APPLY_FIELD, ROLLBACK_FIELD> getSingleStep();

    /**
     * Sets the multistep payload containing a list of steps.
     *
     * @param multiStep the multistep payload
     */
    void setMultiStep(MultiStep<APPLY_FIELD, ROLLBACK_FIELD> multiStep);

    /**
     * Returns the multistep payload.
     *
     * @return the multistep payload, or null if not set
     */
    MultiStep<APPLY_FIELD, ROLLBACK_FIELD> getMultiStep();

    /**
     * Checks if this template has a single step payload.
     *
     * @return true if a single step payload is set
     */
    default boolean hasSingleStep() {
        return getSingleStep() != null;
    }

    /**
     * Checks if this template has a multistep payload with at least one step.
     *
     * @return true if a multistep payload is set and not empty
     */
    default boolean hasMultiStep() {
        return getMultiStep() != null && !getMultiStep().isEmpty();
    }

    Class<SHARED_CONFIG_FIELD> getConfigurationClass();

    Class<APPLY_FIELD> getApplyPayloadClass();

    Class<ROLLBACK_FIELD> getRollbackPayloadClass();

}
