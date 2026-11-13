package codes.shandon.sctotp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import codes.shandon.sctotp.R

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

// Everything in IBM Plex Mono, keeping Material's sizes
val Typography = Typography().run {
    fun TextStyle.mono() = copy(fontFamily = PlexMono)
    Typography(
        displayLarge = displayLarge.mono(),
        displayMedium = displayMedium.mono(),
        displaySmall = displaySmall.mono(),
        headlineLarge = headlineLarge.mono(),
        headlineMedium = headlineMedium.mono().copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.mono().copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.mono().copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.mono().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.mono(),
        bodyLarge = bodyLarge.mono(),
        bodyMedium = bodyMedium.mono(),
        bodySmall = bodySmall.mono(),
        labelLarge = labelLarge.mono().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.mono().copy(fontWeight = FontWeight.Bold),
        labelSmall = labelSmall.mono()
    )
}
