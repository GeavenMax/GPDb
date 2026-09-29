package com.gpdb.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppTheme(val id: String, val label: String, val isDark: Boolean) {
    AUTO("auto", "跟随系统", true),
    GLASS_DARK("glass-dark", "流体玻璃 · 暗", true),
    GLASS_LIGHT("glass-light", "流体玻璃 · 浅", false),
    CLASSIC_DARK("classic-dark", "经典 · 暗", true),
    CLASSIC_LIGHT("classic-light", "经典 · 浅", false),
    MY_DARK("my-dark", "Material You · 暗", true),
    MY_LIGHT("my-light", "Material You · 浅", false);

    companion object {
        fun fromId(id: String?): AppTheme {
            return when (id?.lowercase()) {
                "glass-dark" -> GLASS_DARK
                "glass-light" -> GLASS_LIGHT
                "classic-dark", "dark" -> CLASSIC_DARK
                "classic-light", "light" -> CLASSIC_LIGHT
                "my-dark" -> MY_DARK
                "my-light" -> MY_LIGHT
                else -> AUTO
            }
        }
    }
}

val GlassDarkColorScheme = darkColorScheme(
    primary = GlassDarkPrimary,
    secondary = GlassDarkSecondary,
    background = GlassDarkBg,
    surface = GlassDarkSurface,
    surfaceVariant = GlassDarkSurfaceVariant,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE2E4F0),
    onSurfaceVariant = Color(0xFF989CB8)
)

val GlassLightColorScheme = lightColorScheme(
    primary = GlassLightPrimary,
    secondary = GlassLightSecondary,
    background = GlassLightBg,
    surface = GlassLightSurface,
    surfaceVariant = GlassLightSurfaceVariant,
    onBackground = Color(0xFF141624),
    onSurface = Color(0xFF23263B),
    onSurfaceVariant = Color(0xFF6B7280)
)

val ClassicDarkColorScheme = darkColorScheme(
    primary = ClassicDarkPrimary,
    secondary = ClassicDarkSecondary,
    background = ClassicDarkBg,
    surface = ClassicDarkSurface,
    surfaceVariant = ClassicDarkSurfaceVariant,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFFA1A1AA)
)

val ClassicLightColorScheme = lightColorScheme(
    primary = ClassicLightPrimary,
    secondary = ClassicLightSecondary,
    background = ClassicLightBg,
    surface = ClassicLightSurface,
    surfaceVariant = ClassicLightSurfaceVariant,
    onBackground = Color(0xFF18181B),
    onSurface = Color(0xFF27272A),
    onSurfaceVariant = Color(0xFF71717A)
)

val MyDarkColorScheme = darkColorScheme(
    primary = MyDarkPrimary,
    secondary = MyDarkSecondary,
    background = MyDarkBg,
    surface = MyDarkSurface,
    surfaceVariant = MyDarkSurfaceVariant,
    onBackground = Color(0xFFFAF6F0),
    onSurface = Color(0xFFF0EAE1),
    onSurfaceVariant = Color(0xFFA59E92)
)

val MyLightColorScheme = lightColorScheme(
    primary = MyLightPrimary,
    secondary = MyLightSecondary,
    background = MyLightBg,
    surface = MyLightSurface,
    surfaceVariant = MyLightSurfaceVariant,
    onBackground = Color(0xFF201B14),
    onSurface = Color(0xFF322C24),
    onSurfaceVariant = Color(0xFF7E766D)
)

@Composable
fun GPDbTheme(
    themeChoice: String = "auto",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val theme = AppTheme.fromId(themeChoice)

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val isDark = if (theme == AppTheme.AUTO) isSystemDark else theme.isDark
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        theme == AppTheme.AUTO -> {
            if (isSystemDark) GlassDarkColorScheme else GlassLightColorScheme
        }
        theme == AppTheme.GLASS_DARK -> GlassDarkColorScheme
        theme == AppTheme.GLASS_LIGHT -> GlassLightColorScheme
        theme == AppTheme.CLASSIC_DARK -> ClassicDarkColorScheme
        theme == AppTheme.CLASSIC_LIGHT -> ClassicLightColorScheme
        theme == AppTheme.MY_DARK -> MyDarkColorScheme
        theme == AppTheme.MY_LIGHT -> MyLightColorScheme
        else -> GlassDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
