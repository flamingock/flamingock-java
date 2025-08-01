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
 * Interface representing a reusable change template with configuration of type {@code CONFIG}.
 * <p>
 * This interface is commonly implemented by classes that act as templates for Change Units
 * where a specific configuration needs to be injected and managed independently.
 */
public interface ChangeTemplate<SHARED_CONFIG, EXECUTION, ROLLBACK> extends ReflectionMetadataProvider {

    void setChangeId(String changeId);

    void setTransactional(boolean isTransactional);

    void setConfiguration(SHARED_CONFIG configuration);

    void setExecution(EXECUTION execution);

    void setRollback(ROLLBACK rollback);

    Class<SHARED_CONFIG> getConfigurationClass();

    Class<EXECUTION> getExecutionClass();

    Class<ROLLBACK> getRollbackClass();

}
