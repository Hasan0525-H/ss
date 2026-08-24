package com.vibe.app.presentation.ui.setting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vibe.app.data.dto.OpenRouterModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDialog(
    dialogState: PlatformDialogState, // أو النوع المستخدم لحالة الحوار في مشروعك
    currentModel: String,
    settingViewModel: PlatformSettingViewModel
) {
    // تحقق مما إذا كان الحوار مفتوحاً (استبدل الشرط بما يناسب حقل الحالة لديك إذا لزم الأمر)
    // إذا كنت تستخدم نافذة حوار مخصصة، يمكنك تكييف الـ state هنا
    
    var isFreeOnly by remember { mutableStateOf(true) }
    var modelsList by remember { mutableStateOf<List<OpenRouterModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var expanded by remember { mutableStateOf(false) }

    // جلب النماذج عند تغيير نوع الفلتر (مجاني أو مدفوع)
    LaunchedEffect(isFreeOnly) {
        isLoading = true
        // استدعاء دالة الجلب من الـ ViewModel أو الـ API مباشرة
        modelsList = settingViewModel.fetchOpenRouterModels(isFreeOnly)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = { settingViewModel.closeApiModelDialog() },
        title = { Text("اختر النموذج (Model)") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // أزرار التبديل: مجاني / مدفوع
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isFreeOnly,
                        onClick = { isFreeOnly = true },
                        label = { Text("مجاني (Free)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isFreeOnly,
                        onClick = { isFreeOnly = false },
                        label = { Text("مدفوع (Paid)") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // القائمة المنسدلة لاختيار النموذج
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("النموذج المحدد") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (isLoading) {
                            DropdownMenuItem(
                                text = { 
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                },
                                onClick = {}
                            )
                        } else if (modelsList.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("لا توجد نماذج متاحة") },
                                onClick = {}
                            )
                        } else {
                            modelsList.forEach { model ->
                                val priceLabel = if (isFreeOnly) {
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
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = priceLabel, style = MaterialTheme.typography.bodySmall)
                                        }
                                    },
                                    onClick = {
                                        selectedModel = model.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    settingViewModel.updateModel(selectedModel)
                    settingViewModel.closeApiModelDialog()
                }
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { settingViewModel.closeApiModelDialog() }
            ) {
                Text("إلغاء")
            }
        }
    )
}
