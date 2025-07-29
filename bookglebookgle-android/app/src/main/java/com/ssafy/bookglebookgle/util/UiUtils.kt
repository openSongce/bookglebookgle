package com.ssafy.bookglebookgle.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ScreenSize {

    val width: Dp
        @Composable get() = LocalConfiguration.current.screenWidthDp.dp

    val height: Dp
        @Composable get() = LocalConfiguration.current.screenHeightDp.dp
}
