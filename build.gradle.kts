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
}

allprojects {
    group = "io.flamingock"
    version = "0.0.38-beta"
    
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "flamingock.project-structure")
    apply(plugin = "flamingock.release-management")
}