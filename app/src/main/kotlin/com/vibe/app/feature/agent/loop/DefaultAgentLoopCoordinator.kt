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
import com.vibe.app.feature.agent.loop.compaction.ConversationCompactor
import com.vibe.app.feature.agent.loop.compaction.ProviderContextBudget
import com.vibe.app.feature.agent.loop.iteration.AgentMode
import com.vibe.app.feature.agent.loop.iteration.IterationModeDetector
import com.vibe.app.feature.agent.loop.iteration.PromptAssembler
import com.vibe.app.feature.diagnostic.ChatDiagnosticLogger
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
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

    override suspend fun run(
        request: AgentLoopRequest
    ): Flow<AgentLoopEvent> = flow {

        emit(
            AgentLoopEvent.LoopStarted(
                chatId = request.chatId,
                platformUid = request.platform.uid,
            )
        )

        val projectId = request.projectId

        var turnContext: TurnContext? = null
        var mode = AgentMode.GREENFIELD
        var memo: ProjectMemo? = null

        if (!projectId.isNullOrBlank()) {
            runCatching {

                val workspace =
                    projectManager.openWorkspace(projectId)

                val vibeDirs =
                    VibeProjectDirs
                        .fromWorkspaceRoot(
                            workspace.rootDir
                        )
                        .also {
                            it.ensureCreated()
                        }

                snapshotManager.recoverPendingRestore(
                    projectId,
                    workspace.rootDir,
                    vibeDirs,
                )

                mode =
                    iterationModeDetector.detect(
                        projectId,
                        vibeDirs,
                    )

                if (mode == AgentMode.ITERATE) {
                    memo =
                        memoLoader.load(
                            vibeDirs
                        )
                }

                val priorTurnCount =
                    snapshotManager
                        .list(
                            projectId,
                            vibeDirs,
                        )
                        .count {
                            it.type ==
                                SnapshotType.TURN
                        }

                val nextTurnIndex =
                    priorTurnCount + 1

                val handle =
                    snapshotManager.prepare(
                        projectId = projectId,
                        workspaceRoot =
                            workspace.rootDir,
                        vibeDirs = vibeDirs,
                        type = SnapshotType.TURN,
                        label =
                            currentUserText(
                                request
                            )
                                .orEmpty()
                                .take(40),
                        turnIndex =
                            nextTurnIndex,
                    )

                turnContext =
                    TurnContext(
                        projectId = projectId,
                        workspaceRoot =
                            workspace.rootDir,
                        vibeDirs = vibeDirs,
                        mode = mode,
                        snapshotHandle = handle,
                        turnIndex =
                            nextTurnIndex,
                    )
            }
        }

        val collectedToolResults =
            mutableListOf<AgentToolResult>()

        try {

            var previousResponseId: String? =
                null

            val initialConversation =
                buildInitialConversation(
                    request
                )

            var conversationDelta =
                initialConversation

            val fullConversation =
                initialConversation
                    .toMutableList()

            for (
                iteration in
                1..request.policy.maxIterations
            ) {

                emit(
                    AgentLoopEvent.ModelTurnStarted(
                        iteration
                    )
                )

                val pendingToolResults =
                    mutableListOf<AgentToolResult>()

                val pendingCalls =
                    mutableListOf<
                        com.vibe.app.feature.agent.AgentToolCall
                    >()

                val outputBuilder =
                    StringBuilder()

                var failureMessage: String? =
                    null

                var turnReasoningContent:
                    String? = null

                val effectivePolicy =
                    if (
                        iteration == 1 &&
                        request.tools.isNotEmpty()
                    ) {

                        request.policy.copy(
                            toolChoiceMode =
                                AgentToolChoiceMode.REQUIRED
                        )

                    } else {

                        request.policy
                    }

                val compactionResult =
                    conversationCompactor.compact(
                        items =
                            fullConversation.toList(),
                        clientType =
                            request.platform.compatibleType,
                        platform =
                            request.platform,
                    )

                agentModelGateway
                    .streamTurn(
                        AgentModelRequest(
                            platform =
                                request.platform,

                            diagnosticContext =
                                request.diagnosticContext
                                    ?.copy(
                                        platformUid =
                                            request.platform.uid
                                    ),

                            conversation =
                                conversationDelta,

                            fullConversation =
                                compactionResult.items,

                            instructions =
                                buildInstructions(
                                    request = request,
                                    mode = mode,
                                    memo = memo,
                                ),

                            tools =
                                request.tools,

                            policy =
                                effectivePolicy,

                            previousResponseId =
                                previousResponseId,
                        )
                    )
                    .collect { event ->

                        when (event) {

                            is AgentModelEvent.ThinkingDelta -> {

                                emit(
                                    AgentLoopEvent.ThinkingDelta(
                                        iteration,
                                        event.delta,
                                    )
                                )
                            }

                            is AgentModelEvent.OutputDelta -> {

                                outputBuilder.append(
                                    event.delta
                                )

                                emit(
                                    AgentLoopEvent.OutputDelta(
                                        iteration,
                                        event.delta,
                                    )
                                )
                            }

                            is AgentModelEvent.ToolCallReady -> {

                                pendingCalls +=
                                    event.call

                                emit(
                                    AgentLoopEvent.ToolCallDiscovered(
                                        iteration,
                                        event.call,
                                    )
                                )
                            }

                            is AgentModelEvent.Completed -> {

                                previousResponseId =
                                    event.responseId
                                        ?: previousResponseId

                                turnReasoningContent =
                                    event.reasoningContent
                            }

                            is AgentModelEvent.Failed -> {

                                failureMessage =
                                    event.message
                            }
                        }
                    }

                if (failureMessage != null) {

                    emit(
                        AgentLoopEvent.LoopFailed(
                            message =
                                failureMessage,
                            iteration =
                                iteration,
                        )
                    )

                    return@flow
                }

                if (pendingCalls.isEmpty()) {

                    emit(
                        AgentLoopEvent.LoopCompleted(
                            finalText =
                                outputBuilder
                                    .toString()
                                    .trim(),

                            toolResults =
                                collectedToolResults.toList(),
                        )
                    )

                    return@flow
                }

                fullConversation +=
                    AgentConversationItem(
                        role =
                            AgentMessageRole.ASSISTANT,

                        text =
                            outputBuilder
                                .toString()
                                .trim()
                                .takeIf {
                                    it.isNotEmpty()
                                },

                        toolCalls =
                            pendingCalls.toList(),

                        reasoningContent =
                            turnReasoningContent,
                    )

                var shouldStopAfterToolFailure =
                    false

                pendingCalls.forEach { call ->

                    val tool =
                        agentToolRegistry.findTool(
                            call.name
                        )

                    if (tool == null) {

                        val result =
                            AgentToolResult(
                                toolCallId =
                                    call.id,

                                toolName =
                                    call.name,

                                output =
                                    buildJsonObject {
                                        put(
                                            "error",
                                            JsonPrimitive(
                                                "Tool not found: ${call.name}"
                                            )
                                        )
                                    },

                                isError = true,
                            )

                        pendingToolResults +=
                            result

                        collectedToolResults +=
                            result

                        emit(
                            AgentLoopEvent.ToolExecutionFinished(
                                iteration,
                                result,
                            )
                        )

                        return@forEach
                    }

                    emit(
                        AgentLoopEvent.ToolExecutionStarted(
                            iteration,
                            call,
                        )
                    )

                    val result =
                        runCatching {

                            tool.execute(
                                call = call,

                                context =
                                    com.vibe.app.feature.agent.AgentToolContext(
                                        chatId =
                                            request.chatId,

                                        platformUid =
                                            request.platform.uid,

                                        iteration =
                                            iteration,

                                        projectId =
                                            request.projectId
                                                ?: "",
                                    ),
                            )

                        }.getOrElse { error ->

                            AgentToolResult(
                                toolCallId =
                                    call.id,

                                toolName =
                                    call.name,

                                output =
                                    buildJsonObject {
                                        put(
                                            "error",
                                            JsonPrimitive(
                                                error.message
                                                    ?: "Tool execution failed"
                                            )
                                        )
                                    },

                                isError = true,
                            )
                        }

                    pendingToolResults +=
                        result

                    collectedToolResults +=
                        result

                    if (
                        call.name ==
                            "run_build_pipeline" &&
                        result.isError
                    ) {

                        val errorText =
                            result.output
                                .toString()
                                .lowercase()

                        if (
                            isExternalBuildFailure(
                                errorText
                            )
                        ) {

                            shouldStopAfterToolFailure =
                                true

                            emit(
                                AgentLoopEvent.ToolExecutionFinished(
                                    iteration,
                                    result,
                                )
                            )

                            return@forEach
                        }
                    }

                    if (
                        turnContext != null &&
                        !result.isError
                    ) {

                        runCatching {

                            when (call.name) {

                                "write_project_file",
                                "edit_project_file" -> {

                                    val path =
                                        call.arguments
                                            .jsonObject["path"]
                                            ?.jsonPrimitive
                                            ?.content
                                            ?: return@runCatching

                                    turnContext!!
                                        .writtenFiles +=
                                        path
                                }

                                "delete_project_file" -> {

                                    val path =
                                        call.arguments
                                            .jsonObject["path"]
                                            ?.jsonPrimitive
                                            ?.content
                                            ?: return@runCatching

                                    turnContext!!
                                        .deletedFiles +=
                                        path
                                }
                            }
                        }
                    }

                    emit(
                        AgentLoopEvent.ToolExecutionFinished(
                            iteration,
                            result,
                        )
                    )
                }

                if (
                    shouldStopAfterToolFailure
                ) {

                    emit(
                        AgentLoopEvent.LoopFailed(
                            message =
                                "The build service is temporarily unavailable. " +
                                    "The project files were created successfully, " +
                                    "but the build service returned an upstream " +
                                    "rate-limit or overload error. " +
                                    "Please retry the build later.",

                            iteration =
                                iteration,
                        )
                    )

                    return@flow
                }

                val toolResultItems =
                    pendingToolResults.map { result ->

                        AgentConversationItem(
                            role =
                                AgentMessageRole.TOOL,

                            toolCallId =
                                result.toolCallId,

                            toolName =
                                result.toolName,

                            payload =
                                result.output,
                        )
                    }

                fullConversation +=
                    toolResultItems

                conversationDelta =
                    toolResultItems
            }

            val windDownMessage =
                AgentConversationItem(
                    role =
                        AgentMessageRole.USER,

                    text =
                        "[System] You have used all available iterations. " +
                            "Do NOT call any more tools. " +
                            "Summarize what you have accomplished so far.",
                )

            fullConversation +=
                windDownMessage

            conversationDelta =
                listOf(
                    windDownMessage
                )

            val finalOutput =
                StringBuilder()

            emit(
                AgentLoopEvent.ModelTurnStarted(
                    request.policy.maxIterations + 1
                )
            )

            val windDownCompaction =
                conversationCompactor.compact(
                    items =
                        fullConversation.toList(),

                    clientType =
                        request.platform.compatibleType,

                    platform =
                        request.platform,
                )

            agentModelGateway
                .streamTurn(
                    AgentModelRequest(
                        platform =
                            request.platform,

                        diagnosticContext =
                            request.diagnosticContext
                                ?.copy(
                                    platformUid =
                                        request.platform.uid
                                ),

                        conversation =
                            conversationDelta,

                        fullConversation =
                            windDownCompaction.items,

                        instructions =
                            buildInstructions(
                                request = request,
                                mode = mode,
                                memo = memo,
                            ),

                        tools =
                            emptyList(),

                        policy =
                            request.policy.copy(
                                toolChoiceMode =
                                    AgentToolChoiceMode.NONE
                            ),

                        previousResponseId =
                            previousResponseId,
                    )
                )
                .collect { event ->

                    when (event) {

                        is AgentModelEvent.OutputDelta -> {

                            finalOutput.append(
                                event.delta
                            )

                            emit(
                                AgentLoopEvent.OutputDelta(
                                    request.policy.maxIterations + 1,
                                    event.delta,
                                )
                            )
                        }

                        is AgentModelEvent.ThinkingDelta -> {

                            emit(
                                AgentLoopEvent.ThinkingDelta(
                                    request.policy.maxIterations + 1,
                                    event.delta,
                                )
                            )
                        }

                        else -> Unit
                    }
                }

            val summary =
                finalOutput
                    .toString()
                    .trim()

            if (summary.isNotEmpty()) {

                emit(
                    AgentLoopEvent.LoopCompleted(
                        finalText =
                            summary,

                        toolResults =
                            collectedToolResults.toList(),
                    )
                )

            } else {

                emit(
                    AgentLoopEvent.LoopFailed(
                        message =
                            "Agent loop exceeded max iterations: " +
                                request.policy.maxIterations,

                        iteration =
                            request.policy.maxIterations,
                    )
                )
            }

        } finally {

            if (turnContext != null) {

                runCatching {

                    val buildSucceeded =
                        collectedToolResults.any {
                            it.toolName ==
                                "run_build_pipeline" &&
                                !it.isError
                        }

                    if (buildSucceeded) {

                        outlineGenerator.regenerate(
                            turnContext!!.projectId,
                            turnContext!!.workspaceRoot,
                            turnContext!!.vibeDirs,
                        )
                    }

                    if (
                        turnContext!!.firstWriteDone
                    ) {

                        runCatching {

                            turnContext!!
                                .snapshotHandle
                                .commit()
                        }
                    }

                    turnContext!!
                        .snapshotHandle
                        .finalize(
                            buildSucceeded =
                                buildSucceeded,

                            affectedFiles =
                                turnContext!!
                                    .writtenFiles
                                    .toList(),

                            deletedFiles =
                                turnContext!!
                                    .deletedFiles
                                    .toList(),
                        )

                    snapshotManager.enforceRetention(
                        turnContext!!.projectId,
                        turnContext!!.vibeDirs,
                    )
                }
            }
        }
    }

    private fun isExternalBuildFailure(
        error: String
    ): Boolean {

        val indicators =
            listOf(
                "upstream error",
                "service temporarily overloaded",
                "temporarily overloaded",
                "rate limit",
                "rate-limit",
                "ratelimit",
                "too many requests",
                "http 429",
                "status 429",
                "http 502",
                "status 502",
                "http 503",
                "status 503",
                "http 504",
                "status 504",
                "service unavailable",
                "temporarily unavailable",
                "provider returned error",
                "provider error",
                "gateway timeout",
            )

        return indicators.any {
            error.contains(it)
        }
    }

    private fun buildInitialConversation(
        request: AgentLoopRequest
    ): List<AgentConversationItem> {

        val items =
            mutableListOf<AgentConversationItem>()

        request.userMessages
            .forEachIndexed { index, userMessage ->

                items +=
                    userMessage
                        .toAgentConversationItem()

                val assistantsForTurn =
                    request.assistantMessages
                        .getOrNull(index)
                        .orEmpty()

                val assistantForTurn =
                    assistantsForTurn
                        .firstOrNull {
                            it.platformType ==
                                request.platform.uid
                        }
                        ?: assistantsForTurn
                            .firstOrNull {
                                it.content.isNotBlank()
                            }

                if (
                    assistantForTurn != null &&
                    assistantForTurn.content.isNotBlank()
                ) {

                    items +=
                        assistantForTurn
                            .toAgentConversationItem()
                }
            }

        return compactCrossTurnHistory(
            items,
            request,
        )
    }

    private fun compactCrossTurnHistory(
        items: List<AgentConversationItem>,
        request: AgentLoopRequest,
    ): List<AgentConversationItem> {

        val budget =
            ProviderContextBudget
                .forProvider(
                    request.platform.compatibleType
                )

        val historyBudget =
            (
                budget.maxTokens * 0.6
            ).toInt()

        val currentTokens =
            ConversationContextManager
                .estimateTokens(items)

        if (
            currentTokens <= historyBudget
        ) {
            return items
        }

        val assistantIndices =
            items.indices
                .filter {
                    items[it].role ==
                        AgentMessageRole.ASSISTANT &&
                        items[it].toolCalls
                            .isNullOrEmpty()
                }
                .reversed()

        if (
            assistantIndices.isEmpty()
        ) {
            return items
        }

        val result =
            items.toMutableList()

        assistantIndices
            .forEachIndexed { rank, itemIndex ->

                val item =
                    result[itemIndex]

                val text =
                    item.text
                        ?: return@forEachIndexed

                val maxChars =
                    when (rank) {

                        0 ->
                            MAX_RECENT_ASSISTANT_CHARS

                        1 ->
                            MAX_OLDER_ASSISTANT_CHARS

                        else ->
                            MAX_SUMMARY_CHARS
                    }

                if (
                    text.length > maxChars
                ) {

                    result[itemIndex] =
                        item.copy(

                            text =
                                text.take(
                                    maxChars
                                ) +
                                    "\n\n" +
                                    "[... earlier content truncated for context budget]",

                            reasoningContent =
                                null,
                        )
                }
            }

        return result
    }

    private fun MessageV2.toAgentConversationItem():
        AgentConversationItem {

        val isAssistant =
            platformType != null

        return AgentConversationItem(

            role =
                if (isAssistant)
                    AgentMessageRole.ASSISTANT
                else
                    AgentMessageRole.USER,

            attachments =
                if (isAssistant)
                    emptyList()
                else
                    files,

            text =
                buildString {

                    if (isAssistant) {

                        buildTurnWorkSummary(
                            thoughts
                        )?.let { summary ->

                            append(summary)
                            append("\n\n")
                        }
                    }

                    append(content)

                    if (files.isNotEmpty()) {

                        append(
                            "\n\n[Files]\n"
                        )

                        append(
                            files.joinToString(
                                separator = "\n"
                            )
                        )
                    }

                }.trim(),
        )
    }

    private val promptTemplate: String by lazy {

        context.assets
            .open(
                "agent-system-prompt.md"
            )
            .bufferedReader()
            .use {
                it.readText()
            }
    }

    private val iterationAppendix: String by lazy {

        context.assets
            .open(
                "iteration-mode-appendix.md"
            )
            .bufferedReader()
            .use {
                it.readText()
            }
    }

    private fun currentUserText(
        request: AgentLoopRequest
    ): String? =
        request.userMessages
            .lastOrNull()
            ?.content

    private suspend fun buildInstructions(
        request: AgentLoopRequest,
        mode: AgentMode = AgentMode.GREENFIELD,
        memo: ProjectMemo? = null,
    ): String {

        val packageName =
            request.projectId
                ?.let {
                    "com.vibe.generated.p$it"
                }
                ?: "com.vibe.generated.emptyactivity"

        val packagePath =
            packageName.replace(
                '.',
                '/'
            )

        val custom =
            request.systemPrompt
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: request.platform.systemPrompt
                    ?.takeIf {
                        it.isNotBlank()
                    }

        val basePrompt =
            promptTemplate
                .replace(
                    "{{PACKAGE_NAME}}",
                    packageName
                )
                .replace(
                    "{{PACKAGE_PATH}}",
                    packagePath
                )

        val assembled =
            PromptAssembler.assemble(
                basePrompt =
                    basePrompt,

                iterationAppendix =
                    iterationAppendix,

                mode =
                    mode,

                memo =
                    memo,
            )

        return buildString {

            append(
                assembled
            )

            if (custom != null) {

                append(
                    "\n\n[Additional System Prompt]\n"
                )

                append(
                    custom
                )
            }
        }
    }

    companion object {

        private const val MAX_RECENT_ASSISTANT_CHARS =
            4000

        private const val MAX_OLDER_ASSISTANT_CHARS =
            1500

        private const val MAX_SUMMARY_CHARS =
            500
    }
}
