package io.flamingock.build

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class PrintVersionTask : DefaultTask() {

    @TaskAction
    fun print() {
        println(project.version)
    }
}
