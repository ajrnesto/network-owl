package io.github.ajrnesto.networkowl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

data class NetworkOwlConfig(
    val theme: NetworkOwlTheme = NetworkOwlTheme(),
    val redactedHeaders: Set<String> = emptySet(),
    val redactedStorageKeys: Set<String> = emptySet(),
    val maxContentLength: Long = 250_000L,
    val capacity: Int = 300,
    val showNotification: Boolean = true,
    val showShortcut: Boolean = true,
)

data class NetworkOwlTheme(
    val colors: NetworkOwlColors = NetworkOwlColors(),
    val fonts: NetworkOwlFonts = NetworkOwlFonts(),
)

data class NetworkOwlColors(
    val primary: Color = Color(0xFF2563EB),
    val onPrimary: Color = Color(0xFFFFFFFF),
    val background: Color = Color(0xFFFFFFFF),
    val surface: Color = Color(0xFFF5F5F7),
    val onBackground: Color = Color(0xFF111111),
    val onBackgroundMuted: Color = Color(0xFF6B6B70),
    val divider: Color = Color(0xFFE5E5EA),
    val success: Color = Color(0xFF16A34A),
    val redirect: Color = Color(0xFF2563EB),
    val clientError: Color = Color(0xFFD97706),
    val serverError: Color = Color(0xFFDC2626),
    val pending: Color = Color(0xFF8E8E93),
    val codeBackground: Color = Color(0xFFF6F8FA),
    val jsonKey: Color = Color(0xFF0550AE),
    val jsonString: Color = Color(0xFF0A3069),
    val jsonNumber: Color = Color(0xFF953800),
    val jsonLiteral: Color = Color(0xFF8250DF),
)

data class NetworkOwlFonts(
    val text: FontFamily = FontFamily.Default,
    val code: FontFamily = FontFamily.Monospace,
)
