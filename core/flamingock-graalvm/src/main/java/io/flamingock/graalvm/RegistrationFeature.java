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

import io.flamingock.graalvm.MetadataModuleInfoLoader.MetadataModuleInfo;
import io.flamingock.internal.util.ReflectionUtil;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;


/**
 * GraalVM Feature that wires Flamingock for native-image build at compile time.
 *
 * <p>This class deliberately keeps zero compile-time dependencies on Flamingock's runtime
 * classes. Every Flamingock class it touches is referenced by fully-qualified name and
 * loaded via {@link Class#forName(String, boolean, ClassLoader)} with {@code initialize=false}.
 * The reasons:
 * <ul>
 *   <li>Allows {@code flamingock-graalvm} to compile against {@code flamingock-general-util}
 *       only, regardless of which Flamingock edition (community / cloud / etc.) the user
 *       brings to the build.</li>
 *   <li>Keeps the Feature class's {@code <clinit>} as light as possible — there are no
 *       static field initializers that could pull problematic transitive classes (slf4j,
 *       logback, etc.) into the build-time image heap.</li>
 *   <li>{@code Class.forName(name, false, loader)} returns a {@code Class<?>} reference
 *       without firing the target's {@code <clinit>}; subsequent
 *       {@link RuntimeReflection#register} calls also skip initialization. So none of the
 *       registered classes get build-time-initialized through this Feature.</li>
 * </ul>
 */
