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

import io.flamingock.api.RecoveryStrategy;
import io.flamingock.api.template.AbstractChangeTemplate;
import io.flamingock.api.template.ChangeTemplate;
import io.flamingock.api.template.TemplateField;
import io.flamingock.api.template.TemplatePayload;
import io.flamingock.api.template.TemplateStep;
import io.flamingock.api.template.wrappers.TemplateString;
import io.flamingock.api.template.wrappers.TemplateVoid;
import io.flamingock.internal.util.ReflectionUtil;
import io.flamingock.graalvm.MetadataModuleInfoLoader.MetadataModuleInfo;
import io.flamingock.internal.common.core.metadata.FlamingockMetadata;
import io.flamingock.internal.common.core.preview.*;
import io.flamingock.internal.common.core.change.AbstractChangeDescriptor;
import io.flamingock.internal.common.core.change.RecoveryDescriptor;
import io.flamingock.internal.common.core.change.TargetSystemDescriptor;
import io.flamingock.internal.common.core.change.ChangeDescriptor;
import io.flamingock.internal.common.core.template.ChangeTemplateManager;
import io.flamingock.internal.core.pipeline.loaded.LoadedPipeline;
import io.flamingock.internal.core.pipeline.loaded.stage.AbstractLoadedStage;
import io.flamingock.internal.core.change.loaded.AbstractLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractReflectionLoadedChange;
import io.flamingock.internal.core.change.loaded.AbstractTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.CodeLoadedChange;
import io.flamingock.internal.core.change.loaded.SimpleTemplateLoadedChange;
import io.flamingock.internal.core.change.loaded.MultiStepTemplateLoadedChange;
import io.flamingock.internal.util.log.FlamingockLoggerFactory;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;


import java.nio.charset.CoderResult;
import java.util.List;
import java.util.ServiceLoader;


public class RegistrationFeature implements Feature {

    private static final Logger logger = new Logger();

    private static void registerInternalClasses() {
        logger.startRegistrationProcess("internal classes");

        registerClassForReflection(ChangeDescriptor.class.getName());
        registerClassForReflection(AbstractChangeDescriptor.class.getName());

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
        registerClassForReflection(AbstractLoadedChange.class.getName());
        registerClassForReflection(AbstractReflectionLoadedChange.class.getName());
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
//        initializeClassAtBuildTime(CodeLoadedChange.class);
        // Commented out: <clinit> reaches slf4j (directly or via parent class), which
        // conflicts with Spring Boot Native (and any environment that marks slf4j
        // init-at-runtime). Re-enable only after refactoring the offending static logger
        // to a lazy-holder pattern so the parent class's <clinit> no longer touches slf4j.
        // initializeClassAtBuildTime(AbstractTemplateLoadedChange.class);
        // initializeClassAtBuildTime(SimpleTemplateLoadedChange.class);
        // initializeClassAtBuildTime(MultiStepTemplateLoadedChange.class);
        // initializeClassAtBuildTime(ChangeTemplateManager.class);
        // RecoveryDescriptor's <clinit> stores a RecoveryStrategy enum constant in
        // DEFAULT_INSTANCE; GraalVM 25+ rejects image-heap objects whose class isn't
        // also init-at-build-time, so RecoveryStrategy must come along.
//        initializeClassAtBuildTime(RecoveryStrategy.class);
//        initializeClassAtBuildTime(RecoveryDescriptor.class);
//        initializeClassAtBuildTime(FlamingockLoggerFactory.class);
        logger.completeInitializationProcess("internal classes");
    }

    private static void initializeExternalClassesAtBuildTime() {
        logger.startInitializationProcess("external classes");
//        initializeClassAtBuildTime(LoggerFactory.class);
        logger.completeInitializationProcess("external classes");
    }

