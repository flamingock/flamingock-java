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
package io.flamingock.internal.common.core.preview;

import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.beans.Transient;

public class CodePreviewChange extends AbstractPreviewTask {
    private PreviewMethod executionMethodName;
    private PreviewMethod rollbackMethodName;
    private PreviewMethod beforeExecutionMethodName;
    private PreviewMethod rollbackBeforeExecutionMethodName;

    private String sourcePackage;

    public CodePreviewChange() {
        super();
    }

    public CodePreviewChange(String id,
                             String order,
                             String author,
                             String sourceClassPath,
                             PreviewMethod executionMethodPreview,
                             PreviewMethod rollbackMethodPreview,
                             PreviewMethod beforeExecutionMethodPreview,
                             PreviewMethod rollbackBeforeExecutionMethodPreview,
                             boolean runAlways,
                             boolean transactional,
                             boolean system,
                             TargetSystemDescriptor targetSystem,
                             RecoveryDescriptor recovery) {
        super(id, order, author, sourceClassPath, runAlways, transactional, system, targetSystem, recovery);
        this.executionMethodName = executionMethodPreview;
        this.rollbackMethodName = rollbackMethodPreview;
        this.beforeExecutionMethodName = beforeExecutionMethodPreview;
        this.rollbackBeforeExecutionMethodName = rollbackBeforeExecutionMethodPreview;
        this.sourcePackage = sourceClassPath.substring(0, sourceClassPath.lastIndexOf("."));
    }

    public PreviewMethod getExecutionMethodName() {
        return executionMethodName;
    }

    public void setExecutionMethodName(PreviewMethod executionMethodName) {
        this.executionMethodName = executionMethodName;
    }

    public PreviewMethod getRollbackMethodName() {
        return rollbackMethodName;
    }

    public void setRollbackMethodName(PreviewMethod rollbackMethodName) {
        this.rollbackMethodName = rollbackMethodName;
    }

    public PreviewMethod getBeforeExecutionMethodName() {
        return beforeExecutionMethodName;
    }

    public void setBeforeExecutionMethodName(PreviewMethod beforeExecutionMethodName) {
        this.beforeExecutionMethodName = beforeExecutionMethodName;
    }

    public PreviewMethod getRollbackBeforeExecutionMethodName() {
        return rollbackBeforeExecutionMethodName;
    }

    public void setRollbackBeforeExecutionMethodName(PreviewMethod rollbackBeforeExecutionMethodName) {
        this.rollbackBeforeExecutionMethodName = rollbackBeforeExecutionMethodName;
    }

    @Transient
    public String getSourcePackage() {
        return sourcePackage;
    }

    @Override
    public String pretty() {
        String fromParent = super.pretty();
        return fromParent + String.format("\n\t\t[class: %s]", getSource());
    }

    @Override
    public String toString() {
        return "PreviewChange{" +
                ", id='" + id + '\'' +
                ", order='" + order + '\'' +
                ", author='" + author + '\'' +
                ", source='" + source + '\'' +
                ", runAlways=" + runAlways +
                ", transactional=" + transactional +
                (getTargetSystem() != null ? ", targetSystem='" + getTargetSystem().getId() + '\'' : "") +
                (getRecovery() != null ? ", recovery='" + getRecovery().getStrategy() + '\'' : "") +
                '}';
    }

}
