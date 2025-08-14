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
package io.flamingock.internal.core.engine.audit.domain;

import io.flamingock.internal.common.core.audit.AuditEntry;
import io.flamingock.internal.common.core.audit.AuditTxType;

/**
 * Holds both audit status and transaction type information for recovery decision making.
 * This data structure enables sophisticated recovery logic that considers both the execution 
 * state and the transaction characteristics of a change.
 */
public class AuditEntryInfo {
    
    private final AuditEntry.Status status;
    private final AuditTxType txType;
    private final String changeId;
    
    public AuditEntryInfo(String changeId, AuditEntry.Status status, AuditTxType txType) {
        this.changeId = changeId;
        this.status = status;
        this.txType = txType != null ? txType : AuditTxType.NON_TX;
    }
    
    public AuditEntry.Status getStatus() {
        return status;
    }
    
    public AuditTxType getTxType() {
        return txType;
    }
    
    public String getChangeId() {
        return changeId;
    }
    
    
    @Override
    public String toString() {
        return String.format("AuditEntryInfo{changeId='%s', status=%s, txType=%s}", 
                           changeId, status, txType);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditEntryInfo)) return false;
        
        AuditEntryInfo that = (AuditEntryInfo) o;
        return changeId != null ? changeId.equals(that.changeId) : that.changeId == null &&
               status == that.status &&
               txType == that.txType;
    }
    
    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + txType.hashCode();
        result = 31 * result + (changeId != null ? changeId.hashCode() : 0);
        return result;
    }
}