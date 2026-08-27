package com.vibe.app.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibe.app.R
import com.vibe.app.data.dto.OpenRouterModel
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.model.ModelSpeedTier
import com.vibe.app.data.model.ModelTaskTier
import com.vibe.app.data.model.speedTier
import com.vibe.app.data.model.taskTier
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogSelector(
    providerType: ClientType,
    selectedModel: String,
    isFreePlan: Boolean,
    models: List<OpenRouterModel>,
    isLoading: Boolean,
    enabled: Boolean = true,
    onPlanTypeChange: (Boolean) -> Unit,
    onModelSelected: (OpenRouterModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(providerType, isFreePlan) { mutableStateOf(false) }
    var searchQuery by remember(providerType, isFreePlan) { mutableStateOf("") }

    val filteredModels = remember(models, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            models
        } else {
            models.filter { model ->
                model.id.contains(query, ignoreCase = true) ||
                    model.name?.contains(query, ignoreCase = true) == true
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanSegmentedControl(
            isFreePlan = isFreePlan,
            enabled = enabled,
            onPlanTypeChange = { isFree ->
                onPlanTypeChange(isFree)
                searchQuery = ""
                expanded = false
            },
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (enabled && !isLoading) expanded = true
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(
                        if (providerType == ClientType.GOOGLE_AI_STUDIO) {
                            R.string.search_google_models
                        } else {
                            R.string.search_openrouter_live_models
                        }
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            singleLine = true,
        )

        Text(
            text = stringResource(R.string.choose_model),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = {
                if (enabled && !isLoading) expanded = !expanded
            },
        ) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.model_name)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (selectedModel.isBlank()) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                singleLine = true,
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                when {
                    isLoading -> {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            },
                            onClick = {},
                        )
                    }

                    filteredModels.isEmpty() -> {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (searchQuery.isBlank()) {
                                        stringResource(R.string.no_models_available)
                                    } else {
                                        stringResource(R.string.no_matching_models)
                                    }
                                )
                            },
                            onClick = { expanded = false },
                        )
                    }

                    else -> {
                        filteredModels.forEach { model ->
                            val selected = model.id == selectedModel
                            DropdownMenuItem(
                                text = {
                                    ModelCatalogItem(
                                        model = model,
                                        selected = selected,
                                    )
                                },
                                trailingIcon = {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                                onClick = {
                                    onModelSelected(model)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (providerType == ClientType.OPEN_ROUTER) {
                    stringResource(R.string.openrouter_live_catalog_note)
                } else {
                    stringResource(R.string.pricing_snapshot_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlanSegmentedControl(
    isFreePlan: Boolean,
    enabled: Boolean,
    onPlanTypeChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Segment(
                text = stringResource(R.string.model_catalog_free),
                selected = isFreePlan,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onPlanTypeChange(true) },
            )
            Segment(
                text = stringResource(R.string.model_catalog_paid),
                selected = !isFreePlan,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onPlanTypeChange(false) },
            )
        }
    }
}

@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ModelCatalogItem(
    model: OpenRouterModel,
    selected: Boolean,
) {
    val price = model.pricing
    val priceText = when {
        price?.isFree == true -> stringResource(R.string.model_price_free)
        price?.promptPricePerMillion != null && price.completionPricePerMillion != null -> {
            stringResource(
                R.string.model_price_io_per_million,
                formatUsd(price.promptPricePerMillion!!),
                formatUsd(price.completionPricePerMillion!!),
            )
        }
        else -> stringResource(R.string.model_price_unavailable)
    }

    val speedText = stringResource(
        when (model.speedTier) {
            ModelSpeedTier.VERY_FAST -> R.string.model_speed_very_fast
            ModelSpeedTier.FAST -> R.string.model_speed_fast
            ModelSpeedTier.BALANCED -> R.string.model_speed_balanced
            ModelSpeedTier.SLOWER -> R.string.model_speed_slower
        }
    )

    val taskText = stringResource(
        when (model.taskTier) {
            ModelTaskTier.SIMPLE -> R.string.model_task_simple
            ModelTaskTier.MEDIUM -> R.string.model_task_medium
            ModelTaskTier.COMPLEX -> R.string.model_task_complex
        }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = model.id,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (!model.name.isNullOrBlank() && model.name != model.id) {
            Text(
                text = model.name!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = priceText,
            style = MaterialTheme.typography.labelSmall,
            color = if (price?.isFree == true) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = "$speedText • $taskText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.supportsTools) {
            Text(
                text = stringResource(R.string.model_agent_compatible),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatUsd(value: Double): String =
    when {
        value >= 100.0 -> String.format(Locale.US, "%.0f", value)
        value >= 10.0 -> String.format(Locale.US, "%.2f", value)
        value >= 1.0 -> String.format(Locale.US, "%.2f", value)
        else -> String.format(Locale.US, "%.3f", value)
    }
