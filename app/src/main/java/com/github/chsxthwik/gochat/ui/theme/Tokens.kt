package com.github.chsxthwik.gochat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GoChat design tokens. Dark-first: the app is a developer terminal product —
 * one carefully tuned palette beats two half-tuned schemes. Every color,
 * type style, spacing step, and corner radius used by the UI lives here so
 * screens stay consistent and restyling touches a single file.
 */
object GoColors {
    // surfaces — deepest to most raised
    val Bg = Color(0xFF0A0A0E)          // app canvas
    val Surface = Color(0xFF121218)     // cards, sheets, bubbles
    val SurfaceHigh = Color(0xFF1A1A22) // pressed/raised elements, chips
    val SurfaceTop = Color(0xFF21212C)  // user bubble, hover

    // hairlines
    val Line = Color(0x14FFFFFF)        // borders, dividers
    val LineStrong = Color(0x29FFFFFF)  // visible separators

    // text
    val Text = Color(0xFFE8E8ED)        // primary
    val TextDim = Color(0xFF9D9DA9)     // secondary, metadata
    val TextFaint = Color(0xFF6B6B7A)   // captions, placeholders

    // accent — GoChat amber
    val Accent = Color(0xFFF5A524)
    val AccentSoft = Color(0x24F5A524)  // tinted background for active states
    val OnAccent = Color(0xFF171003)

    // semantic
    val Error = Color(0xFFE5484D)
    val ErrorSoft = Color(0x20E5484D)
    val Warn = Color(0xFFE8B93E)
    val WarnSoft = Color(0x1FE8B93E)
    val Ok = Color(0xFF4CC38A)
    val Info = Color(0xFF5B9BEE)

    // code
    val CodeBg = Color(0xFF0D0D13)
    val CodeText = Color(0xFFD5D5DE)

    // message roles
    val UserBubble = Color(0xFF1E1E2A)
}

/** Type scale — body text uses the product sans, labels/metadata use mono. */
object GoType {
    private val sans = FontFamily.SansSerif
    private val mono = FontFamily.Monospace

    val Display = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, color = GoColors.Text)
    val Headline = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = GoColors.Text)
    val Title = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = GoColors.Text)
    val TitleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = GoColors.Text)

    val Body = TextStyle(fontFamily = sans, fontSize = 15.5.sp, lineHeight = 23.sp, color = GoColors.Text)
    val BodyDim = TextStyle(fontFamily = sans, fontSize = 14.sp, lineHeight = 20.sp, color = GoColors.TextDim)
    val BodySmall = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp, color = GoColors.TextDim)

    val Label = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp, color = GoColors.TextDim)
    val LabelStrong = TextStyle(fontFamily = mono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp, color = GoColors.Accent)
    val Caption = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 15.sp, color = GoColors.TextFaint)
    val CaptionStrong = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, color = GoColors.TextDim)

    val Mono = TextStyle(fontFamily = mono, fontSize = 13.5.sp, lineHeight = 20.sp, color = GoColors.Text)
    val MonoDim = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 17.sp, color = GoColors.TextDim)
    val MonoAccent = TextStyle(fontFamily = mono, fontSize = 13.5.sp, lineHeight = 20.sp, color = GoColors.Accent)
    val Code = TextStyle(fontFamily = mono, fontSize = 13.sp, lineHeight = 19.sp, color = GoColors.CodeText)
    val Mark = TextStyle(fontFamily = mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = GoColors.Accent)
}

/** Spacing grid — pick the nearest step instead of inventing a value. */
object GoSpace {
    val Xxs = 2.dp
    val Xs = 4.dp
    val S = 8.dp
    val Sm = 10.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 20.dp
    val Xxl = 28.dp
    val Huge = 36.dp
}

/** Corner radii — flat-ish terminal aesthetic, rounded where it earns it. */
object GoShape {
    val Xs = 4.dp   // tiny chips, tags
    val S = 8.dp    // small buttons, summary strip
    val M = 12.dp   // cards, inputs, timeline
    val L = 14.dp   // text fields, big buttons
    val Xl = 18.dp  // user bubble
    val Pill = 100  // percent
}
