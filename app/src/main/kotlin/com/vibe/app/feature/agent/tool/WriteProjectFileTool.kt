package com.vibe.app.feature.agent.tool

import com.vibe.app.feature.agent.AgentTool
import com.vibe.app.feature.agent.AgentToolCall
import com.vibe.app.feature.agent.AgentToolContext
import com.vibe.app.feature.agent.AgentToolDefinition
import com.vibe.app.feature.agent.AgentToolResult
import com.vibe.app.feature.project.ProjectManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Singleton
class WriteProjectFileTool @Inject constructor(
    private val projectManager: ProjectManager,
) : AgentTool {

    override val definition = AgentToolDefinition(
        name = "write_project_file",
        description = "Mandatory tool for creating Android project files. When the user requests creating or modifying files, use this tool. Do not explain steps instead of writing files.",
        inputSchema = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put(
                "properties",
                buildJsonObject {
                    put("path", stringProp())
                    put("content", stringProp())
                },
            )
            put("required", requiredFields("path", "content"))
        },
    )

    override suspend fun execute(call: AgentToolCall, context: AgentToolContext): AgentToolResult {
        val path = call.arguments.requireString("path")
        val content = call.arguments.requireString("content")

        return try {
            val workspace = projectManager.openWorkspace(context.projectId)
            workspace.writeTextFile(path, content)

            val file = File(workspace.rootDir, path)
            val success = file.exists() && file.length() > 0L

            call.result(
                output = buildJsonObject {
                    put("success", JsonPrimitive(success))
                    put("path", JsonPrimitive(path))
                    put("bytesWritten", JsonPrimitive(file.length()))
                },
                isError = !success,
            )
        } catch (e: Exception) {
            call.errorResult(e.message ?: "Failed to write project file")
        }
    }
}
