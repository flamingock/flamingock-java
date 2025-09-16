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
    version = "0.0.40-beta"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "flamingock.project-structure")
    apply(plugin = "flamingock.release-management")
}
