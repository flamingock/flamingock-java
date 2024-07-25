/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.task.navigation.step;

public interface SuccessableStep {

    /**
     * Don't confuse with successful state. It may be a failed step, but its execution was successful.
     * For example, a ManualRolledBackStep. The rollback execution was successful, but it represents a failed step, as the
     * execution failed, and it required rollback.
     *
     * @return if the actual step has been successful
     */
    boolean isSuccessStep();
}
