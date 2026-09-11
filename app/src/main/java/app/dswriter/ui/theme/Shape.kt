package app.dswriter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale. Softer and more generous than the Material baseline so panels, cards, and the
 * composer read as one deliberate surface family instead of stock rectangles.
 */
internal val DSShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully rounded shape for capsules, chips, and circular buttons. */
val PillShape = RoundedCornerShape(percent = 50)

/** Layout rhythm. Keeps padding consistent across every screen. */
object Spacing {
    val hairline = 2.dp
    val tiny = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val regular = 16.dp
    val large = 20.dp
    val xlarge = 28.dp
    val xxlarge = 40.dp
}

object Dimens {
    /** Minimum touch target; every interactive control must respect it. */
    val touchTarget = 48.dp
    val iconButton = 40.dp
    val messageAvatar = 32.dp
    val composerMaxHeight = 168.dp
    val contentMaxWidth = 720.dp
}
