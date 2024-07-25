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

package io.flamingock.community.internal.lock;

import io.flamingock.core.engine.lock.LockStatus;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * Type: entity class.
 *
 * @since 27/07/2014
 */
public class LockEntry {

    private final String key;

    private final LockStatus status;

    private final String owner;

    private final LocalDateTime expiresAt;

    public LockEntry(String key, LockStatus status, String owner, LocalDateTime expiresAt) {
        this.key = key;
        this.status = status;
        this.owner = owner;
        this.expiresAt = expiresAt;
    }


    /**
     * @return lock's key
     */
    public String getKey() {
        return key;
    }

    /**
     * @return lock's status
     */
    public LockStatus getStatus() {
        return status;
    }

    /**
     * @return lock's owner
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return lock's expiration time
     * @see Date
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * @param owner the owner to be checked
     * @return true if the parameter and the lock's owner are equals. False otherwise
     */
    public boolean isOwner(String owner) {
        return this.owner.equals(owner);
    }

    @Override
    public String toString() {
        return "LockEntry{" +
                "key='" + key + '\'' +
                ", status='" + status + '\'' +
                ", owner='" + owner + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}