    /**
     * Walk the {@link io.flamingock.internal.common.core.metadata.FlamingockMetadataProvider}
     * SPI once and do both registration passes off the resulting collection: per-module
     * metadata.json files become runtime resources, per-module reflection classes become
     * reflection-registered. The SPI service file and the generated provider impl classes
     * are intentionally not registered here — GraalVM's automatic
     * {@code ServiceLoader.load(...)} detection (triggered by the static call in
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
            // initialize=false — load the Class<?> reference without firing <clinit>.
            // Default native-image behavior (init at runtime) takes over from here, which
            // is the safe default. Classes that genuinely require build-time init opt in
            // explicitly via initializeInternalClassesAtBuildTime().
            registerClassForReflection(Class.forName(
                    className, false, RegistrationFeature.class.getClassLoader()));
            // Commented out: this redundant explicit init was the second path that pulled
            // user @Change classes' <clinit> into build time, breaking Spring Boot Native
            // when those classes had static loggers. Reflection registration alone suffices.
            // initializeClassAtBuildTime(Class.forName(className));
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
            registerClassForReflection(Class.forName(
                    className, false, RegistrationFeature.class.getClassLoader()));
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
        registerCloudApiClasses();
        registerTemplates();
        registerProviderInfo();
        logger.finishedProcess("GraalVM classes registration and initialization");
    }

    private void registerTemplates() {
        logger.startRegistrationProcess("templates");
        // Static infrastructure registrations — Flamingock-internal classes with clean
        // <clinit>; safe to register/initialize at build time.
        registerClassForReflection(ChangeTemplateManager.class);
        registerClassForReflection(ChangeTemplate.class);
        registerClassForReflection(AbstractChangeTemplate.class);
        registerClassForReflection(TemplateStep.class);
        registerClassForReflection(TemplateField.class);
        registerClassForReflection(TemplatePayload.class);
        registerClassForReflection(TemplateString.class);
        registerClassForReflection(TemplateVoid.class);

        // Per-template registration. Critical: never instantiate the template class at build
        // time. Templates routinely declare `static final Logger log = LoggerFactory.getLogger(
        // ...)` in their <clinit>, which would pull SLF4J/Logback into the build-time image
        // heap and break native-image. ServiceLoader.Provider::type returns the Class<?> ref
        // without ever calling the provider's constructor — class is loaded but NOT
        // initialized. The Class<?> overload of registerClassForReflection used below also
        // skips initialization; getAnnotation() and ReflectionUtil.resolveTypeArgumentsAsClasses
        // are pre-init operations too.
        ServiceLoader.load(ChangeTemplate.class).stream()
                .map(provider -> (Class<? extends ChangeTemplate<?, ?, ?>>) provider.type())
                .forEach(this::registerTemplateClass);

        logger.completedRegistrationProcess("templates");
    }

    /**
     * Register a single template class for reflection without initializing it. Reads
     * everything we need (generic type args + {@code @ChangeTemplate.reflectiveClasses})
     * from class-level metadata only.
     */
    private void registerTemplateClass(Class<? extends ChangeTemplate<?, ?, ?>> templateClass) {
        // Class<?> overload — does not initialize the class.
        registerClassForReflection(templateClass);

        // Generic type args (configuration / apply / rollback payload classes) — same
        // resolution AbstractChangeTemplate's runtime constructor uses, but here it runs
        // off the templateClass so no instance is needed.
        Class<?>[] typeArgs = ReflectionUtil.resolveTypeArgumentsAsClasses(
                templateClass, AbstractChangeTemplate.class);
        for (Class<?> arg : typeArgs) {
            registerClassForReflection(arg);
        }

        // @ChangeTemplate(reflectiveClasses = {...}) — getAnnotation is a pre-init operation.
        // Default annotation value is empty array, so simple templates contribute nothing here.
        io.flamingock.api.annotations.ChangeTemplate annotation =
                templateClass.getAnnotation(io.flamingock.api.annotations.ChangeTemplate.class);
        if (annotation != null) {
            for (Class<?> extra : annotation.reflectiveClasses()) {
                registerClassForReflection(extra);
            }
        }
    }


}
