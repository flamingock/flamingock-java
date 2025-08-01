import org.jreleaser.model.Active

plugins {
    id("org.jreleaser")
}

jreleaser {
    project {
        description.set("Description should be here")
        inceptionYear.set("2024")
        authors.set(setOf("dieppa"))
    }
    signing {
        active.set(Active.ALWAYS)
        armored = true
        enabled = true
    }
    gitRootSearch.set(true)
    release {
        github {
            skipRelease.set(true)
            skipTag.set(true)
        }
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    applyMavenCentralRules.set(true)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                    maxRetries.set(90)
                    retryDelay.set(20)
                }
            }
        }
    }
}