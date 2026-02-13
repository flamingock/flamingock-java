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
 * Core interface for Flamingock change templates.
 *
 * <p>Templates enable declarative, YAML-based changes. Implement this interface
 * by extending {@link AbstractChangeTemplate} and annotating with
 * {@link io.flamingock.api.annotations.ChangeTemplate}.
 *
 * @param <SHARED_CONFIG_FIELD> shared configuration type (use {@code Void} if none)
 * @param <APPLY_FIELD> apply payload type parsed from YAML
 * @param <ROLLBACK_FIELD> rollback payload type parsed from YAML
 * @see AbstractChangeTemplate
 * @see io.flamingock.api.annotations.ChangeTemplate
 */
public interface ChangeTemplate<SHARED_CONFIG_FIELD, APPLY_FIELD, ROLLBACK_FIELD> extends ReflectionMetadataProvider {

    void setChangeId(String changeId);

    void setTransactional(boolean isTransactional);

    void setConfiguration(SHARED_CONFIG_FIELD configuration);

    void setApplyPayload(APPLY_FIELD applyPayload);

    void setRollbackPayload(ROLLBACK_FIELD rollbackPayload);

    Class<SHARED_CONFIG_FIELD> getConfigurationClass();

    Class<APPLY_FIELD> getApplyPayloadClass();

    Class<ROLLBACK_FIELD> getRollbackPayloadClass();

}