public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

    private static final String CHANGE_TEMPLATE_INTERFACE_FQN =
            "io.flamingock.api.template.ChangeTemplate";
    private static final String ABSTRACT_CHANGE_TEMPLATE_FQN =
            "io.flamingock.api.template.AbstractChangeTemplate";
    private static final String CHANGE_TEMPLATE_ANNOTATION_FQN =
            "io.flamingock.api.annotations.ChangeTemplate";

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        logger.startProcess("GraalVM classes registration and initialization");
        registerInternalClasses();
        registerCloudApiClasses();
        registerTemplates();
        registerProviderInfo();
        logger.finishedProcess("GraalVM classes registration and initialization");
    }

    private static void registerInternalClasses() {
        logger.startRegistrationProcess("internal classes");

        registerClassForReflection("io.flamingock.internal.common.core.change.ChangeDescriptor");
        registerClassForReflection("io.flamingock.internal.common.core.change.AbstractChangeDescriptor");

        //preview
        registerClassForReflection("io.flamingock.internal.common.core.preview.PreviewPipeline");
        registerClassForReflection("io.flamingock.internal.common.core.preview.PreviewStage");
        registerClassForReflection("io.flamingock.internal.common.core.preview.PreviewConstructor");
        registerClassForReflection("io.flamingock.internal.common.core.preview.SystemPreviewStage");
        registerClassForReflection("io.flamingock.internal.common.core.preview.CodePreviewChange");
        registerClassForReflection("io.flamingock.internal.common.core.preview.PreviewMethod");
        registerClassForReflection("io.flamingock.internal.common.core.preview.TemplatePreviewChange");
        registerClassForReflection("io.flamingock.internal.common.core.metadata.FlamingockMetadata");
        registerClassForReflection("io.flamingock.internal.common.core.change.TargetSystemDescriptor");
        registerClassForReflection("io.flamingock.internal.common.core.change.RecoveryDescriptor");

        //Loaded
        registerClassForReflection("io.flamingock.internal.core.pipeline.loaded.LoadedPipeline");
        registerClassForReflection("io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.AbstractLoadedChange");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.CodeLoadedChange");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.SimpleTemplateLoadedChange");
        registerClassForReflection("io.flamingock.internal.core.change.loaded.MultiStepTemplateLoadedChange");

        //others
        registerClassForReflection("java.nio.charset.CoderResult");


        logger.completedRegistrationProcess("internal classes");
    }

    /**
     * Walk the {@code FlamingockMetadataProvider} SPI once and do both registration passes
     * off the resulting collection: per-module metadata.json files become runtime resources,
     * per-module reflection classes become reflection-registered. The SPI service file and
     * the generated provider impl classes are intentionally not registered here — GraalVM's
     * automatic {@code ServiceLoader.load(...)} detection (triggered by the static call in
     * {@code MetadataLoader.loadAll()}) handles both.
     */
    private static void registerProviderInfo() {
        logger.startRegistrationProcess("provider info");
        List<MetadataModuleInfo> providers = MetadataModuleInfoLoader.load();

        logger.startRegistrationProcess("metadata resources");
        providers.forEach(p ->
                RuntimeResourceAccess.addResource(p.module(), p.metadataResourcePath()));
        logger.completedRegistrationProcess("metadata resources");

        logger.startRegistrationProcess("user classes");
        providers.stream()
                .flatMap(p -> p.reflectionClasses().stream())
                .forEach(RegistrationFeature::registerClassForReflection);
        logger.completedRegistrationProcess("user classes");

        logger.completedRegistrationProcess("provider info");
    }

    private static void registerClassForReflection(String className) {
        try {
            registerClassForReflection(getClass(className));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }



    /**
     * Optionally register a class by name — silently skips if it is not on the classpath.
     * Used for modules that may or may not be present at native-image time (e.g. cloud DTOs
     * are absent in community-only builds).
     */
    private static void tryRegisterClassForReflection(String className) {
        try {
            registerClassForReflection(getClass(className));
        } catch (ClassNotFoundException ignored) {
            // module not on classpath — nothing to register
        }
    }

    /**
     * Cloud HTTP DTOs serialised/deserialised by Jackson in HttpAuthClient and the cloud
     * audit/lock/plan clients. The HTTP path uses Apache HttpClient + raw Jackson (not
     * Spring's HttpMessageConverters), so Spring Boot AOT does not see these classes and
     * they need explicit reflection hints. Without this, Jackson can't introspect getters
     * in native and serialises empty bodies (FAIL_ON_EMPTY_BEANS is disabled in
     * JsonObjectMapper), which the server then NPEs on.
     */
    private static void registerCloudApiClasses() {
        logger.startRegistrationProcess("cloud api classes");
        // request
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.TokenExchangeRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.ClientSubmissionRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.StageRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.ChangeRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.ExecutionPlanRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.AuditEntryRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.LockAcquisitionRequest");
        tryRegisterClassForReflection("io.flamingock.cloud.api.request.LockExtensionRequest");
        // response
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.TokenExchangeResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.ExecutionPlanResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.ExecutionDetailResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.ExecutionSummaryResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.PipelineResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.StageResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.ChangeResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.AuditEntryResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.LockResponse");
        tryRegisterClassForReflection("io.flamingock.cloud.api.response.LockInfoResponse");
        logger.completedRegistrationProcess("cloud api classes");
    }

    private static void registerClassForReflection(Class<?> clazz) {
        logger.startClassRegistration(clazz);
        RuntimeReflection.register(clazz);
        RuntimeReflection.register(clazz.getFields());
        RuntimeReflection.register(clazz.getDeclaredFields());
        RuntimeReflection.register(clazz.getDeclaredConstructors());
        RuntimeReflection.register(clazz.getDeclaredMethods());
    }

    private void registerTemplates() {
        logger.startRegistrationProcess("templates");
        // Static infrastructure registrations — Flamingock-internal classes with clean
        // <clinit>; safe to register at build time. Class.forName(name, false, loader) inside
        // registerClassForReflection(String) loads the Class<?> reference without firing
        // <clinit>, so this is also a no-init operation.
        registerClassForReflection("io.flamingock.internal.common.core.template.ChangeTemplateManager");
        registerClassForReflection(CHANGE_TEMPLATE_INTERFACE_FQN);
        registerClassForReflection(ABSTRACT_CHANGE_TEMPLATE_FQN);
        registerClassForReflection("io.flamingock.api.template.TemplateStep");
        registerClassForReflection("io.flamingock.api.template.TemplateField");
        registerClassForReflection("io.flamingock.api.template.TemplatePayload");
        registerClassForReflection("io.flamingock.api.template.wrappers.TemplateString");
        registerClassForReflection("io.flamingock.api.template.wrappers.TemplateVoid");

        // Per-template registration. Critical: never instantiate the template class at build
        // time. Templates routinely declare `static final Logger log = LoggerFactory.getLogger(
        // ...)` in their <clinit>, which would pull SLF4J/Logback into the build-time image
        // heap and break native-image. ServiceLoader.Provider::type returns the Class<?> ref
        // without ever calling the provider's constructor — class is loaded but NOT
        // initialized. The Class<?> overload of registerClassForReflection used below also
        // skips initialization; getAnnotation() and ReflectionUtil.resolveTypeArgumentsAsClasses
        // are pre-init operations too.
        Class<?> changeTemplateInterface;
        try {
            changeTemplateInterface = getClass(CHANGE_TEMPLATE_INTERFACE_FQN);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        ServiceLoader<?> templateProviders = ServiceLoader.load((Class) changeTemplateInterface,
                RegistrationFeature.class.getClassLoader());
        templateProviders.stream()
                .map(provider -> (Class<?>) provider.type())
                .forEach(this::registerTemplateClass);

        logger.completedRegistrationProcess("templates");
    }

    /**
     * Register a single template class for reflection without initializing it. Reads
     * everything we need (generic type args + {@code @ChangeTemplate.reflectiveClasses})
     * from class-level metadata only, via reflection on classes loaded with
     * {@code initialize=false}. {@link ReflectiveOperationException} covers
     * {@link ClassNotFoundException}, {@link NoSuchMethodException},
     * {@link IllegalAccessException}, and
     * {@link java.lang.reflect.InvocationTargetException} — any of which here indicates a
     * misconfigured Flamingock classpath at native-image build time and warrants failing
     * the build.
     */
    private void registerTemplateClass(Class<?> templateClass) {
        // Class<?> overload — does not initialize the class.
        registerClassForReflection(templateClass);

        try {
            // Generic type args (configuration / apply / rollback payload classes) — same
            // resolution AbstractChangeTemplate's runtime constructor uses, but here it runs
            // off the templateClass so no instance is needed.
            Class<?> abstractChangeTemplateClass = getClass(ABSTRACT_CHANGE_TEMPLATE_FQN);
            Class<?>[] typeArgs = ReflectionUtil.resolveTypeArgumentsAsClasses(
                    templateClass, abstractChangeTemplateClass);
            for (Class<?> arg : typeArgs) {
                registerClassForReflection(arg);
            }

            // @ChangeTemplate(reflectiveClasses = {...}) — getAnnotation is a pre-init
            // operation. Default annotation value is empty array, so simple templates
            // contribute nothing here.
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> annotationClass =
                    (Class<? extends Annotation>) getClass(CHANGE_TEMPLATE_ANNOTATION_FQN);
            Annotation annotation = templateClass.getAnnotation(annotationClass);
            if (annotation != null) {
                Method reflectiveClasses = annotationClass.getMethod("reflectiveClasses");
                Class<?>[] extras = (Class<?>[]) reflectiveClasses.invoke(annotation);
                for (Class<?> extra : extras) {
                    registerClassForReflection(extra);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static Class<?> getClass(String className) throws ClassNotFoundException {
        return Class.forName(
                className, false, RegistrationFeature.class.getClassLoader());
    }

}
