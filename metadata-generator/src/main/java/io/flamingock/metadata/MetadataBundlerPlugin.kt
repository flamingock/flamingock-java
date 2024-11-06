package io.flamingock.graalvm

import io.flamingock.core.api.metadata.FlamingockMetadata
import io.flamingock.core.api.metadata.Constants
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.register

class MetadataBundlerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val copyMetadata = project.tasks.register<Copy>("copyMetadata") {
            dependsOn("compileJava")
            from("${project.buildDir}/generated/sources/annotationProcessor/java/main/" + FlamingockMetadata.FILE_PATH)
            into("${project.projectDir}/src/main/resources/META-INF/")
        }

        val copyClassesToRegister = project.tasks.register<Copy>("copyClassesToRegister") {
            dependsOn("compileJava")
            from("${project.buildDir}/generated/sources/annotationProcessor/java/main/" + Constants.GRAALVM_REFLECT_CLASSES_PATH)
            into("${project.projectDir}/src/main/resources/META-INF/")
        }

        project.tasks.named("processResources") {
            dependsOn(copyMetadata, copyClassesToRegister)
        }
    }
}
