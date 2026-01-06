package com.lonx.lyrico.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.lonx.lyrico.ui.components.Indication.AlphaIndication
import com.moriafly.salt.ui.SaltConfigs
import com.moriafly.salt.ui.SaltTheme

@Composable
fun LyricoTheme(
    content: @Composable () -> Unit
) {

    SaltTheme(
        configs = SaltConfigs(
            isDarkTheme = isSystemInDarkTheme(),
            indication = AlphaIndication
        ),
        content = content
    )
}