package com.vibe.app.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformTopAppBar(
    title: String,
    onNavigationClick: () -> Unit,
    onDeleteClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier
) {

    var menuExpanded by remember {
        mutableStateOf(false)
    }


    LargeTopAppBar(

        title = {
            Text(
                text = title
            )
        },


        navigationIcon = {

            IconButton(
                onClick = onNavigationClick
            ) {

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null
                )
            }
        },


        actions = {

            IconButton(
                onClick = {
                    menuExpanded = true
                }
            ) {

                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null
                )
            }


            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = {
                    menuExpanded = false
                }
            ) {

                DropdownMenuItem(

                    text = {
                        Text(
                            text = "Delete"
                        )
                    },

                    onClick = {

                        menuExpanded = false

                        onDeleteClick()
                    }
                )
            }
        },


        scrollBehavior = scrollBehavior,

        modifier = modifier
    )
}
