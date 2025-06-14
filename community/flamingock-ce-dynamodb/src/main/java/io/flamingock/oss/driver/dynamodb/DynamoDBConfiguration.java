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

package io.flamingock.oss.driver.dynamodb;

import io.flamingock.commons.utils.DynamoDBConstants;
import io.flamingock.internal.core.community.driver.DriverConfigurable;
import io.flamingock.core.context.ContextResolver;

public class DynamoDBConfiguration implements DriverConfigurable {

    private boolean autoCreate = true;
    private String auditRepositoryName = DynamoDBConstants.AUDIT_LOG_TABLE_NAME;
    private String lockRepositoryName = DynamoDBConstants.LOCK_TABLE_NAME;
    private long readCapacityUnits = 5L;
    private long writeCapacityUnits = 5L;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public String getAuditRepositoryName() {
        return auditRepositoryName;
    }

    public void setAuditRepositoryName(String auditRepositoryName) {
        this.auditRepositoryName = auditRepositoryName;
    }

    public String getLockRepositoryName() {
        return lockRepositoryName;
    }

    public void setLockRepositoryName(String lockRepositoryName) {
        this.lockRepositoryName = lockRepositoryName;
    }

    public long getReadCapacityUnits() {
        return readCapacityUnits;
    }

    public void setReadCapacityUnits(long readCapacityUnits) {
        this.readCapacityUnits = readCapacityUnits;
    }

    public long getWriteCapacityUnits() {
        return writeCapacityUnits;
    }

    public void setWriteCapacityUnits(long writeCapacityUnits) {
        this.writeCapacityUnits = writeCapacityUnits;
    }

    public void mergeConfig(ContextResolver dependencyContext) {
        dependencyContext.getPropertyAs("dynamodb.autoCreate", Boolean.class)
                .ifPresent(this::setAutoCreate);
        dependencyContext.getPropertyAs("dynamodb.auditRepositoryName", String.class)
                .ifPresent(this::setAuditRepositoryName);
        dependencyContext.getPropertyAs("dynamodb.lockRepositoryName", String.class)
                .ifPresent(this::setLockRepositoryName);
        dependencyContext.getPropertyAs("dynamodb.readCapacityUnits", Long.class)
                .ifPresent(this::setReadCapacityUnits);
        dependencyContext.getPropertyAs("dynamodb.writeCapacityUnits", Long.class)
                .ifPresent(this::setWriteCapacityUnits);
    }
}
