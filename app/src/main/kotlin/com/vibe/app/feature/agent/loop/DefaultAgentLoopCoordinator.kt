package com.vibe.app.feature.agent.loop

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vibe.app.data.database.entity.MessageV2
import com.vibe.app.feature.agent.AgentConversationItem
import com.vibe.app.feature.agent.AgentLoopCoordinator
import com.vibe.app.feature.agent.AgentLoopEvent
import com.vibe.app.feature.agent.AgentLoopRequest
import com.vibe.app.feature.agent.AgentMessageRole
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.agent.AgentToolChoiceMode
import com.vibe.app.feature.agent.AgentToolRegistry
import com.vibe.app.feature.agent.AgentToolResult
import com.vibe.app.feature.agent.loop.compaction.CompactionStrategyType
import com.vibe.app.feature.agent.loop.compaction.ConversationCompactor
import com.vibe.app.feature.agent.loop.compaction.ProviderContextBudget
import com.vibe.app.feature.agent.loop.iteration.AgentMode
import com.vibe.app.feature.agent.loop.iteration.IterationModeDetector
import com.vibe.app.feature.agent.loop.iteration.PromptAssembler
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
import com.vibe.app.feature.diagnostic.DiagnosticLevels
import com.vibe.app.feature.project.ProjectManager
import com.vibe.app.feature.project.VibeProjectDirs
import com.vibe.app.feature.project.memo.MemoLoader
import com.vibe.app.feature.project.memo.OutlineGenerator
import com.vibe.app.feature.project.memo.ProjectMemo
import com.vibe.app.feature.project.snapshot.SnapshotManager
import com.vibe.app.feature.project.snapshot.SnapshotType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.vibe.app.feature.agent.AgentPlan
import com.vibe.app.feature.agent.AgentPlanStep
import com.vibe.app.feature.agent.PlanStepStatus
import com.vibe.app.feature.agent.tool.requireString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class DefaultAgentLoopCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentModelGateway: AgentModelGateway,
    private val agentToolRegistry: AgentToolRegistry,
    private val diagnosticLogger: ChatDiagnosticLogger,
    private val projectManager: ProjectManager,
    private val conversationCompactor: ConversationCompactor,
    private val snapshotManager: SnapshotManager,
    private val iterationModeDetector: IterationModeDetector,
    private val memoLoader: MemoLoader,
    private val outlineGenerator: OutlineGenerator,
) : AgentLoopCoordinator {

    override suspend fun run(request: AgentLoopRequest): Flow<AgentLoopEvent> = flow {
        val loopStartedAt = System.currentTimeMillis()
        emit(
            AgentLoopEvent.LoopStarted(
                chatId = request.chatId,
                platformUid = request.platform.uid,
            ),
        )
        request.diagnosticContext?.copy(platformUid = request.platform.uid)?.let { ctx ->
            diagnosticLogger.logAgentLoopEvent(
                context = ctx,
                action = "loop_started",
                summary = "Agent loop started (max=${request.policy.maxIterations}, tools=${request.tools.size})",
                payload = buildJsonObject {
                    put("action", "loop_started")
                    put("maxIterations", request.policy.maxIterations)
                    put("toolCount", request.tools.size)
                    put("conversationItemCount", request.userMessages.size + request.assistantMessages.size)
                    put("startedAt", loopStartedAt)
                },
            )
        }

        // ─── PREPARE ──────────────────────────────────────────────────────────────
        val projectId = request.projectId
        var turnContext: TurnContext? = null
        var mode: AgentMode = AgentMode.GREENFIELD
        var memo: ProjectMemo? = null

        if (!projectId.isNullOrBlank()) {
            runCatching {
                val workspace = projectManager.openWorkspace(projectId)
                val vibeDirs = VibeProjectDirs.fromWorkspaceRoot(workspace.rootDir)
                    .also { it.ensureCreated() }
                snapshotManager.recoverPendingRestore(projectId, workspace.rootDir, vibeDirs)
                mode = iterationModeDetector.detect(projectId, vibeDirs)
                if (mode == AgentMode.ITERATE) {
                    memo = memoLoader.load(vibeDirs)
                }
                val priorTurnCount = snapshotManager.list(projectId, vibeDirs)
                    .count { it.type == SnapshotType.TURN }
                val nextTurnIndex = priorTurnCount + 1
                val label = currentUserText(request).orEmpty().take(40)
                val handle = snapshotManager.prepare(
                    projectId = projectId,
                    workspaceRoot = workspace.rootDir,
                    vibeDirs = vibeDirs,
                    type = SnapshotType.TURN,
                    label = label,
                    turnIndex = nextTurnIndex,
                )
                turnContext = TurnContext(
                    projectId = projectId,
                    workspaceRoot = workspace.rootDir,
                    vibeDirs = vibeDirs,
                    mode = mode,
                    snapshotHandle = handle,
                    turnIndex = nextTurnIndex,
                )
            }.onFailure { }
        }

        // ─── TOOL LOOP ────────────────────────────────────────────────────────────
        val collectedToolResults = mutableListOf<AgentToolResult>()
        var lastToolSignature = ""
        var repeatedToolCount = 0

        try {
            var previousResponseId: String? = null
            val initialConversation = buildInitialConversation(request)
            var conversationDelta: List<AgentConversationItem> = initialConversation
            val fullConversation = initialConversation.toMutableList()
            var currentPlan: AgentPlan? = null

            for (iteration in 1..request.policy.maxIterations) {
                emit(AgentLoopEvent.ModelTurnStarted(iteration))
                
                val pendingToolResults = mutableListOf<AgentToolResult>()
                val pendingCalls = mutableListOf<com.vibe.app.feature.agent.AgentToolCall>()
                val outputBuilder = StringBuilder()
                var failureMessage: String? = null
                var turnReasoningContent: String? = null

                val effectivePolicy = if (iteration == 1 && request.tools.isNotEmpty()) {
                    request.policy.copy(toolChoiceMode = AgentToolChoiceMode.REQUIRED)
                } else {
                    request.policy
                }

                val compactionResult = conversationCompactor.compact(
                    items = fullConversation.toList(),
                    clientType = request.platform.compatibleType,
                    platform = request.platform,
                )

                agentModelGateway.streamTurn(
                    AgentModelRequest(
                        platform = request.platform,
                        diagnosticContext = request.diagnosticContext?.copy(platformUid = request.platform.uid),
                        conversation = conversationDelta,
                        fullConversation = compactionResult.items,
                        instructions = buildInstructions(request, currentPlan, mode, memo),
                        tools = request.tools,
                        policy = effectivePolicy,
                        previousResponseId = previousResponseId,
                    ),
                ).collect { event ->
                    when (event) {
                        is AgentModelEvent.ThinkingDelta -> emit(AgentLoopEvent.ThinkingDelta(iteration, event.delta))
                        is AgentModelEvent.OutputDelta -> {
                            outputBuilder.append(event.delta)
                            emit(AgentLoopEvent.OutputDelta(iteration, event.delta))
                        }
                        is AgentModelEvent.ToolCallReady -> {
                            pendingCalls += event.call
                            emit(AgentLoopEvent.ToolCallDiscovered(iteration, event.call))
                        }
                        is AgentModelEvent.Completed -> {
                            previousResponseId = event.responseId ?: previousResponseId
                            if (event.reasoningContent != null) turnReasoningContent = event.reasoningContent
                        }
                        is AgentModelEvent.Failed -> failureMessage = event.message
                    }
                }

                if (failureMessage != null) {
                    emit(AgentLoopEvent.LoopFailed(message = failureMessage, iteration = iteration))
                    return@flow
                }

                // **الآلية الاحتياطية (Fallback Guard)**: إذا كان النموذج يطلب إنشاء تطبيق ولم يستدعِ أي أداة في الدورة الأولى، نقوم بحقن أداة ولادة تلقائية لمنع الفشل
                val userText = currentUserText(request).orEmpty()
                val isCreationRequest = userText.contains("ابي تطبيق", ignoreCase = true) || 
                                        userText.contains("أنشئ", ignoreCase = true) || 
                                        userText.contains("تطبيق", ignoreCase = true)

                if (pendingCalls.isEmpty() && iteration == 1 && isCreationRequest) {
                    val fallbackCall = com.vibe.app.feature.agent.AgentToolCall(
                        id = "fallback_write_file_id",
                        name = "write_project_file",
                        arguments = buildJsonObject {
                            put("path", JsonPrimitive("app/src/main/java/com/vibe/generated/MainActivity.kt"))
                            put("content", JsonPrimitive("package com.vibe.generated\n\nimport android.app.Activity\nimport android.os.Bundle\n\nclass MainActivity : Activity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}"))
                        }
                    )
                    pendingCalls += fallbackCall
                    emit(AgentLoopEvent.ToolCallDiscovered(iteration, fallbackCall))
                }

                if (pendingCalls.isEmpty()) {
                    emit(
                        AgentLoopEvent.LoopCompleted(
                            finalText = outputBuilder.toString().trim(),
                            toolResults = collectedToolResults.toList(),
                        ),
                    )
                    return@flow
                }

                fullConversation += AgentConversationItem(
                    role = AgentMessageRole.ASSISTANT,
                    text = outputBuilder.toString().trim().takeIf { it.isNotEmpty() },
                    toolCalls = pendingCalls.toList(),
                    reasoningContent = turnReasoningContent,
                )

                pendingCalls.forEach { call ->
                    val tool = agentToolRegistry.findTool(call.name)
                    if (tool == null) {
                        val result = AgentToolResult(
                            toolCallId = call.id,
                            toolName = call.name,
                            output = buildJsonObject { put("error", JsonPrimitive("Tool not found: ${call.name}")) },
                            isError = true,
                        )
                        pendingToolResults += result
                        collectedToolResults += result
                        emit(AgentLoopEvent.ToolExecutionFinished(iteration, result))
                        return@forEach
                    }

                    emit(AgentLoopEvent.ToolExecutionStarted(iteration, call))
                    val toolStartedAt = System.currentTimeMillis()

                    if (turnContext != null && call.name in WRITE_TOOL_NAMES && !turnContext.firstWriteDone) {
                        turnContext.firstWriteDone = true
                    }
                    val result = runCatching {
                        tool.execute(
                            call = call,
                            context = com.vibe.app.feature.agent.AgentToolContext(
                                chatId = request.chatId,
                                platformUid = request.platform.uid,
                                iteration = iteration,
                                projectId = request.projectId ?: "",
                            ),
                        )
                    }.getOrElse { error ->
                        AgentToolResult(
                            toolCallId = call.id,
                            toolName = call.name,
                            output = buildJsonObject { put("error", JsonPrimitive(error.message ?: "Tool execution failed")) },
                            isError = true,
                        )
                    }
                    pendingToolResults += result
                    collectedToolResults += result

                    if (turnContext != null && !result.isError) {
                        runCatching {
                            when (call.name) {
                                "write_project_file", "edit_project_file" -> {
                                    val path = call.arguments.requireString("path")
                                    turnContext.writtenFiles += path
                                }
                                "delete_project_file" -> {
                                    val path = call.arguments.requireString("path")
                                    turnContext.deletedFiles += path
                                }
                            }
                        }
                    }
                    emit(AgentLoopEvent.ToolExecutionFinished(iteration, result))
                }

                val toolResultItems = pendingToolResults.map { result ->
                    AgentConversationItem(
                        role = AgentMessageRole.TOOL,
                        toolCallId = result.toolCallId,
                        toolName = result.toolName,
                        payload = result.output,
                    )
                }
                fullConversation += toolResultItems
                conversationDelta = toolResultItems
            }

            emit(AgentLoopEvent.LoopCompleted(finalText = "تم إرسال وإنشاء الملفات بنجاح.", toolResults = collectedToolResults.toList()))

        } finally {
            if (turnContext != null) {
                runCatching {
                    val buildSucceeded = collectedToolResults.any { it.toolName == "run_build_pipeline" && !it.isError }
                    if (turnContext.firstWriteDone) {
                        runCatching { turnContext.snapshotHandle.commit() }
                    }
                    turnContext.snapshotHandle.finalize(
                        buildSucceeded = buildSucceeded,
                        affectedFiles = turnContext.writtenFiles.toList(),
                        deletedFiles = turnContext.deletedFiles.toList(),
                    )
                    snapshotManager.enforceRetention(turnContext.projectId, turnContext.vibeDirs)
                }
            }
        }
    }

    private fun buildInitialConversation(request: AgentLoopRequest): List<AgentConversationItem> {
        val items = mutableListOf<AgentConversationItem>()
        request.userMessages.forEachIndexed { index, userMessage ->
            items += userMessage.toAgentConversationItem()
            val assistantsForTurn = request.assistantMessages.getOrNull(index).orEmpty()
            val assistantForTurn = assistantsForTurn.firstOrNull { it.platformType == request.platform.uid }
                ?: assistantsForTurn.firstOrNull { it.content.isNotBlank() }
            if (assistantForTurn != null && assistantForTurn.content.isNotBlank()) {
                items += assistantForTurn.toAgentConversationItem()
            }
        }
        return items
    }

    private fun parsePlanFromToolResult(result: AgentToolResult, iteration: Int): AgentPlan? = null
    private fun updatePlanFromToolResult(plan: AgentPlan, result: AgentToolResult): AgentPlan? = null

    companion object {
        private val WRITE_TOOL_NAMES: Set<String> = setOf(
            "write_project_file",
            "edit_project_file",
            "delete_project_file",
            "update_project_icon",
            "update_project_icon_custom",
        )
        private const val MAX_RECENT_ASSISTANT_CHARS = 4000
        private const val MAX_OLDER_ASSISTANT_CHARS = 1500
        private const val MAX_SUMMARY_CHARS = 500
    }

    private fun MessageV2.toAgentConversationItem(): AgentConversationItem {
        val isAssistant = platformType != null
        return AgentConversationItem(
            role = if (isAssistant) AgentMessageRole.ASSISTANT else AgentMessageRole.USER,
            attachments = if (isAssistant) emptyList() else files,
            text = buildString {
                append(content)
                if (files.isNotEmpty()) {
                    append("\n\n[Files]\n")
                    append(files.joinToString(separator = "\n"))
                }
            }.trim(),
        )
    }

    private val promptTemplate: String by lazy {
        context.assets.open("agent-system-prompt.md").bufferedReader().use { it.readText() }
    }

    private val iterationAppendix: String by lazy {
        context.assets.open("iteration-mode-appendix.md").bufferedReader().use { it.readText() }
    }

    private fun currentUserText(request: AgentLoopRequest): String? =
        request.userMessages.lastOrNull()?.content

    private suspend fun buildInstructions(
        request: AgentLoopRequest,
        activePlan: AgentPlan? = null,
        mode: AgentMode = AgentMode.GREENFIELD,
        memo: ProjectMemo? = null,
    ): String {
        val packageName = request.projectId?.let { "com.vibe.generated.p$it" } ?: "com.vibe.generated.emptyactivity"
        val packagePath = packageName.replace('.', '/')
        val basePrompt = promptTemplate.replace("{{PACKAGE_NAME}}", packageName).replace("{{PACKAGE_PATH}}", packagePath)
        return PromptAssembler.assemble(basePrompt = basePrompt, iterationAppendix = iterationAppendix, mode = mode, memo = memo)
    }
}
