import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("java")
}

description = "${project.name}'s description"

val jacksonVersion = "2.15.2"
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":flamingock-core-api"))

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

gradlePlugin {
    plugins {
        create("autoConfigurePlugin") {
            id = "io.flamingock.MetadataBundler"
            implementationClass = "io.flamingock.graalvm.MetadataBundlerPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}