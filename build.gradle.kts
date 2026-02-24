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
    version = "1.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    if (project.file("build.gradle.kts").exists()) {
        apply(plugin = "flamingock.project-structure")
        apply(plugin = "flamingock.release-management")
    }
}
