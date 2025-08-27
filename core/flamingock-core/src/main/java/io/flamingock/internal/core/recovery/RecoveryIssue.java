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
package io.flamingock.internal.core.recovery;

/**
 * Represents a recovery issue that requires manual intervention during pipeline execution.
 * This domain object is specifically designed for recovery scenarios and avoids the
 * inappropriate use of audit-specific objects for recovery purposes.
 */
public class RecoveryIssue {
    
    private final String changeId;
    
    public RecoveryIssue(String changeId) {
        this.changeId = changeId;
    }
    
    /**
     * Returns the unique identifier of the change that requires manual intervention.
     * 
     * @return the change ID
     */
    public String getChangeId() {
        return changeId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecoveryIssue)) return false;
        
        RecoveryIssue that = (RecoveryIssue) o;
        return changeId != null ? changeId.equals(that.changeId) : that.changeId == null;
    }
    
    @Override
    public int hashCode() {
        return changeId != null ? changeId.hashCode() : 0;
    }
    
    @Override
    public String toString() {
        return String.format("RecoveryIssue{changeId='%s'}", changeId);
    }
}