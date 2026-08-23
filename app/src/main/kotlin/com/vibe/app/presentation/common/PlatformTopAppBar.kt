package com.vibe.app.presentation.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
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

    TopAppBar(

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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },


        actions = {

            IconButton(
                onClick = onDeleteClick
            ) {

                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        },


        scrollBehavior = scrollBehavior,

        modifier = modifier
    )
}
