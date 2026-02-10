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

/**
 * Contains error information when an execution fails.
 */
public class ErrorInfo {

    private String errorType;
    private String message;
    private String changeId;
    private String stageId;

    public ErrorInfo() {
    }

    public ErrorInfo(String errorType, String message, String changeId, String stageId) {
        this.errorType = errorType;
        this.message = message;
        this.changeId = changeId;
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

    public String getChangeId() {
        return changeId;
    }

    public void setChangeId(String changeId) {
        this.changeId = changeId;
    }

    public String getStageId() {
        return stageId;
    }

    public void setStageId(String stageId) {
        this.stageId = stageId;
    }

    /**
     * Creates an ErrorInfo from a Throwable.
     */
    public static ErrorInfo fromThrowable(Throwable throwable, String changeId, String stageId) {
        String errorType = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        return new ErrorInfo(errorType, message, changeId, stageId);
    }
}
