/*
 * Copyright 2024 Flamingock (https://www.flamingock.io)
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
package io.flamingock.gradle.internal

/**
 * Central location for all Flamingock plugin constants.
 */
internal object FlamingockConstants {
    const val GROUP = "io.flamingock"
    const val EXTENSION_NAME = "flamingock"

    const val SOURCES_OPTION = "flamingock.sources"
    const val RESOURCES_OPTION = "flamingock.resources"

    const val KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
    const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

    /**
     * Gradle property that, when set to `false`, suppresses the auto-application of
     * `org.jetbrains.kotlin.kapt` in Kotlin projects. Default behaviour (property unset or
     * any value other than literal `false`, case-insensitive) is to auto-apply kapt so the
     * Flamingock annotation processor runs against Kotlin sources.
     */
    const val AUTO_APPLY_KAPT_PROPERTY = "flamingock.autoApplyKapt"

    val FLAMINGOCK_VERSION: String by lazy {
        FlamingockConstants::class.java.classLoader
            .getResourceAsStream("flamingock-plugin.properties")
            ?.bufferedReader()
            ?.use { reader ->
                java.util.Properties().apply { load(reader) }.getProperty("version")
            }
            ?: throw IllegalStateException("Could not read flamingock-plugin.properties from classpath")
    }
}