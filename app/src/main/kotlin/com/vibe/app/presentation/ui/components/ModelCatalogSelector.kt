package com.vibe.app.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = isFreePlan,
                enabled = enabled,
                onClick = {
                    onPlanTypeChange(true)
                    searchQuery = ""
                    expanded = false
                },
                label = { Text(stringResource(R.string.model_catalog_free)) },
            )
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = !isFreePlan,
                enabled = enabled,
                onClick = {
                    onPlanTypeChange(false)
                    searchQuery = ""
                    expanded = false
                },
                label = { Text(stringResource(R.string.model_catalog_paid)) },
            )
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                if (enabled && !isLoading) expanded = true
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    stringResource(
                        if (providerType == ClientType.GOOGLE_AI_STUDIO) {
                            R.string.search_google_models
                        } else {
                            R.string.search_openrouter_live_models
                        }
                    )
                )
            },
            placeholder = { Text(stringResource(R.string.model_name_or_id)) },
            singleLine = true,
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
                label = { Text(stringResource(R.string.model)) },
                placeholder = { Text(stringResource(R.string.model_name)) },
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
                            DropdownMenuItem(
                                text = { ModelCatalogItem(model) },
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

        Text(
            text = stringResource(
                if (providerType == ClientType.OPEN_ROUTER) {
                    R.string.openrouter_live_catalog_note
                } else {
                    R.string.google_catalog_pricing_note
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelCatalogItem(model: OpenRouterModel) {
    val price = model.pricing
    val priceText = when {
        price?.isFree == true ->
            stringResource(R.string.model_price_free)

        price?.promptPricePerMillion != null &&
            price.completionPricePerMillion != null -> {
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = model.name ?: model.id,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (!model.name.isNullOrBlank()) {
            Text(
                text = model.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = priceText,
            style = MaterialTheme.typography.bodySmall,
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
