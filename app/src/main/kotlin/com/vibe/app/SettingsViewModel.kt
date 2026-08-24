package com.vibe.app.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionScreen(
    apiKey: String,
    viewModel: SettingsViewModel,
    onModelSelected: (String) -> Unit = {}
) {

    val models by viewModel.models.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFreeOnly by viewModel.isFreeFilter.collectAsState()


    var expanded by remember {
        mutableStateOf(false)
    }


    var selectedModel by remember {
        mutableStateOf("")
    }


    LaunchedEffect(apiKey) {

        if (apiKey.isNotBlank()) {

            viewModel.fetchModels(
                apiKey = apiKey,
                isFreeOnly = true
            )

        }
    }


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {


        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {


            FilterChip(
                selected = isFreeOnly,

                onClick = {

                    viewModel.fetchModels(
                        apiKey = apiKey,
                        isFreeOnly = true
                    )

                },

                label = {
                    Text("مجاني Free")
                },

                modifier = Modifier.weight(1f)
            )



            FilterChip(
                selected = !isFreeOnly,

                onClick = {

                    viewModel.fetchModels(
                        apiKey = apiKey,
                        isFreeOnly = false
                    )

                },

                label = {
                    Text("مدفوع Paid")
                },

                modifier = Modifier.weight(1f)
            )

        }



        ExposedDropdownMenuBox(
            expanded = expanded,

            onExpandedChange = {
                expanded = !expanded
            }

        ) {


            OutlinedTextField(

                value = selectedModel,

                onValueChange = {},

                readOnly = true,

                label = {
                    Text("Model")
                },


                trailingIcon = {

                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = expanded
                    )

                },


                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()

            )



            ExposedDropdownMenu(

                expanded = expanded,

                onDismissRequest = {
                    expanded = false
                }

            ) {


                when {


                    isLoading -> {


                        DropdownMenuItem(

                            text = {

                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp)
                                )

                            },

                            onClick = {}

                        )


                    }



                    models.isEmpty() -> {


                        DropdownMenuItem(

                            text = {

                                Text(
                                    "لا توجد موديلات"
                                )

                            },

                            onClick = {}

                        )


                    }



                    else -> {


                        models.forEach { model ->


                            DropdownMenuItem(

                                text = {


                                    Text(
                                        text = model.id
                                    )


                                },


                                onClick = {


                                    selectedModel = model.id

                                    expanded = false


                                    onModelSelected(
                                        model.id
                                    )


                                }

                            )


                        }


                    }


                }


            }


        }


    }

}
