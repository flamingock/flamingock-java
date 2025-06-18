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

package io.flamingock.internal.common.core.task;

import java.util.Optional;
import java.util.StringJoiner;

public abstract class AbstractTaskDescriptor implements TaskDescriptor {


    protected String id;

    protected String order;

    protected String source;

    protected boolean runAlways;

    protected boolean transactional;

    protected boolean system;

    public AbstractTaskDescriptor(){}

    public AbstractTaskDescriptor(String id,
                                  String order,
                                  String source,
                                  boolean runAlways,
                                  boolean transactional,
                                  boolean system) {
        this.id = id;
        this.order = order;
        this.source = source;
        this.runAlways = runAlways;
        this.transactional = transactional;
        this.system = system;
    }
    @Override
    public String getId() {
        return id;
    }

    @Override
    public Optional<String> getOrder() {
        return Optional.ofNullable(order);
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public boolean isRunAlways() {
        return runAlways;
    }

    @Override
    public boolean isTransactional() {
        return transactional;
    }

    @Override
    public boolean isSystem() {
        return system;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOrder(String order) {
        this.order = order;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setRunAlways(boolean runAlways) {
        this.runAlways = runAlways;
    }

    public void setTransactional(boolean transactional) {
        this.transactional = transactional;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractTaskDescriptor that = (AbstractTaskDescriptor) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AbstractTaskDescriptor.class.getSimpleName() + "[", "]")
                .add("source=" + source)
                .add("sourceClass=" + getSource())
                .add("sourceName='" + getSource() + "'")
                .add("id='" + getId() + "'")
                .add("runAlways=" + isRunAlways())
                .add("transactional=" + isTransactional())
                .add("order=" + getOrder())
                .add("sortable=" + isSortable())
                .toString();
    }



}
