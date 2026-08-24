package com.vibe.app.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vibe.app.data.database.entity.MessageV2

@Composable
fun ChatModelDialog(
    platformOrder: List<String>,
    initialModels: Map<String, String>,
    platformNames: Map<String, String>,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Map<String, String>) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var models by rememberSaveable(platformOrder, initialModels) {
        mutableStateOf(platformOrder.associateWith { uid -> initialModels[uid].orEmpty() })
    }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = "Chat Models") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Please specify the models for each platform.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                platformOrder.forEach { platformUid ->
                    val platformName = platformNames[platformUid] ?: "Unknown"
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        value = models[platformUid].orEmpty(),
                        onValueChange = { value ->
                            models = models.toMutableMap().apply { put(platformUid, value) }
                        },
                        singleLine = true,
                        label = { Text(text = "Model for $platformName") },
                        supportingText = {
                            Text("Enter model name (e.g., gpt-4)")
                        }
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            val hasBlank = platformOrder.any { models[it].orEmpty().trim().isBlank() }
            TextButton(
                enabled = !hasBlank,
                onClick = {
                    onConfirmRequest(
                        models.mapValues { (_, model) -> model.trim() }
                    )
                }
            ) {
                Text("Update Models")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProjectNameDialog(
    initialProjectName: String,
    onConfirmRequest: (projectName: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var projectName by rememberSaveable(initialProjectName) { mutableStateOf(initialProjectName) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = "Project Name") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 16.dp),
                    value = projectName,
                    singleLine = true,
                    isError = projectName.length > 50,
                    supportingText = {
                        if (projectName.length > 50) {
                            Text("Project name is too long (${projectName.length}/50)")
                        }
                    },
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = projectName.isNotBlank() && projectName != initialProjectName,
                onClick = {
                    onConfirmRequest(projectName)
                    onDismissRequest()
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChatQuestionEditDialog(
    initialQuestion: MessageV2,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (MessageV2) -> Unit
) {
    val configuration = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { configuration.containerSize.width.toDp() }
    val screenHeight = with(LocalDensity.current) { configuration.containerSize.height.toDp() }
    var question by remember { mutableStateOf(initialQuestion.content) }

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .widthIn(max = screenWidth - 40.dp)
            .heightIn(max = screenHeight - 80.dp),
        title = { Text(text = "Edit Question") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("User Message") }
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && question != initialQuestion.content,
                onClick = { onConfirmRequest(initialQuestion.copy(content = question)) }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text("Cancel")
            }
        }
    )
}
