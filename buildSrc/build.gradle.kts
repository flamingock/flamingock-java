plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.jreleaser:jreleaser-gradle-plugin:1.15.0")
    implementation("org.json:json:20210307")
}