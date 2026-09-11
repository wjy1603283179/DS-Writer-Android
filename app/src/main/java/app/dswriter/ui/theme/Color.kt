package app.dswriter.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * The product is a writing surface, so the neutral ramp is warm rather than the default
 * grey-violet Material baseline. Every role the app actually uses is defined explicitly;
 * leaving a role unset would fall back to the stock purple baseline scheme.
 */
private val Indigo10 = Color(0xFF0B1020)
private val Indigo20 = Color(0xFF16203C)
private val Indigo30 = Color(0xFF23305C)
private val Indigo40 = Color(0xFF2F4180)
private val Indigo60 = Color(0xFF5A6FD4)
private val Indigo80 = Color(0xFFB4C0F5)
private val Indigo90 = Color(0xFFDCE2FF)

private val Teal10 = Color(0xFF04201E)
private val Teal20 = Color(0xFF0B3733)
private val Teal30 = Color(0xFF14514B)
private val Teal40 = Color(0xFF1D6C63)
private val Teal80 = Color(0xFF84D5C9)
private val Teal90 = Color(0xFFB8EDE4)

private val Amber10 = Color(0xFF2A1A05)
private val Amber20 = Color(0xFF452B0B)
private val Amber30 = Color(0xFF664113)
private val Amber40 = Color(0xFF8A571B)
private val Amber80 = Color(0xFFF0BE7C)
private val Amber90 = Color(0xFFFFDFB4)

private val Plum10 = Color(0xFF25102A)
private val Plum20 = Color(0xFF3D1B44)
private val Plum30 = Color(0xFF5C2B66)
private val Plum40 = Color(0xFF7C3C88)
private val Plum80 = Color(0xFFE4B4EC)
private val Plum90 = Color(0xFFF6D9FB)

// Warm neutral ramp: keeps long-form reading comfortable instead of clinical grey.
private val Warm00 = Color(0xFFFBFAF7)
private val Warm04 = Color(0xFFF5F3EE)
private val Warm10 = Color(0xFFE9E6DF)
private val Warm20 = Color(0xFFD6D2C8)
private val Warm40 = Color(0xFF8E8A7F)
private val Warm60 = Color(0xFF5E5B53)
private val Warm80 = Color(0xFF3A3833)
private val Warm87 = Color(0xFF26251F)
private val Warm92 = Color(0xFF1B1A16)
private val Warm96 = Color(0xFF121110)

private val ErrorLight = Color(0xFFB3261E)
private val ErrorContainerLight = Color(0xFFF9DEDC)
private val OnErrorContainerLight = Color(0xFF410E0B)
private val ErrorDark = Color(0xFFF2B8B5)
private val ErrorContainerDark = Color(0xFF601410)
private val OnErrorContainerDark = Color(0xFFF9DEDC)

internal val LightColorScheme = lightColorScheme(
    primary = Indigo40,
    onPrimary = Color.White,
    primaryContainer = Indigo90,
    onPrimaryContainer = Indigo10,
    inversePrimary = Indigo80,

    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,

    tertiary = Plum40,
    onTertiary = Color.White,
    tertiaryContainer = Plum90,
    onTertiaryContainer = Plum10,

    surfaceTint = Indigo40,
    surfaceBright = Warm00,
    surfaceDim = Warm10,

    background = Warm04,
    onBackground = Warm92,
    surface = Warm00,
    onSurface = Warm92,
    surfaceVariant = Warm04,
    onSurfaceVariant = Warm60,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F6F1),
    surfaceContainer = Warm04,
    surfaceContainerHigh = Color(0xFFF1EEE7),
    surfaceContainerHighest = Warm10,

    inverseSurface = Warm80,
    inverseOnSurface = Warm00,

    outline = Warm40,
    outlineVariant = Warm20,

    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,

    scrim = Color.Black,
)

internal val DarkColorScheme = darkColorScheme(
    primary = Indigo80,
    onPrimary = Indigo10,
    primaryContainer = Indigo30,
    onPrimaryContainer = Indigo90,
    inversePrimary = Indigo40,

    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,

    tertiary = Plum80,
    onTertiary = Plum10,
    tertiaryContainer = Plum30,
    onTertiaryContainer = Plum90,

    surfaceTint = Indigo80,
    surfaceBright = Warm87,
    surfaceDim = Warm96,

    background = Warm96,
    onBackground = Warm10,
    surface = Warm92,
    onSurface = Warm10,
    surfaceVariant = Warm87,
    onSurfaceVariant = Warm20,
    surfaceContainerLowest = Color(0xFF0D0C0B),
    surfaceContainerLow = Color(0xFF161513),
    surfaceContainer = Warm92,
    surfaceContainerHigh = Warm87,
    surfaceContainerHighest = Color(0xFF33312B),

    inverseSurface = Warm10,
    inverseOnSurface = Warm92,

    outline = Warm40,
    outlineVariant = Warm80,

    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,

    scrim = Color.Black,
)

/**
 * Extended roles Material 3 does not define but the product needs:
 * a queued-task accent and a local-model accent.
 */
data class DSAccentColors(
    val queued: Color,
    val queuedContainer: Color,
    val onQueuedContainer: Color,
    val local: Color,
    val localContainer: Color,
    val onLocalContainer: Color,
)

internal val LightAccents = DSAccentColors(
    queued = Amber40,
    queuedContainer = Amber90,
    onQueuedContainer = Amber10,
    local = Teal40,
    localContainer = Teal90,
    onLocalContainer = Teal10,
)

internal val DarkAccents = DSAccentColors(
    queued = Amber80,
    queuedContainer = Amber30,
    onQueuedContainer = Amber90,
    local = Teal80,
    localContainer = Teal30,
    onLocalContainer = Teal90,
)
