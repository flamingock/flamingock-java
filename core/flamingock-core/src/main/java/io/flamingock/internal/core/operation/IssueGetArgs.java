/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.core.operation;

public class IssueGetArgs implements OperationArgs {

    private final String changeId;
    private final boolean withGuidance;

    public IssueGetArgs() {
        this(null, false);
    }

    public IssueGetArgs(String changeId, boolean withGuidance) {
        this.changeId = changeId;
        this.withGuidance = withGuidance;
    }

    /**
     * Returns the change ID to look up, or null to return the first issue.
     */
    public String getChangeId() {
        return changeId;
    }

    public boolean isWithGuidance() {
        return withGuidance;
    }
}
