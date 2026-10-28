package com.prismde.core.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shapes specification.
 * Provides organic, rounded geometry with grouped item corners
 * and expressive squircles for modern cards, sheets, and docks.
 */
object ExpressiveShapes {
    // Grouped (clustered) container shapes for settings & lists
    val GroupedItemTop = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp
    )
    val GroupedItemMiddle = RoundedCornerShape(4.dp)
    val GroupedItemBottom = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 4.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    val GroupedItemSingle = RoundedCornerShape(24.dp)

    // Component shapes
    val CardShape = RoundedCornerShape(24.dp)
    val CardSmallShape = RoundedCornerShape(16.dp)
    val SquircleShape = RoundedCornerShape(20.dp)
    val TabShape = RoundedCornerShape(16.dp)
    val FloatingDockShape = RoundedCornerShape(32.dp)
    val FloatingBarShape = CircleShape
    val PillShape = RoundedCornerShape(50)
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}
