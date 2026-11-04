package io.github.ajrnesto.networkowl.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ajrnesto.networkowl.HttpTransaction
import io.github.ajrnesto.networkowl.NetworkOwlColors
import io.github.ajrnesto.networkowl.NetworkOwlTheme

private val LocalNetworkOwlTheme = staticCompositionLocalOf { NetworkOwlTheme() }

@Composable
internal fun InspectorTheme(theme: NetworkOwlTheme, content: @Composable () -> Unit) {
    val colors = theme.colors
    val scheme = lightColorScheme(
        primary = colors.primary,
        onPrimary = colors.onPrimary,
        primaryContainer = lerp(colors.background, colors.primary, 0.14f),
        onPrimaryContainer = colors.primary,
        secondary = colors.primary,
        onSecondary = colors.onPrimary,
        secondaryContainer = lerp(colors.background, colors.primary, 0.14f),
        onSecondaryContainer = colors.primary,
        tertiary = colors.primary,
        onTertiary = colors.onPrimary,
        tertiaryContainer = lerp(colors.background, colors.primary, 0.14f),
        onTertiaryContainer = colors.primary,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.background,
        onSurface = colors.onBackground,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.onBackgroundMuted,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surface,
        outline = colors.divider,
        outlineVariant = colors.divider,
        error = colors.serverError,
    )
    CompositionLocalProvider(LocalNetworkOwlTheme provides theme) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = expressiveShapes,
            typography = Typography().withFontFamily(theme.fonts.text),
            content = content,
        )
    }
}

internal object Inspector {
    val codeStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(fontFamily = LocalNetworkOwlTheme.current.fonts.code, fontSize = 13.sp, lineHeight = 18.sp)

    val colors: NetworkOwlColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNetworkOwlTheme.current.colors

    @Composable
    @ReadOnlyComposable
    fun statusColor(transaction: HttpTransaction): Color {
        val colors = LocalNetworkOwlTheme.current.colors
        return when (transaction.status) {
            HttpTransaction.Status.Pending -> colors.pending
            HttpTransaction.Status.Failed -> colors.serverError
            HttpTransaction.Status.Complete -> when (transaction.response?.code ?: 0) {
                in 200..299 -> colors.success
                in 300..399 -> colors.redirect
                in 400..499 -> colors.clientError
                else -> colors.serverError
            }
        }
    }
}

// The expressive corner scale, one step rounder than the baseline.
private val expressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Material's expressive spring tokens. MotionScheme.expressive() is still internal in material3 1.4.
internal object InspectorMotion {
    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> defaultSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> defaultEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
}

private fun Typography.withFontFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
