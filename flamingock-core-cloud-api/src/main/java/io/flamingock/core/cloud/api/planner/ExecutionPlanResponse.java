/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.core.cloud.api.planner;

import java.util.List;

public class ExecutionPlanResponse {

    public enum Action {
        CONTINUE, EXECUTE, AWAIT
    }


    private Action action;

    private String executionId;

    private Lock lock;

    private List<Stage> stages;

    public ExecutionPlanResponse() {
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public Lock getLock() {
        return lock;
    }

    public void setLock(Lock lock) {
        this.lock = lock;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public void setStages(List<Stage> stages) {
        this.stages = stages;
    }


    public boolean isContinue() {
        return action == Action.CONTINUE;
    }

    public Action getAction() {
        return action;
    }

    public boolean isExecute() {
        return action == Action.EXECUTE;
    }

    public boolean isAwait() {
        return action == Action.AWAIT;
    }

    public void validate() {
        if (isExecute() && executionId == null) {
            throw new RuntimeException("ExecutionPlan must contain a valid executionId");
        }
        if (isExecute() && getStages() == null) {
            throw new RuntimeException("ExecutionPlan is execute, but not body returned");
        }

        if (isAwait() && getLock() == null) {
            throw new RuntimeException("ExecutionPlan is await, but not lock information returned");
        }
    }

    public static class Stage {
        private String name;

        private int order;

        private List<Task> tasks;

        public Stage() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Task> getTasks() {
            return tasks;
        }

        public void setTasks(List<Task> tasks) {
            this.tasks = tasks;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }
    }

    public static class Task {
        private String id;
        private String ongoingStatus;

        public Task() {
        }

        public Task(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOngoingStatus() {
            return ongoingStatus;
        }

        public void setOngoingStatus(String ongoingStatus) {
            this.ongoingStatus = ongoingStatus;
        }
    }

    public static class Lock {

        private String organizationId;
        private String projectId;
        private String environment;

        private String key;

        private String owner;

        private String acquisitionId;

        private long acquiredForMillis;


        public Lock() {
        }

        public String getOrganizationId() {
            return organizationId;
        }

        public void setOrganizationId(String organizationId) {
            this.organizationId = organizationId;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getAcquisitionId() {
            return acquisitionId;
        }

        public void setAcquisitionId(String acquisitionId) {
            this.acquisitionId = acquisitionId;
        }

        public long getAcquiredForMillis() {
            return acquiredForMillis;
        }

        public void setAcquiredForMillis(long acquiredForMillis) {
            this.acquiredForMillis = acquiredForMillis;
        }
    }
}
