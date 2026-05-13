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
package io.flamingock.internal.common.core.response.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains error information when an execution fails.
 *
 * <p>{@code changeIds} carries the change identifiers associated with the error. It may be
 * empty (e.g., lock or validate-time failures with no specific change), a single id (most
 * stage-level failures), or several (e.g., a stage blocked by manual intervention because
 * multiple changes need recovery).
 */
public class ErrorInfo {

    private String errorType;
    private String message;
    private List<String> changeIds;
    private String stageId;

    public ErrorInfo() {
        this.changeIds = new ArrayList<>();
    }

    public ErrorInfo(String errorType, String message, List<String> changeIds, String stageId) {
        this.errorType = errorType;
        this.message = message;
        this.changeIds = changeIds != null ? changeIds : new ArrayList<>();
        this.stageId = stageId;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getChangeIds() {
        return changeIds;
    }

    public void setChangeIds(List<String> changeIds) {
        this.changeIds = changeIds != null ? changeIds : new ArrayList<>();
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    /**
     * Creates an ErrorInfo from a Throwable. Pass {@link Collections#emptyList()} when no
     * specific change is associated with the failure, or {@link Collections#singletonList(Object)}
     * for single-change cases.
     */
    public static ErrorInfo fromThrowable(Throwable throwable, List<String> changeIds, String stageId) {
        String errorType = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        return new ErrorInfo(errorType, message, changeIds, stageId);
    }
}
