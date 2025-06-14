package io.flamingock.graalvm;

import io.flamingock.core.api.template.AbstractChangeTemplate;
import io.flamingock.core.api.template.ChangeTemplate;
import io.flamingock.core.api.template.ChangeTemplateConfig;
import io.flamingock.core.api.template.TemplateFactory;
import io.flamingock.importer.ImporterAdapter;
import io.flamingock.importer.adapter.ImporterAdapterFactory;
import io.flamingock.internal.core.pipeline.LoadedStage;
import io.flamingock.internal.core.pipeline.Pipeline;
import io.flamingock.core.preview.PreviewMethod;
import io.flamingock.core.preview.PreviewPipeline;
import io.flamingock.core.preview.PreviewStage;
import io.flamingock.core.system.SystemModule;
import io.flamingock.core.task.AbstractTaskDescriptor;
import io.flamingock.core.task.TaskDescriptor;
import io.flamingock.internal.core.task.loaded.AbstractLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.AbstractLoadedTask;
import io.flamingock.internal.core.task.loaded.AbstractReflectionLoadedTask;
import io.flamingock.internal.core.task.loaded.CodeLoadedChangeUnit;
import io.flamingock.internal.core.task.loaded.TemplateLoadedChangeUnit;
import io.flamingock.core.preview.CodePreviewChangeUnit;
import io.flamingock.core.preview.TemplatePreviewChangeUnit;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.nio.charset.CoderResult;
import java.util.List;
import java.util.ServiceLoader;


public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        logger.startProcess("GraalVM classes registration");
        registerInternalClasses();
        registerTemplates();
        registerModules();
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
        registerClass(ChangeTemplateConfig.class);
        registerClass(TemplatePreviewChangeUnit.class.getName());

        //Loaded
        registerClass(Pipeline.class.getName());
        registerClass(LoadedStage.class.getName());
        registerClass(AbstractLoadedTask.class.getName());
        registerClass(AbstractReflectionLoadedTask.class.getName());
        registerClass(AbstractLoadedChangeUnit.class.getName());
        registerClass(CodeLoadedChangeUnit.class.getName());
        registerClass(TemplateLoadedChangeUnit.class.getName());

        //Importer adapter
        registerClass(ImporterAdapterFactory.getImporterAdapter().getClass());

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
        registerClass(TemplateFactory.class);
        registerClass(ChangeTemplate.class);
        registerClass(AbstractChangeTemplate.class);
        for (ChangeTemplate<?> template : ServiceLoader.load(ChangeTemplate.class)) {
            registerClass(template.getClass());
            template.getReflectiveClasses().forEach(RegistrationFeature::registerClass);
        }
        logger.completedRegistration("templates");
    }

    private void registerModules() {
        logger.startRegistration("system modules");
        for (SystemModule systemModule : ServiceLoader.load(SystemModule.class)) {
            PreviewStage previewStage = systemModule.getStage();
            //Only registers code-base change units classes
            //template-base change units not needed because Template classes
            //already registered with registerTemplates()
            previewStage.getTasks()
                    .stream()
                    .filter(task -> CodePreviewChangeUnit.class.isAssignableFrom(task.getClass()))
                    .map(task -> (CodePreviewChangeUnit) task)
                    .map(AbstractTaskDescriptor::getSource)
                    .forEach(RegistrationFeature::registerClass);
            registerClass(systemModule.getClass());
        }
        logger.completedRegistration("system modules");
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
