package com.vibe.app.presentation.ui.components

import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


@Composable
fun PreferenceSwitchWithContainer(

    title: String,

    isChecked: Boolean,

    onCheckedChange: (Boolean) -> Unit,

    modifier: Modifier = Modifier

) {


    ListItem(

        headlineContent = {

            Text(
                text = title
            )

        },


        trailingContent = {

            Switch(

                checked = isChecked,

                onCheckedChange = onCheckedChange

            )

        },


        modifier = modifier

    )
}
