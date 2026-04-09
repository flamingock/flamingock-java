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
    version = "1.2.0"

    extra["generalUtilVersion"] = "1.5.1"
    extra["templateApiVersion"] = "1.3.2"
    extra["coreApiVersion"] = "1.3.1"
    extra["sqlVersion"] = "1.2.0"
    extra["mongodbTemplateVersion"] = "1.2.0"

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
