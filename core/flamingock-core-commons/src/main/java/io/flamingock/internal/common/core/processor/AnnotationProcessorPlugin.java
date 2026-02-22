/*
 * Copyright 2026 Flamingock (https://www.flamingock.io)
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
package io.flamingock.internal.common.core.processor;

import io.flamingock.internal.common.core.util.LoggerPreProcessor;

import javax.annotation.processing.RoundEnvironment;

/**
 * Extension point for annotation processing plugins.
 * <p>
 * Implementations are discovered using {@link java.util.ServiceLoader}. For an implementation to be found at
 * processing time, the JAR that provides it must be present on the application's {@code annotationProcessor}
 * classpath.
 * <p>
 * The implementation JAR must also include the standard service registration file
 * {@code META-INF/services/io.flamingock.internal.common.core.processor.AnnotationProcessorPlugin},
 * listing the implementation class name.
 */
public interface AnnotationProcessorPlugin {

    void initialize(RoundEnvironment roundEnv, LoggerPreProcessor logger);
}
