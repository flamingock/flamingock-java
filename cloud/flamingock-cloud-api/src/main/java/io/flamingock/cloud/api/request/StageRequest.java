/*
 * Copyright 2023 Flamingock (https://www.flamingock.io)
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
package io.flamingock.cloud.api.request;

import java.util.List;

public class StageRequest {
    private String name;

    private int order;

    private List<ChangeRequest> tasks;

    public StageRequest() {
    }

    public StageRequest(String name, int order, List<ChangeRequest> tasks) {
        this.name = name;
        this.order = order;
        this.tasks = tasks;
    }

    public String getName() {
        return name;
    }

    public int getOrder() {
        return order;
    }

    public List<ChangeRequest> getTasks() {
        return tasks;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public void setTasks(List<ChangeRequest> tasks) {
        this.tasks = tasks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StageRequest that = (StageRequest) o;
        return order == that.order
                && java.util.Objects.equals(name, that.name)
                && java.util.Objects.equals(tasks, that.tasks);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, order, tasks);
    }
}
