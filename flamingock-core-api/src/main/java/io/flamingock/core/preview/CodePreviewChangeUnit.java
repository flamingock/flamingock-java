/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.core.preview;

public class CodePreviewChangeUnit extends AbstractCodePreviewTask {

    protected boolean isNewChangeUnit;


    public CodePreviewChangeUnit() {
        super();
    }

    public CodePreviewChangeUnit(String id,
                                 String order,
                                 String sourceClassPath,
                                 PreviewMethod executionMethodPreview,
                                 PreviewMethod rollbackMethodPreview,
                                 boolean runAlways,
                                 boolean transactional,
                                 boolean isNewChangeUnit,
                                 boolean system) {
        super(id, order, sourceClassPath, executionMethodPreview, rollbackMethodPreview, runAlways, transactional, system);
        this.isNewChangeUnit = isNewChangeUnit;
    }


    public boolean isNewChangeUnit() {
        return isNewChangeUnit;
    }

    public void setNewChangeUnit(boolean newChangeUnit) {
        isNewChangeUnit = newChangeUnit;
    }


    @Override
    public String pretty() {
        String fromParent = super.pretty();
        return fromParent + String.format("\n\t\t[class: %s]", getSource());
    }

    @Override
    public String toString() {
        return "PreviewChangeUnit{" + "isNewChangeUnit=" + isNewChangeUnit +
                ", id='" + id + '\'' +
                ", order='" + order + '\'' +
                ", source='" + source + '\'' +
                ", runAlways=" + runAlways +
                ", transactional=" + transactional +
                '}';
    }

}
