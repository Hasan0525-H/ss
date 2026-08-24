package com.vibe.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    apiKey: String,
    viewModel: SettingsViewModel
) {
    val models by viewModel.models.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFreeFilter by viewModel.isFreeFilter.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    var selectedModelId by remember { mutableStateOf("") }

    // جلب الموديلات المجانية تلقائياً فور توفر مفتاح API
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            viewModel.fetchModels(apiKey = apiKey, isFreeOnly = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // أزرار التبديل الفوري بين المجاني والمدفوع
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isFreeFilter,
                onClick = { viewModel.fetchModels(apiKey, isFreeOnly = true) },
                label = { Text("مجاني (Free)") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = !isFreeFilter,
                onClick = { viewModel.fetchModels(apiKey, isFreeOnly = false) },
                label = { Text("مدفوع (Paid)") },
                modifier = Modifier.weight(1f)
            )
        }

        // القائمة المنسدلة لاختيار الموديل المطلوبة
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedModelId,
                onValueChange = { selectedModelId = it },
                label = { Text("Model") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (isLoading) {
                    DropdownMenuItem(
                        text = { CircularProgressIndicator(modifier = Modifier.size(24.dp)) },
                        onClick = {}
                    )
                } else if (models.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("لا توجد موديلات متاحة") },
                        onClick = {}
                    )
                } else {
                    models.forEach { model ->
                        val priceLabel = if (isFreeFilter) {
                            "مجاني"
                        } else {
                            "$${model.pricing?.averagePrice ?: 0.0}/1K"
                        }

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = model.id, modifier = Modifier.weight(1f))
                                    Text(text = priceLabel, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                selectedModelId = model.id
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
