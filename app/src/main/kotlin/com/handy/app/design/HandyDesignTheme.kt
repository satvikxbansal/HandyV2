package com.handy.app.design

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHandyDesignColors = staticCompositionLocalOf { HandyDesign.Colors }
val LocalHandyDesignDimens = staticCompositionLocalOf { HandyDesign.Dimens }
val LocalHandyDesignType = staticCompositionLocalOf { HandyDesignType }

@Composable
fun HandyDesignTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides HandyDesign.Colors.TextPrimary,
        LocalHandyDesignColors provides HandyDesign.Colors,
        LocalHandyDesignDimens provides HandyDesign.Dimens,
        LocalHandyDesignType provides HandyDesignType,
        content = content,
    )
}
