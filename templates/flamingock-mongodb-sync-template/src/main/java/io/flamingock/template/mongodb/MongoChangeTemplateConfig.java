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


import io.flamingock.api.annotations.NonLockGuarded;
import io.flamingock.api.annotations.NonLockGuardedType;
import io.flamingock.api.template.ChangeTemplateConfig;
import io.flamingock.template.mongodb.model.MongoOperation;

@NonLockGuarded(NonLockGuardedType.NONE)
public class MongoChangeTemplateConfig  extends ChangeTemplateConfig<MongoOperation, MongoOperation> {
    private MongoOperation execution;
    private MongoOperation rollback;

    public MongoChangeTemplateConfig(MongoOperation execution, MongoOperation rollback) {
        super( execution, rollback);
    }

    public MongoChangeTemplateConfig() {
        super();
    }

    public MongoOperation getExecution() { return execution; }

    public void setExecution(MongoOperation execution) { this.execution = execution; }

    public MongoOperation getRollback() { return rollback; }

    public void setRollback(MongoOperation rollback) {
        this.rollback = rollback;
    }

    public void validate() {
        //TODO implement validation
        // we probably want 3 validations
        // config generic
        // and operation validation in execution and rollback
    }

    @Override
    public String toString() {
        return "MongoChangeTemplateConfig{" + "execution=" + execution +
                ", rollback=" + rollback +
                '}';
    }
}
