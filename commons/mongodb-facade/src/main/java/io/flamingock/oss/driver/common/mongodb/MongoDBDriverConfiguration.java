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

package io.flamingock.oss.driver.common.mongodb;

import io.flamingock.core.community.driver.DriverConfigurable;

public class MongoDBDriverConfiguration implements DriverConfigurable {

    public final static String DEFAULT_AUDIT_REPOSITORY_NAME = "flamingockAuditLogs";
    public final static String DEFAULT_LOCK_REPOSITORY_NAME = "flamingockLock";

    private boolean autoCreate = true;
    private String auditRepositoryName = DEFAULT_AUDIT_REPOSITORY_NAME;
    private String lockRepositoryName = DEFAULT_LOCK_REPOSITORY_NAME;

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

}
