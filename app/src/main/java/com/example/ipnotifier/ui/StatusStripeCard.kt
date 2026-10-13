package com.example.ipnotifier.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val StatusActiveColor = Color(0xFF43A047)
val StatusInactiveColor = Color(0xFFE53935)

fun statusColor(active: Boolean): Color =
    if (active) StatusActiveColor else StatusInactiveColor

/** [active] = null means the status is not known yet and the stripe stays neutral. */
@Composable
fun StatusStripeCard(
    active: Boolean?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stripeColor by animateColorAsState(
        targetValue = active?.let { statusColor(it) } ?: MaterialTheme.colorScheme.outlineVariant,
        label = "statusStripe",
    )
    val body: @Composable () -> Unit = {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(stripeColor),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    }
}
