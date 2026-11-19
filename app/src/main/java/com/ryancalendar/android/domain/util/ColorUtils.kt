package com.ryancalendar.android.domain.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 1:1 移植 Web 端 js/color.js 的 28 色板与 WCAG 对比度自适应配色算法
 */
object ColorUtils {

    val PresetColors: List<String> = listOf(
        "#D93A3A", "#E11D48", "#EA580C", "#D97A12", "#EAB308", "#FACC15", "#84CC16",
        "#2E9E5B", "#059669", "#0D9488", "#06B6D4", "#0284C7", "#2B6CD4", "#4F46E5",
        "#7C3AED", "#9333EA", "#C026D3", "#DB2777", "#9F1239", "#B45309", "#A16207",
        "#D6C29F", "#94A3B8", "#64748B", "#475569", "#334155", "#1E293B", "#0F766E"
    )

    data class EventColorStyle(
        val primary: Color,
        val border: Color,
        val text: Color,
        val softBg: Color,
        val chipBg: Color
    )

    fun normalizeHex(hex: String?): String {
        if (hex.isNullOrBlank()) return "#64748B"
        var clean = hex.trim().removePrefix("#")
        if (clean.length == 3 && clean.all { it.isHexDigit() }) {
            clean = "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
        }
        if (clean.length != 6 || !clean.all { it.isHexDigit() }) return "#64748B"
        return "#${clean.uppercase()}"
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun parseColor(hex: String?): Color {
        val norm = normalizeHex(hex).removePrefix("#")
        val r = norm.substring(0, 2).toInt(16)
        val g = norm.substring(2, 4).toInt(16)
        val b = norm.substring(4, 6).toInt(16)
        return Color(r, g, b)
    }

    private fun hexToRgb(hex: String): Triple<Int, Int, Int> {
        val norm = normalizeHex(hex).removePrefix("#")
        return Triple(
            norm.substring(0, 2).toInt(16),
            norm.substring(2, 4).toInt(16),
            norm.substring(4, 6).toInt(16)
        )
    }

    private fun rgbToHex(r: Double, g: Double, b: Double): String {
        fun clamp(v: Double) = v.roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(clamp(r), clamp(g), clamp(b))
    }

    private fun rgbToHsl(rInt: Int, gInt: Int, bInt: Int): Triple<Double, Double, Double> {
        val r = rInt / 255.0
        val g = gInt / 255.0
        val b = bInt / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2.0
        var h = 0.0
        var s = 0.0
        if (mx != mn) {
            val d = mx - mn
            s = if (l > 0.5) d / (2.0 - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
                g -> (b - r) / d + 2.0
                else -> (r - g) / d + 4.0
            }
            h /= 6.0
        }
        return Triple(h * 360.0, s * 100.0, l * 100.0)
    }

    private fun hslToHex(hDeg: Double, sPct: Double, lPct: Double): String {
        val h = ((hDeg % 360.0) + 360.0) % 360.0
        val s = sPct.coerceIn(0.0, 100.0) / 100.0
        val l = lPct.coerceIn(0.0, 100.0) / 100.0
        val c = (1.0 - abs(2.0 * l - 1.0)) * s
        val x = c * (1.0 - abs(((h / 60.0) % 2.0) - 1.0))
        val m = l - c / 2.0
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0.0)
            h < 120 -> Triple(x, c, 0.0)
            h < 180 -> Triple(0.0, c, x)
            h < 240 -> Triple(0.0, x, c)
            h < 300 -> Triple(x, 0.0, c)
            else -> Triple(c, 0.0, x)
        }
        return rgbToHex((r + m) * 255.0, (g + m) * 255.0, (b + m) * 255.0)
    }

    private fun relativeLuminance(hex: String): Double {
        val (r, g, b) = hexToRgb(hex)
        fun linear(v: Int): Double {
            val x = v / 255.0
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    private fun contrastWithWhite(hex: String): Double {
        val l = relativeLuminance(hex)
        return (1.0 + 0.05) / (l + 0.05)
    }

    private fun readableHex(hex: String, minAcceptable: Double, targetContrast: Double): String {
        val norm = normalizeHex(hex)
        if (contrastWithWhite(norm) >= minAcceptable) return norm
        val (r, g, b) = hexToRgb(norm)
        val (h, s, l) = rgbToHsl(r, g, b)
        val sat = if (s > 8.0) max(s, min(82.0, s * 1.1)) else s
        var low = 4.0
        var high = l
        var best = hslToHex(h, sat, low)
        repeat(16) {
            val mid = (low + high) / 2.0
            val cand = hslToHex(h, sat, mid)
            if (contrastWithWhite(cand) >= targetContrast) {
                best = cand
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    fun getEventColorStyle(hex: String?): EventColorStyle {
        val norm = normalizeHex(hex)
        val primary = parseColor(norm)
        val border = parseColor(readableHex(norm, 2.2, 3.2))
        val text = parseColor(readableHex(norm, 3.0, 4.5))
        return EventColorStyle(
            primary = primary,
            border = border,
            text = text,
            softBg = primary.copy(alpha = 0.11f),
            chipBg = primary.copy(alpha = 0.13f)
        )
    }
}
