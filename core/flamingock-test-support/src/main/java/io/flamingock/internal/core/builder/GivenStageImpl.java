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
package io.flamingock.internal.core.builder;

import io.flamingock.internal.core.builder.change.AbstractChangeRunnerBuilder;
import java.util.ArrayList;
import java.util.List;

class GivenStageImpl implements GivenStage {

    final AbstractChangeRunnerBuilder<?, ?> builder;
    final List<Class<?>> applied = new ArrayList<>();
    final List<Class<?>> failed = new ArrayList<>();
    final List<Class<?>> rolledback = new ArrayList<>();
    boolean runCalled = false;

    GivenStageImpl(AbstractChangeRunnerBuilder<?, ?> builder) {
        this.builder = builder;
    }

    @Override
    public GivenStage andAppliedChanges(Class<?>... changes) {
        if (changes != null) {
            for (Class<?> c : changes) {
                applied.add(c);
            }
        }
        return this;
    }

    @Override
    public GivenStage andFailedChanges(Class<?>... changes) {
        if (changes != null) {
            for (Class<?> c : changes) {
                failed.add(c);
            }
        }
        return this;
    }

    @Override
    public GivenStage andRolledbackChanges(Class<?>... changes) {
        if (changes != null) {
            for (Class<?> c : changes) {
                rolledback.add(c);
            }
        }
        return this;
    }

    @Override
    public WhenStage whenRun() {
        this.runCalled = true;
        return new WhenStageImpl(this);
    }
}
