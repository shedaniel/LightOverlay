package me.shedaniel.plugin.architect

import org.gradle.api.Project

open class ArchitectPluginExtension(val project: Project) {
    var minecraft = ""

    fun common() {
        project.configurations.create("mcp")

        project.tasks.getByName("remapMcp") {
            it as RemapMCPTask

            it.input.set(project.file("${project.buildDir}/libs/${project.properties["archivesBaseName"]}-${project.version}-dev.jar"))
            it.archiveClassifier.set("mcp")
            it.dependsOn(project.tasks.getByName("jar"))
            project.tasks.getByName("build").dependsOn(it)
        }

        project.artifacts {
            it.add("mcp", mapOf(
                    "file" to project.file("${project.buildDir}/libs/${project.properties["archivesBaseName"]}-${project.version}-mcp.jar"),
                    "type" to "jar",
                    "builtBy" to project.tasks.getByName("remapMcp")
            ))
        }
    }
}