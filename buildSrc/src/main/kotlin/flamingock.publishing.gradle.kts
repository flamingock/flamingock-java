plugins {
    `maven-publish`
    id("org.jreleaser")
}

fun Project.isBomModule(): Boolean = name.endsWith("-bom")
fun Project.isLibraryModule(): Boolean = name !in setOf(
    "flamingock-community-bom",
    "flamingock-cloud-bom"
)

val fromComponentPublishing = if (isBomModule()) "javaPlatform" else "java"
val mavenPublication = if (isBomModule()) "communityBom" else "maven"

publishing {
    publications {
        create<MavenPublication>(mavenPublication) {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components[fromComponentPublishing])
            pom {
                name.set(project.name)
                description.set("Flamingock is a Java library that brings Change-as-Code to your applications. It enables you to define and apply versioned, auditable changes to databases, event schemas, and external systems, ensuring safety, synchronization, and governance at runtime")
                url.set("https://www.flamingock.io")
                inceptionYear.set("2024")

                organization {
                    name.set("Flamingock")
                    url.set("https://www.flamingock.io")
                }

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://spdx.org/licenses/Apache-2.0.html")
                    }
                }

                developers {
                    developer {
                        id.set("dieppa")
                        name.set("Antonio Perez Dieppa")
                        email.set("aperezdieppa@flamingock.io")
                    }
                    developer {
                        id.set("osantana")
                        name.set("Oliver Santana")
                        email.set("osantana@flamingock.io")
                    }
                    developer {
                        id.set("bercianor")
                        name.set("Ruben Berciano")
                        email.set("bercianor@flamingock.io")
                    }
                    developer {
                        id.set("dfrigolet")
                        name.set("David Frigolet")
                        email.set("dfrigolet@flamingock.io")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/flamingock/flamingock-java/issues")
                }

                scm {
                    connection.set("scm:git:https://github.com/flamingock/flamingock-java.git")
                    developerConnection.set("scm:git:ssh://github.com:flamingock/flamingock-java.git")
                    url.set("https://github.com/flamingock/flamingock-java")
                }
            }
        }
    }
    repositories {
        maven {
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
        mavenLocal()
    }
}

if (isLibraryModule()) {
    configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }
}

tasks.register("createStagingDeployFolder") {
    group = "build"
    description = "Creates the staging-deploy folder inside the build directory."
    doLast {
        val stagingDeployDir = layout.buildDirectory.dir("jreleaser").get().asFile
        if (!stagingDeployDir.exists()) {
            stagingDeployDir.mkdirs()
            println("Created: $stagingDeployDir")
        }
    }
}

tasks.matching { it.name == "publish" }.configureEach {
    finalizedBy("createStagingDeployFolder")
}