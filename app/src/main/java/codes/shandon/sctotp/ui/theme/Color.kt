package codes.shandon.sctotp.ui.theme

import androidx.compose.ui.graphics.Color

// Terminal-candy palette, after Bitbase.sh (hot pink + cyan logo, lavender -> teal loader)
// and the Charm family of TUI tools.

// Accents
val HotPink = Color(0xFFFF69B4)
val HotPinkDeep = Color(0xFFD63A8F)
val Cyan = Color(0xFF00FFFF)
val CyanDeep = Color(0xFF00A8B5)
val Lavender = Color(0xFF9B99FE)
val LavenderDeep = Color(0xFF5F5BD9)
val Teal = Color(0xFF2BC8B7)
val TealDeep = Color(0xFF0F8F82)
val Butter = Color(0xFFFFD866)
val Coral = Color(0xFFFF5F7E)
val CoralDeep = Color(0xFFD9304F)
val Sky = Color(0xFF6FB7FF)

// Dark neutrals
val Ink = Color(0xFF16151C)
val InkRaised = Color(0xFF1E1D26)
val InkHigh = Color(0xFF282733)
val InkHighest = Color(0xFF33313F)
val InkLine = Color(0xFF4A4858)
val Mist = Color(0xFFA9A6BB)
val Cream = Color(0xFFFFFDF5)

// Light neutrals
val Paper = Color(0xFFFFF8F0)
val PaperRaised = Color(0xFFFFFFFF)
val PaperHigh = Color(0xFFFBF0E6)
val PaperHighest = Color(0xFFF3E4D8)
val PaperLine = Color(0xFFD9C8BC)
val Graphite = Color(0xFF6B6878)

// Per-account accent colors; each account picks one from its issuer name
val AccountColors = listOf(HotPink, Lavender, Teal, Butter, Coral, Sky)
