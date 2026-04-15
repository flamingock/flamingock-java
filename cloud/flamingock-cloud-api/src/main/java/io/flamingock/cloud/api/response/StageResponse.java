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
package io.flamingock.cloud.api.response;

import java.util.List;

public  class StageResponse {
    private String name;

    private int order;

    private List<ChangeResponse> changes;

    public StageResponse() {
    }

    public StageResponse(String name, int order, List<ChangeResponse> changes) {
        this.name = name;
        this.order = order;
        this.changes = changes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<ChangeResponse> getChanges() {
        return changes;
    }

    public void setChanges(List<ChangeResponse> changes) {
        this.changes = changes;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageResponse that = (StageResponse) o;
        return order == that.order
                && java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(changes, that.changes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, order, changes);
    }
}
