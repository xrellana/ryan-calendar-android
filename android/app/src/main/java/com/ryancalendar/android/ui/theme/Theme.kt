package com.ryancalendar.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppColors {
    val CanvasBg = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF1F5F9)
    val RestDayBg = Color(0xFFF0F2F5)
    val BorderSubtle = Color(0xFFE5E7EB)
    val BorderStrong = Color(0xFFCBD5E1)
    val TextMain = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF475569)
    val TextMuted = Color(0xFF64748B)
    val Primary = Color(0xFF2563EB)
    val PrimarySoft = Color(0xFFEFF6FF)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFEF2F2)
    val HolidayBadgeBg = Color(0xFFFEE2E2)
    val HolidayBadgeText = Color(0xFFDC2626)
    val WorkdayBadgeBg = Color(0xFFE2E8F0)
    val WorkdayBadgeText = Color(0xFF334155)
    val Success = Color(0xFF16A34A)
}

private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AppColors.PrimarySoft,
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = AppColors.TextMain,
    background = AppColors.CanvasBg,
    onBackground = AppColors.TextMain,
    surface = AppColors.Surface,
    onSurface = AppColors.TextMain,
    surfaceVariant = AppColors.SurfaceVariant,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.BorderStrong,
    outlineVariant = AppColors.BorderSubtle,
    error = AppColors.Danger,
    onError = Color.White,
    errorContainer = AppColors.DangerSoft,
    onErrorContainer = AppColors.Danger
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMain),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = AppColors.TextMain),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = AppColors.TextMain),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = AppColors.TextSecondary),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun RyanCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
