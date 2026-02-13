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
package io.flamingock.internal.core.task.loaded;

import io.flamingock.api.template.AbstractSteppableTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Loaded change for steppable templates (multiple steps).
 * Used for templates extending {@link io.flamingock.api.template.AbstractSteppableTemplate}.
 * <p>
 * The steps are converted from raw YAML data (List of Maps) to typed TemplateStep objects
 * at load time, enabling early validation and cleaner executable tasks.
 */
public class SteppableTemplateLoadedChange extends AbstractTemplateLoadedChange {

    private final List<TemplateStep<?, ?>> steps;

    SteppableTemplateLoadedChange(String changeFileName,
                                  String id,
                                  String order,
                                  String author,
                                  Class<? extends AbstractSteppableTemplate<?, ?, ?>> templateClass,
                                  Constructor<?> constructor,
                                  List<String> profiles,
                                  boolean transactional,
                                  boolean runAlways,
                                  boolean systemTask,
                                  Object configuration,
                                  List<TemplateStep<?, ?>> steps,
                                  TargetSystemDescriptor targetSystem,
                                  RecoveryDescriptor recovery) {
        super(changeFileName, id, order, author, templateClass, constructor, profiles, transactional, runAlways, systemTask, configuration, targetSystem, recovery);
        this.steps = steps;
    }

    public List<TemplateStep<?, ?>> getSteps() {
        return steps;
    }
}
