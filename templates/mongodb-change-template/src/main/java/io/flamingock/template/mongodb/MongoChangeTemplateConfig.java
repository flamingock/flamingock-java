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

package io.flamingock.template.mongodb;


import io.flamingock.core.api.annotations.NonLockGuarded;
import io.flamingock.core.api.annotations.NonLockGuardedType;
import io.flamingock.template.mongodb.model.MongoOperation;

import java.util.List;

@NonLockGuarded(NonLockGuardedType.NONE)
public class MongoChangeTemplateConfig  {
    private List<MongoOperation> changes;
    private List<MongoOperation> rollbacks;

    public MongoChangeTemplateConfig(List<MongoOperation> changes, List<MongoOperation> rollbacks) {
        this.changes = changes;
        this.rollbacks = rollbacks;
    }

    public MongoChangeTemplateConfig() {
        this(null, null);
    }

    public List<MongoOperation> getChanges() { return changes; }

    public void setChanges(List<MongoOperation> changes) { this.changes = changes; }

    public List<MongoOperation> getRollbacks() { return rollbacks; }

    public void setRollbacks(List<MongoOperation> rollbacks) {
        this.rollbacks = rollbacks;
    }

    public void validate() {
        //TODO implement validation
        // we probably want 3 validations
        // config generic
        // and operation validation in execution and rollback
    }

    @Override
    public String toString() {
        return "MongoChangeTemplateConfig{" + "changes=" + changes +
                ", rollbacks=" + rollbacks +
                '}';
    }
}
