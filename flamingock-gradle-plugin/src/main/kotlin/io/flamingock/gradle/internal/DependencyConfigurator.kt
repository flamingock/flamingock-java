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

import io.flamingock.gradle.FlamingockExtension
import org.gradle.api.Project

/**
 * Configures Flamingock dependencies based on the extension settings.
 */
internal object DependencyConfigurator {

    fun configure(project: Project, extension: FlamingockExtension, version: String) {
        val group = FlamingockConstants.GROUP
        val dependencies = project.dependencies
        val kaptEnabled = project.plugins.hasPlugin("org.jetbrains.kotlin.kapt")
                || project.configurations.findByName("kapt") != null

        // Always add the annotation processor
        dependencies.add(
            "annotationProcessor",
            "$group:flamingock-processor:$version"
        )
        if (kaptEnabled) {
            dependencies.add(
                "kapt",
                "$group:flamingock-processor:$version"
            )
        }

        // Always add BOM for version management
        dependencies.add(
            "implementation",
            dependencies.platform("$group:flamingock-bom:$version")
        )

        // Edition dependencies (cloud is the default)
        if (extension.isCommunityEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-community"
            )
        } else {
            dependencies.add(
                "implementation",
                "$group:flamingock-cloud"
            )
        }

        // Mongock support
        if (extension.isMongockEnabled) {
            dependencies.add(
                "implementation",
                "$group:mongock-support"
            )
            dependencies.add(
                "annotationProcessor",
                "$group:mongock-support:$version"
            )
            if (kaptEnabled) {
                dependencies.add(
                    "kapt",
                    "$group:mongock-support:$version"
                )
            }
        }

        // Spring Boot integration
        if (extension.isSpringbootEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-springboot-integration"
            )
        }

        // GraalVM support
        if (extension.isGraalvmEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-graalvm"
            )
        }

        // SQL support
        if (extension.isSqlEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-sql-template"
            )
            dependencies.add(
                "implementation",
                "$group:flamingock-sql-targetsystem"
            )
        }

        // MongoDB support
        if (extension.isMongodbEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-mongodb-sync-template"
            )
            dependencies.add(
                "implementation",
                "$group:flamingock-mongodb-sync-targetsystem"
            )
            if (extension.isSpringbootEnabled) {
                dependencies.add(
                    "implementation",
                    "$group:flamingock-mongodb-springdata-targetsystem"
                )
            }
        }

        // DynamoDB support
        if (extension.isDynamodbEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-dynamodb-targetsystem"
            )
        }

        // Couchbase support
        if (extension.isCouchbaseEnabled) {
            dependencies.add(
                "implementation",
                "$group:flamingock-couchbase-targetsystem"
            )
        }

        // Test support - springboot variant includes basic test-support transitively
        if (extension.isSpringbootEnabled) {
            dependencies.add("testImplementation", "$group:flamingock-springboot-test-support")
        } else {
            dependencies.add("testImplementation", "$group:flamingock-test-support")
        }
    }
}
