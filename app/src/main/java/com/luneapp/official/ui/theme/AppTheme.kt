package com.luneapp.official.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luneapp.official.R
import com.luneapp.official.domain.settings.AppDarkMode

@Composable
private fun appTypography(): Typography {
    val comfortaa = FontFamily(
        Font(R.font.comfortaa_regular, FontWeight.Light),
        Font(R.font.comfortaa_regular, FontWeight.Normal),
        Font(R.font.comfortaa_regular, FontWeight.Medium),
        Font(R.font.comfortaa_regular, FontWeight.SemiBold),
        Font(R.font.comfortaa_regular, FontWeight.Bold),
    )
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = comfortaa),
        displayMedium = d.displayMedium.copy(fontFamily = comfortaa),
        displaySmall = d.displaySmall.copy(fontFamily = comfortaa),
        headlineLarge = d.headlineLarge.copy(
            fontFamily = comfortaa,
            fontWeight = FontWeight.Bold,
            fontSize = 56.sp,
            letterSpacing = (-2).sp
        ),
        headlineMedium = d.headlineMedium.copy(
            fontFamily = comfortaa,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            letterSpacing = (-1).sp
        ),
        headlineSmall = d.headlineSmall.copy(fontFamily = comfortaa),
        titleLarge = d.titleLarge.copy(fontFamily = comfortaa, fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontFamily = comfortaa, fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.copy(fontFamily = comfortaa),
        bodyLarge = d.bodyLarge.copy(fontFamily = comfortaa),
        bodyMedium = d.bodyMedium.copy(fontFamily = comfortaa),
        bodySmall = d.bodySmall.copy(fontFamily = comfortaa),
        labelLarge = d.labelLarge.copy(fontFamily = comfortaa),
        labelMedium = d.labelMedium.copy(fontFamily = comfortaa, fontWeight = FontWeight.SemiBold),
        labelSmall = d.labelSmall.copy(fontFamily = comfortaa),
    )
}

// ============================================================
// Expressive Shapes (fallback using RoundedCornerShape)
// ============================================================

/**
 * Holds expressive shapes as [Shape] for use with Modifier.clip().
 * Usage: `Modifier.clip(MaterialTheme.expressiveShapes.flower)`
 */
data class ExpressiveShapes(
    val circle: Shape = RoundedCornerShape(50),
    val square: Shape = RoundedCornerShape(0.dp),
    val slanted: Shape = RoundedCornerShape(12.dp),
    val arch: Shape = RoundedCornerShape(12.dp),
    val semicircle: Shape = RoundedCornerShape(12.dp),
    val oval: Shape = RoundedCornerShape(12.dp),
    val pill: Shape = RoundedCornerShape(50),
    val triangle: Shape = RoundedCornerShape(12.dp),
    val arrow: Shape = RoundedCornerShape(12.dp),
    val fan: Shape = RoundedCornerShape(12.dp),
    val diamond: Shape = RoundedCornerShape(12.dp),
    val clamShell: Shape = RoundedCornerShape(12.dp),
    val pentagon: Shape = RoundedCornerShape(12.dp),
    val gem: Shape = RoundedCornerShape(12.dp),
    val sunny: Shape = RoundedCornerShape(12.dp),
    val verySunny: Shape = RoundedCornerShape(12.dp),
    val cookie4: Shape = RoundedCornerShape(12.dp),
    val cookie6: Shape = RoundedCornerShape(12.dp),
    val cookie7: Shape = RoundedCornerShape(12.dp),
    val cookie9: Shape = RoundedCornerShape(12.dp),
    val cookie12: Shape = RoundedCornerShape(12.dp),
    val clover4: Shape = RoundedCornerShape(12.dp),
    val clover8: Shape = RoundedCornerShape(12.dp),
    val burst: Shape = RoundedCornerShape(12.dp),
    val softBurst: Shape = RoundedCornerShape(12.dp),
    val boom: Shape = RoundedCornerShape(12.dp),
    val softBoom: Shape = RoundedCornerShape(12.dp),
    val flower: Shape = RoundedCornerShape(50),
    val puffy: Shape = RoundedCornerShape(50),
    val puffyDiamond: Shape = RoundedCornerShape(12.dp),
    val ghostish: Shape = RoundedCornerShape(12.dp),
    val pixelCircle: Shape = RoundedCornerShape(50),
    val pixelTriangle: Shape = RoundedCornerShape(12.dp),
    val bun: Shape = RoundedCornerShape(50),
    val heart: Shape = RoundedCornerShape(12.dp),
)

val LocalExpressiveShapes = staticCompositionLocalOf { ExpressiveShapes() }

/** Access expressive shapes via MaterialTheme. */
val MaterialTheme.expressiveShapes: ExpressiveShapes
    @Composable get() = LocalExpressiveShapes.current

// ============================================================
// Theme composable
// ============================================================

@Composable
fun AppTheme(
    darkMode: AppDarkMode = AppDarkMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (darkMode) {
        AppDarkMode.DARK -> true
        AppDarkMode.LIGHT -> false
        AppDarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && isDarkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !isDarkTheme -> dynamicLightColorScheme(context)
        isDarkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    CompositionLocalProvider(LocalExpressiveShapes provides ExpressiveShapes()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
