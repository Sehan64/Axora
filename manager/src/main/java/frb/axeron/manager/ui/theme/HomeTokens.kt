package frb.axeron.manager.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeTokens(colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme): HomeTokens {
    return HomeTokensImpl(
        // Semantic status colors
        statusRunning = colorScheme.primary,
        statusRunningContainer = colorScheme.primaryContainer,
        statusRunningOnContainer = colorScheme.onPrimaryContainer,
        statusStopped = colorScheme.error,
        statusStoppedContainer = colorScheme.errorContainer,
        statusStoppedOnContainer = colorScheme.onErrorContainer,
        statusUpdating = colorScheme.tertiary,
        statusUpdatingContainer = colorScheme.tertiaryContainer,
        statusUpdatingOnContainer = colorScheme.onTertiaryContainer,
        statusWarning = colorScheme.secondary,
        statusWarningContainer = colorScheme.secondaryContainer,
        statusWarningOnContainer = colorScheme.onSecondaryContainer,
        
        // Elevation layers for home
        heroSurface = colorScheme.surfaceContainerHighest,
        heroSurfaceVariant = colorScheme.surfaceContainerHigh,
        cardSurface = colorScheme.surfaceContainer,
        cardSurfaceHover = colorScheme.surfaceContainerHigh,
        cardSurfacePressed = colorScheme.surfaceContainerHighest,
        
        // Banner overlay
        bannerOverlayStart = Color.Black.copy(alpha = 0.5f),
        bannerOverlayMid = Color.Black.copy(alpha = 0.3f),
        bannerOverlayEnd = Color.Transparent,
        
        // Motion tokens
        motionDurationShort = 150,
        motionDurationMedium = 300,
        motionDurationLong = 500,
        motionEasingStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
        motionEasingEmphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f),
        motionEasingDecelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f),
        motionEasingAccelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f),
        
        // Spacing
        spacingXs = 4.dp,
        spacingSm = 8.dp,
        spacingMd = 16.dp,
        spacingLg = 24.dp,
        spacingXl = 32.dp,
        
        // Hero
        heroMinHeight = 200.dp,
        heroMaxHeight = 280.dp,
        heroContentPadding = 24.dp,
        heroBannerCornerRadius = 28.dp,
        
        // Cards
        cardCornerRadius = 20.dp,
        cardPadding = 20.dp,
        cardIconSize = 24.dp,
        metricCardMinWidth = 140.dp,
        
        // Typography scales for home
        heroAppNameSize = 28.sp,
        heroStatusSize = 22.sp,
        heroMetaSize = 14.sp,
        cardLabelSize = 14.sp,
        cardCountSize = 30.sp,
        infoLabelSize = 12.sp,
        infoValueSize = 14.sp,
        
        // Chip
        chipHeight = 40.dp,
        chipIconSize = 18.dp,
        chipCornerRadius = 100.dp,
        
        // Info section
        infoSectionCornerRadius = 20.dp,
        infoRowSpacing = 14.dp,
        infoIconSize = 20.dp,
        
        // Animation
        staggerDelayBase = 80L,
        staggerDelayStep = 100L,
        pulseMinScale = 1.0f,
        pulseMaxScale = 1.08f,
        pulseDuration = 1500
    )
}

interface HomeTokens {
    // Semantic status colors
    val statusRunning: Color
    val statusRunningContainer: Color
    val statusRunningOnContainer: Color
    val statusStopped: Color
    val statusStoppedContainer: Color
    val statusStoppedOnContainer: Color
    val statusUpdating: Color
    val statusUpdatingContainer: Color
    val statusUpdatingOnContainer: Color
    val statusWarning: Color
    val statusWarningContainer: Color
    val statusWarningOnContainer: Color
    
    // Elevation layers
    val heroSurface: Color
    val heroSurfaceVariant: Color
    val cardSurface: Color
    val cardSurfaceHover: Color
    val cardSurfacePressed: Color
    
    // Banner overlay
    val bannerOverlayStart: Color
    val bannerOverlayMid: Color
    val bannerOverlayEnd: Color
    
    // Motion
    val motionDurationShort: Int
    val motionDurationMedium: Int
    val motionDurationLong: Int
    val motionEasingStandard: Easing
    val motionEasingEmphasized: Easing
    val motionEasingDecelerate: Easing
    val motionEasingAccelerate: Easing
    
    // Spacing
    val spacingXs: androidx.compose.ui.unit.Dp
    val spacingSm: androidx.compose.ui.unit.Dp
    val spacingMd: androidx.compose.ui.unit.Dp
    val spacingLg: androidx.compose.ui.unit.Dp
    val spacingXl: androidx.compose.ui.unit.Dp
    
