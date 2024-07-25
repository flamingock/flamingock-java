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

package io.flamingock.core.task.executable.template;

import io.flamingock.template.annotations.TemplateConfigSetter;
import io.flamingock.template.annotations.TemplateConfigValidator;
import io.flamingock.template.annotations.TemplateExecution;
import io.flamingock.template.annotations.TemplateRollbackExecution;
import io.flamingock.core.engine.audit.writer.AuditEntryStatus;
import io.flamingock.core.task.descriptor.TaskDescriptor;
import io.flamingock.core.task.descriptor.TemplatedTaskDescriptor;
import io.flamingock.core.task.executable.ExecutableTaskFactory;
import io.flamingock.core.task.navigation.navigator.StepNavigator;
import io.flamingock.core.util.ReflectionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;


/**
 * Factory for ChangeUnit classes
 */
public class TemplatedExecutableTaskFactory implements ExecutableTaskFactory {

    private static final Logger logger = LoggerFactory.getLogger(StepNavigator.class);

    @Override
    public List<TemplatedExecutableTask> extractTasks(String stageName, TaskDescriptor descriptor, AuditEntryStatus initialState) {
        if (TemplatedTaskDescriptor.class.equals(descriptor.getClass())) {
            return Collections.singletonList(getTasksFromReflection(stageName, (TemplatedTaskDescriptor) descriptor, initialState));
        }

        throw new IllegalArgumentException(String.format("%s not able to process: %s", this.getClass().getSimpleName(), descriptor.getClass().getSimpleName()));

    }

    private TemplatedExecutableTask getTasksFromReflection(String stageName,
                                                           TemplatedTaskDescriptor taskDescriptor,
                                                           AuditEntryStatus initialState) {

        Method executionMethod = ReflectionUtil.findFirstMethodAnnotated(taskDescriptor.getSourceClass(), TemplateExecution.class)
                .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Templated[%s] without %s method",
                        taskDescriptor.getSourceClass().getName(),
                        TemplateExecution.class.getSimpleName())));
        Optional<Method> rollbackMethodOpt = ReflectionUtil
                .findFirstMethodAnnotated(taskDescriptor.getSourceClass(), TemplateRollbackExecution.class);

        Optional<Method> configurationSetterOpt = ReflectionUtil
                .findFirstMethodAnnotated(taskDescriptor.getSourceClass(), TemplateConfigSetter.class);


        Optional<Method> configurationValidatorOpt = ReflectionUtil
                .findFirstMethodAnnotated(taskDescriptor.getSourceClass(), TemplateConfigValidator.class);


        return new TemplatedExecutableTask(
                stageName,
                taskDescriptor,
                AuditEntryStatus.isRequiredExecution(initialState),
                executionMethod,
                rollbackMethodOpt.orElse(null),
                configurationSetterOpt.orElse(null),
                configurationValidatorOpt.orElse(null)
        );

    }




}