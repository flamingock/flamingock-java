import io.flamingock.build.VersionManager
import io.flamingock.build.PrintVersionTask

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.json:json:20210307")
    }
}

plugins {
    id("flamingock.root-project")
    id("flamingock.license")
    id("flamingock.project-structure")
    id("flamingock.release-management")
}

allprojects {
    group = "io.flamingock"
    val declaredVersion = "1.3.0-SNAPSHOT"
    version = VersionManager.resolveVersion(declaredVersion, project.hasProperty("release"))

    extra["generalUtilVersion"] = "1.5.3"
    extra["templateApiVersion"] = "1.3.4"
    extra["coreApiVersion"] = "1.3.3"
    extra["sqlVersion"] = "1.3.2"
    extra["mongodbTemplateVersion"] = "1.3.2"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    if (project.file("build.gradle.kts").exists()) {
        apply(plugin = "flamingock.project-structure")
        @Suppress("UNCHECKED_CAST")
        val gradlePluginProjects = project.extra["gradlePluginProjects"] as Set<String>
        // The gradle plugin publishes through com.gradle.plugin-publish to the Gradle
        // Plugin Portal (release.yml: publishPlugins step), not through JReleaser to
        // Maven Central. Keep flamingock.release-management — and the org.jreleaser
        // plugin it brings — off this subproject entirely.
        if (project.name !in gradlePluginProjects) {
            apply(plugin = "flamingock.release-management")
        }
    }
}

tasks.register<PrintVersionTask>("printVersion")