    // Hero
    val heroMinHeight: androidx.compose.ui.unit.Dp
    val heroMaxHeight: androidx.compose.ui.unit.Dp
    val heroContentPadding: androidx.compose.ui.unit.Dp
    val heroBannerCornerRadius: androidx.compose.ui.unit.Dp
    
    // Cards
    val cardCornerRadius: androidx.compose.ui.unit.Dp
    val cardPadding: androidx.compose.ui.unit.Dp
    val cardIconSize: androidx.compose.ui.unit.Dp
    val metricCardMinWidth: androidx.compose.ui.unit.Dp
    
    // Typography
    val heroAppNameSize: TextUnit
    val heroStatusSize: TextUnit
    val heroMetaSize: TextUnit
    val cardLabelSize: TextUnit
    val cardCountSize: TextUnit
    val infoLabelSize: TextUnit
    val infoValueSize: TextUnit
    
    // Chip
    val chipHeight: androidx.compose.ui.unit.Dp
    val chipIconSize: androidx.compose.ui.unit.Dp
    val chipCornerRadius: androidx.compose.ui.unit.Dp
    
    // Info section
    val infoSectionCornerRadius: androidx.compose.ui.unit.Dp
    val infoRowSpacing: androidx.compose.ui.unit.Dp
    val infoIconSize: androidx.compose.ui.unit.Dp
    
    // Animation
    val staggerDelayBase: Long
    val staggerDelayStep: Long
    val pulseMinScale: Float
    val pulseMaxScale: Float
    val pulseDuration: Int
}

internal class HomeTokensImpl(
    override val statusRunning: Color,
    override val statusRunningContainer: Color,
    override val statusRunningOnContainer: Color,
    override val statusStopped: Color,
    override val statusStoppedContainer: Color,
    override val statusStoppedOnContainer: Color,
    override val statusUpdating: Color,
    override val statusUpdatingContainer: Color,
    override val statusUpdatingOnContainer: Color,
    override val statusWarning: Color,
    override val statusWarningContainer: Color,
    override val statusWarningOnContainer: Color,
    override val heroSurface: Color,
    override val heroSurfaceVariant: Color,
    override val cardSurface: Color,
    override val cardSurfaceHover: Color,
    override val cardSurfacePressed: Color,
    override val bannerOverlayStart: Color,
    override val bannerOverlayMid: Color,
    override val bannerOverlayEnd: Color,
    override val motionDurationShort: Int,
    override val motionDurationMedium: Int,
    override val motionDurationLong: Int,
    override val motionEasingStandard: Easing,
    override val motionEasingEmphasized: Easing,
    override val motionEasingDecelerate: Easing,
    override val motionEasingAccelerate: Easing,
    override val spacingXs: androidx.compose.ui.unit.Dp,
    override val spacingSm: androidx.compose.ui.unit.Dp,
    override val spacingMd: androidx.compose.ui.unit.Dp,
    override val spacingLg: androidx.compose.ui.unit.Dp,
    override val spacingXl: androidx.compose.ui.unit.Dp,
    override val heroMinHeight: androidx.compose.ui.unit.Dp,
    override val heroMaxHeight: androidx.compose.ui.unit.Dp,
    override val heroContentPadding: androidx.compose.ui.unit.Dp,
    override val heroBannerCornerRadius: androidx.compose.ui.unit.Dp,
    override val cardCornerRadius: androidx.compose.ui.unit.Dp,
    override val cardPadding: androidx.compose.ui.unit.Dp,
    override val cardIconSize: androidx.compose.ui.unit.Dp,
    override val metricCardMinWidth: androidx.compose.ui.unit.Dp,
    override val heroAppNameSize: TextUnit,
    override val heroStatusSize: TextUnit,
    override val heroMetaSize: TextUnit,
    override val cardLabelSize: TextUnit,
    override val cardCountSize: TextUnit,
    override val infoLabelSize: TextUnit,
    override val infoValueSize: TextUnit,
    override val chipHeight: androidx.compose.ui.unit.Dp,
    override val chipIconSize: androidx.compose.ui.unit.Dp,
    override val chipCornerRadius: androidx.compose.ui.unit.Dp,
    override val infoSectionCornerRadius: androidx.compose.ui.unit.Dp,
    override val infoRowSpacing: androidx.compose.ui.unit.Dp,
    override val infoIconSize: androidx.compose.ui.unit.Dp,
    override val staggerDelayBase: Long,
    override val staggerDelayStep: Long,
    override val pulseMinScale: Float,
    override val pulseMaxScale: Float,
    override val pulseDuration: Int
) : HomeTokens