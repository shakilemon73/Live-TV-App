package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Encapsulates the configuration of a Material Design 3 responsive layout grid.
 */
data class ResponsiveGridSpec(
    val columns: Int,
    val margin: Dp,
    val gutter: Dp,
    val maxContentWidth: Dp
)

/**
 * Remembers a [ResponsiveGridSpec] dynamically compiled against the screen configuration size class.
 */
@Composable
fun rememberResponsiveGridSpec(): ResponsiveGridSpec {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    return remember(screenWidth) {
        when {
            screenWidth < 600.dp -> ResponsiveGridSpec(
                columns = 2,
                margin = 16.dp,
                gutter = 12.dp,
                maxContentWidth = Dp.Unspecified
            )
            screenWidth < 840.dp -> ResponsiveGridSpec(
                columns = 4,
                margin = 24.dp,
                gutter = 16.dp,
                maxContentWidth = 680.dp
            )
            else -> ResponsiveGridSpec(
                columns = 6,
                margin = 32.dp,
                gutter = 20.dp,
                maxContentWidth = 1024.dp
            )
        }
    }
}

/**
 * A master container applying standard responsive grid margins and restricting maximum content width on wider devices.
 * Helps prevent the UI from looking overly stretched on tablets and desktop monitors.
 */
@Composable
fun ResponsiveGridFrame(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.(ResponsiveGridSpec) -> Unit
) {
    val spec = rememberResponsiveGridSpec()
    
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        val widthModifier = if (spec.maxContentWidth != Dp.Unspecified) {
            Modifier.widthIn(max = spec.maxContentWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        
        Box(
            modifier = widthModifier
                .fillMaxHeight()
                .padding(horizontal = spec.margin),
            contentAlignment = Alignment.TopCenter
        ) {
            content(spec)
        }
    }
}
