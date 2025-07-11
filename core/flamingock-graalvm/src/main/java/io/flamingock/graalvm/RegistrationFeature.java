package io.flamingock.graalvm;

import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.ChangeTemplate;
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

import java.nio.charset.CoderResult;
import java.util.List;


public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        logger.startProcess("GraalVM classes registration");
        registerInternalClasses();
        registerTemplates();
        registerUserClasses();
        logger.finishedProcess("GraalVM classes registration");
    }

    private static void registerInternalClasses() {
        logger.startRegistration("internal classes");

        registerClass(TaskDescriptor.class.getName());
        registerClass(AbstractTaskDescriptor.class.getName());

        //preview
        registerClass(PreviewPipeline.class.getName());
        registerClass(PreviewStage.class.getName());
        registerClass(CodePreviewChangeUnit.class.getName());
        registerClass(PreviewMethod.class);
        registerClass(TemplatePreviewChangeUnit.class.getName());
        registerClass(FlamingockMetadata.class.getName());

        //Loaded
        registerClass(LoadedPipeline.class.getName());
        registerClass(AbstractLoadedStage.class.getName());
        registerClass(AbstractLoadedTask.class.getName());
        registerClass(AbstractReflectionLoadedTask.class.getName());
        registerClass(AbstractLoadedChangeUnit.class.getName());
        registerClass(CodeLoadedChangeUnit.class.getName());
        registerClass(TemplateLoadedChangeUnit.class.getName());

        //others
        registerClass(CoderResult.class.getName());


        logger.completedRegistration("internal classes");
    }


    private static void registerUserClasses() {
        logger.startRegistration("user classes");
        List<String> classesToRegister = FileUtil.getClassesForRegistration();
        classesToRegister.forEach(RegistrationFeature::registerClass);
        logger.completedRegistration("user classes");
    }

    private void registerTemplates() {
        logger.startRegistration("templates");
        registerClass(ChangeTemplateManager.class);
        registerClass(ChangeTemplate.class);
        registerClass(AbstractChangeTemplate.class);
        ChangeTemplateManager.getTemplates().forEach(template -> {
            registerClass(template.getClass());
            template.getReflectiveClasses().forEach(RegistrationFeature::registerClass);
        });

        logger.completedRegistration("templates");
    }

    private static void registerClass(String className) {
        try {
            registerClass(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void registerClass(Class<?> clazz) {
        logger.initClassRegistration(clazz);
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getFields());
        RuntimeReflection.register(clazz.getDeclaredFields());
        RuntimeReflection.register(clazz.getDeclaredConstructors());
        RuntimeReflection.register(clazz.getDeclaredMethods());
    }

}
