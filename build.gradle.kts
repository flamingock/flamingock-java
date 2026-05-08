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
    version = "1.3.0-SNAPSHOT"

    extra["generalUtilVersion"] = "1.5.3"
    extra["templateApiVersion"] = "1.3.4-SNAPSHOT"
    extra["coreApiVersion"] = "1.3.3-SNAPSHOT"
    extra["sqlVersion"] = "1.3.2-SNAPSHOT"
    extra["mongodbTemplateVersion"] = "1.3.2-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    if (project.file("build.gradle.kts").exists()) {
        apply(plugin = "flamingock.project-structure")
        apply(plugin = "flamingock.release-management")
    }
}
