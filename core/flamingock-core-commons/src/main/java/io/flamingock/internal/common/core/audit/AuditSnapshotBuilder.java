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
package io.flamingock.internal.common.core.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuditSnapshotBuilder {
    private final Map<String, AuditEntry> entryMap = new LinkedHashMap<>();

    public void addEntry(AuditEntry newEntry) {
        if(!entryMap.containsKey(newEntry.getTaskId())) {
            entryMap.put(newEntry.getTaskId(), newEntry);
        } else {
            AuditEntry currentEntry = entryMap.get(newEntry.getTaskId());
            AuditEntry winner = AuditEntry.getMostRelevant(currentEntry, newEntry);
            if(winner.equals(newEntry)) {
                //we need to remove it first because LinkedHashMap keeps the original key position,
                //and in case to replace it, we want to update the position
                entryMap.remove(currentEntry.getTaskId());
                entryMap.put(newEntry.getTaskId(), winner);
            }
        }
    }

    public Map<String, AuditEntry> buildMap() {
        return Collections.unmodifiableMap(entryMap);
    }

    public List<AuditEntry> buildList() {
        return Collections.unmodifiableList(new ArrayList<>(entryMap.values()));
    }

}



