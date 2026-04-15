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
package io.flamingock.common.test.cloud.prototype;


import java.util.LinkedList;
import java.util.List;

public class PrototypeStage {

    private final String name;

    private final int order;

    private final List<PrototypeChange> changes;

    public PrototypeStage(String name, int order) {
        this.name = name;
        this.order = order;
        this.changes = new LinkedList<>();
    }

    public PrototypeStage addChange(String changeId,
                                  String className,
                                  String methodName,
                                  boolean transactional) {
        changes.add(new PrototypeChange(changeId, className, methodName, transactional));
        return this;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public List<PrototypeChange> getChanges() {
        return changes;
    }


}
