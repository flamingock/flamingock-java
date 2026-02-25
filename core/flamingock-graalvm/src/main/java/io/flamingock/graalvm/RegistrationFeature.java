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
package io.flamingock.graalvm;

import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.*;
import io.flamingock.internal.common.core.task.AbstractTaskDescriptor;
import io.flamingock.internal.common.core.task.RecoveryDescriptor;
import io.flamingock.internal.common.core.task.TargetSystemDescriptor;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.task.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.task.loaded.CodeLoadedChange;
import io.flamingock.internal.core.task.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.core.task.loaded.MultiStepTemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.slf4j.LoggerFactory;

import java.nio.charset.CoderResult;
import java.util.List;


public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

    private static void registerInternalClasses() {
        logger.startRegistrationProcess("internal classes");

        registerClassForReflection(TaskDescriptor.class.getName());
        registerClassForReflection(AbstractTaskDescriptor.class.getName());

        //preview
        registerClassForReflection(PreviewPipeline.class.getName());
        registerClassForReflection(PreviewStage.class.getName());
        registerClassForReflection(PreviewConstructor.class.getName());
        registerClassForReflection(SystemPreviewStage.class.getName());
        registerClassForReflection(CodePreviewChange.class.getName());
        registerClassForReflection(PreviewMethod.class);
        registerClassForReflection(TemplatePreviewChange.class.getName());
        registerClassForReflection(FlamingockMetadata.class.getName());
        registerClassForReflection(TargetSystemDescriptor.class.getName());
        registerClassForReflection(RecoveryDescriptor.class.getName());

        //Loaded
        registerClassForReflection(LoadedPipeline.class.getName());
        registerClassForReflection(AbstractLoadedStage.class.getName());
        registerClassForReflection(AbstractLoadedTask.class.getName());
        registerClassForReflection(AbstractReflectionLoadedTask.class.getName());
        registerClassForReflection(AbstractLoadedChange.class.getName());
        registerClassForReflection(CodeLoadedChange.class.getName());
        registerClassForReflection(AbstractTemplateLoadedChange.class);
        registerClassForReflection(SimpleTemplateLoadedChange.class);
        registerClassForReflection(MultiStepTemplateLoadedChange.class);

        //others
        registerClassForReflection(CoderResult.class.getName());


        logger.completedRegistrationProcess("internal classes");
    }

    private static void initializeInternalClassesAtBuildTime() {
        logger.startInitializationProcess("internal classes");
        initializeClassAtBuildTime(CodeLoadedChange.class);
        initializeClassAtBuildTime(AbstractLoadedChange.class);
        initializeClassAtBuildTime(AbstractTemplateLoadedChange.class);
        initializeClassAtBuildTime(SimpleTemplateLoadedChange.class);
        initializeClassAtBuildTime(MultiStepTemplateLoadedChange.class);
        initializeClassAtBuildTime(ChangeTemplateManager.class);
        initializeClassAtBuildTime(RecoveryDescriptor.class);
        initializeClassAtBuildTime(FlamingockLoggerFactory.class);
        logger.completeInitializationProcess("internal classes");
    }

    private static void initializeExternalClassesAtBuildTime() {
        logger.startInitializationProcess("external classes");
        initializeClassAtBuildTime(LoggerFactory.class);
        logger.completeInitializationProcess("external classes");
    }

    private static void registerUserClasses() {
        logger.startRegistrationProcess("user classes");
        List<String> classesToRegister = FileUtil.getClassesForRegistration();
        classesToRegister.forEach(RegistrationFeature::registerClassForReflection);
        logger.completedRegistrationProcess("user classes");
    }

    private static void registerClassForReflection(String className) {
        try {
            registerClassForReflection(Class.forName(className));
            initializeClassAtBuildTime(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void registerClassForReflection(Class<?> clazz) {
        logger.startClassRegistration(clazz);
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getFields());
        RuntimeReflection.register(clazz.getDeclaredFields());
        RuntimeReflection.register(clazz.getDeclaredConstructors());
        RuntimeReflection.register(clazz.getDeclaredMethods());
    }

    private static void initializeClassAtBuildTime(Class<?> clazz) {
        logger.startClassInitialization(clazz);
        RuntimeClassInitialization.initializeAtBuildTime(clazz);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        logger.startProcess("GraalVM classes registration and initialization");
        initializeInternalClassesAtBuildTime();
        initializeExternalClassesAtBuildTime();
        registerInternalClasses();
        registerTemplates();
        registerUserClasses();
        logger.finishedProcess("GraalVM classes registration and initialization");
    }

    private void registerTemplates() {
        logger.startRegistrationProcess("templates");
        registerClassForReflection(ChangeTemplateManager.class);
        registerClassForReflection(ChangeTemplate.class);
        registerClassForReflection(AbstractChangeTemplate.class);
        registerClassForReflection(TemplateStep.class);
        ChangeTemplateManager.getRawTemplates().forEach(template -> {
            registerClassForReflection(template.getClass());
            template.getReflectiveClasses().forEach(RegistrationFeature::registerClassForReflection);
        });

        logger.completedRegistrationProcess("templates");
    }


}
