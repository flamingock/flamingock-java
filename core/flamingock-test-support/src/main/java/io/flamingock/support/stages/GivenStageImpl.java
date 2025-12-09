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
package io.flamingock.support.stages;

import io.flamingock.internal.core.builder.BuilderAccessor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GivenStageImpl implements GivenStage {

    private final BuilderAccessor builderAccessor;
    private final List<Class<?>> applied = new ArrayList<>();
    private final List<Class<?>> failed = new ArrayList<>();
    private final List<Class<?>> rolledBack = new ArrayList<>();

    public GivenStageImpl(BuilderAccessor builderAccessor) {
        this.builderAccessor = builderAccessor;
    }

    @Override
    public GivenStage andAppliedChanges(Class<?>... changes) {
        if (changes != null) {
            applied.addAll(Arrays.asList(changes));
        }
        return this;
    }

    @Override
    public GivenStage andFailedChanges(Class<?>... changes) {
        if (changes != null) {
            failed.addAll(Arrays.asList(changes));
        }
        return this;
    }

    @Override
    public GivenStage andRolledBackChanges(Class<?>... changes) {
        if (changes != null) {
            rolledBack.addAll(Arrays.asList(changes));
        }
        return this;
    }

    @Override
    public WhenStage whenRun() {
        return new WhenStageImpl(builderAccessor);
    }
}
