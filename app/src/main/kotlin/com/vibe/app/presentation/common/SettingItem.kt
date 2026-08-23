package com.vibe.app.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.app.R


@Composable
fun SettingItem(

    modifier: Modifier = Modifier,

    title: String,

    description: String? = null,

    enabled: Boolean = true,

    onItemClick: () -> Unit,

    showTrailingIcon: Boolean = true,

    showLeadingIcon: Boolean = false,

    leadingIcon: @Composable () -> Unit = {}

) {


    val itemModifier =
        modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        onClick = onItemClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp)



    val colors =
        ListItemDefaults.colors()



    ListItem(

        modifier = itemModifier,


        headlineContent = {

            Text(
                text = title,
                overflow = TextOverflow.Ellipsis
            )

        },


        supportingContent = {

            description?.let {

                Text(
                    text = it,
                    overflow = TextOverflow.Ellipsis
                )

            }

        },


        leadingContent =
            if (showLeadingIcon) {
                {
                    leadingIcon()
                }
            } else {
                null
            },


        trailingContent = {

            if (showTrailingIcon) {

                Icon(

                    imageVector =
                        ImageVector.vectorResource(
                            id = R.drawable.ic_round_arrow_right
                        ),

                    contentDescription =
                        stringResource(
                            R.string.arrow_icon
                        )

                )

            }

        },


        colors = ListItemDefaults.colors(

            headlineColor =
                if (enabled)
                    colors.headlineColor
                else
                    colors.disabledHeadlineColor,


            supportingColor =
                if (enabled)
                    colors.supportingTextColor
                else
                    colors.disabledHeadlineColor,


            trailingIconColor =
                if (enabled)
                    colors.trailingIconColor
                else
                    colors.disabledTrailingIconColor

        )

    )

}
