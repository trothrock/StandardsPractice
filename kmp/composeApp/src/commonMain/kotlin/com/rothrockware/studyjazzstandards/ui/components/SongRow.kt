package com.rothrockware.studyjazzstandards.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rothrockware.studyjazzstandards.ui.theme.JazzColors

/** Generic list row: leading badge, name + meta, trailing content. */
@Composable
fun ListRow(
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    name: String,
    nameBadge: (@Composable () -> Unit)? = null,
    meta: String? = null,
    trailing: (@Composable RowScopeAlias.() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(name, fontSize = 14.sp, color = JazzColors.Text)
                    nameBadge?.invoke()
                }
                if (!meta.isNullOrEmpty()) {
                    Text(meta, fontSize = 11.sp, color = JazzColors.Text3)
                }
            }
            trailing?.invoke(RowScopeAlias)
        }
        HorizontalDivider(color = JazzColors.Border, thickness = 1.dp)
    }
}

/** Marker object so trailing lambdas read clearly without exposing RowScope. */
object RowScopeAlias
