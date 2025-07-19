package io.flamingock.graalvm;

import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.importer.ImporterTemplateFactory;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.CodePreviewChangeUnit;
import io.flamingock.internal.common.core.preview.PreviewMethod;
import io.flamingock.internal.common.core.preview.PreviewPipeline;
import io.flamingock.internal.common.core.preview.PreviewStage;
import io.flamingock.internal.common.core.preview.TemplatePreviewChangeUnit;
import io.flamingock.internal.common.core.task.AbstractTaskDescriptor;
import io.flamingock.internal.common.core.task.TaskDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.task.loaded.AbstractLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.internal.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChangeUnit;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.slf4j.LoggerFactory;

import java.nio.charset.CoderResult;
import java.util.List;


public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

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

    private static void registerInternalClasses() {
        logger.startRegistrationProcess("internal classes");

        registerClassForReflection(TaskDescriptor.class.getName());
        registerClassForReflection(AbstractTaskDescriptor.class.getName());

        //preview
        registerClassForReflection(PreviewPipeline.class.getName());
        registerClassForReflection(PreviewStage.class.getName());
        registerClassForReflection(CodePreviewChangeUnit.class.getName());
        registerClassForReflection(PreviewMethod.class);
        registerClassForReflection(TemplatePreviewChangeUnit.class.getName());
        registerClassForReflection(FlamingockMetadata.class.getName());

        //Loaded
        registerClassForReflection(LoadedPipeline.class.getName());
        registerClassForReflection(AbstractLoadedStage.class.getName());
        registerClassForReflection(AbstractLoadedTask.class.getName());
        registerClassForReflection(AbstractReflectionLoadedTask.class.getName());
        registerClassForReflection(AbstractLoadedChangeUnit.class.getName());
        registerClassForReflection(CodeLoadedChangeUnit.class.getName());
        registerClassForReflection(TemplateLoadedChangeUnit.class.getName());

        //others
        registerClassForReflection(CoderResult.class.getName());
        registerClassForReflection(ImporterTemplateFactory.class.getName());


        logger.completedRegistrationProcess("internal classes");
    }

    private static void initializeInternalClassesAtBuildTime() {
        logger.startInitializationProcess("internal classes");
        initializeClassAtBuildTime(CodeLoadedChangeUnit.class);
        initializeClassAtBuildTime(AbstractLoadedChangeUnit.class);
        initializeClassAtBuildTime(TemplateLoadedChangeUnit.class);
        initializeClassAtBuildTime(ChangeTemplateManager.class);
        initializeClassAtBuildTime(ImporterTemplateFactory.class);
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

    private void registerTemplates() {
        logger.startRegistrationProcess("templates");
        registerClassForReflection(ChangeTemplateManager.class);
        registerClassForReflection(ChangeTemplate.class);
        registerClassForReflection(AbstractChangeTemplate.class);
        ChangeTemplateManager.getTemplates().forEach(template -> {
            registerClassForReflection(template.getClass());
            template.getReflectiveClasses().forEach(RegistrationFeature::registerClassForReflection);
        });

        logger.completedRegistrationProcess("templates");
    }

    private static void registerClassForReflection(String className) {
        try {
            registerClassForReflection(Class.forName(className));
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


}
