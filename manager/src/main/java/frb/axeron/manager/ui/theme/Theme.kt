package frb.axeron.manager.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import frb.axeron.manager.ui.viewmodel.SettingsViewModel

val LocalHomeTokens = staticCompositionLocalOf<HomeTokens> { HomeTokensDefaults() }

@Composable
fun HomeTokens(): HomeTokens = LocalHomeTokens.current

private class HomeTokensDefaults : HomeTokens {
    override val statusRunning: Color = Color.Unspecified
    override val statusRunningContainer: Color = Color.Unspecified
    override val statusRunningOnContainer: Color = Color.Unspecified
    override val statusStopped: Color = Color.Unspecified
    override val statusStoppedContainer: Color = Color.Unspecified
    override val statusStoppedOnContainer: Color = Color.Unspecified
    override val statusUpdating: Color = Color.Unspecified
    override val statusUpdatingContainer: Color = Color.Unspecified
    override val statusUpdatingOnContainer: Color = Color.Unspecified
    override val statusWarning: Color = Color.Unspecified
    override val statusWarningContainer: Color = Color.Unspecified
    override val statusWarningOnContainer: Color = Color.Unspecified
    override val heroSurface: Color = Color.Unspecified
    override val heroSurfaceVariant: Color = Color.Unspecified
    override val cardSurface: Color = Color.Unspecified
    override val cardSurfaceHover: Color = Color.Unspecified
    override val cardSurfacePressed: Color = Color.Unspecified
    override val bannerOverlayStart: Color = Color.Unspecified
    override val bannerOverlayMid: Color = Color.Unspecified
    override val bannerOverlayEnd: Color = Color.Unspecified
    override val motionDurationShort: Int = 150
    override val motionDurationMedium: Int = 300
    override val motionDurationLong: Int = 500
    override val motionEasingStandard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    override val motionEasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    override val motionEasingDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    override val motionEasingAccelerate: Easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    override val spacingXs: androidx.compose.ui.unit.Dp = 4.dp
    override val spacingSm: androidx.compose.ui.unit.Dp = 8.dp
    override val spacingMd: androidx.compose.ui.unit.Dp = 16.dp
    override val spacingLg: androidx.compose.ui.unit.Dp = 24.dp
    override val spacingXl: androidx.compose.ui.unit.Dp = 32.dp
    override val heroMinHeight: androidx.compose.ui.unit.Dp = 200.dp
    override val heroMaxHeight: androidx.compose.ui.unit.Dp = 280.dp
    override val heroContentPadding: androidx.compose.ui.unit.Dp = 24.dp
    override val heroBannerCornerRadius: androidx.compose.ui.unit.Dp = 28.dp
    override val cardCornerRadius: androidx.compose.ui.unit.Dp = 20.dp
    override val cardPadding: androidx.compose.ui.unit.Dp = 20.dp
    override val cardIconSize: androidx.compose.ui.unit.Dp = 24.dp
    override val metricCardMinWidth: androidx.compose.ui.unit.Dp = 140.dp
    override val heroAppNameSize: TextUnit = 28.sp
    override val heroStatusSize: TextUnit = 22.sp
    override val heroMetaSize: TextUnit = 14.sp
    override val cardLabelSize: TextUnit = 14.sp
    override val cardCountSize: TextUnit = 30.sp
    override val infoLabelSize: TextUnit = 12.sp
    override val infoValueSize: TextUnit = 14.sp
    override val chipHeight: androidx.compose.ui.unit.Dp = 40.dp
    override val chipIconSize: androidx.compose.ui.unit.Dp = 18.dp
    override val chipCornerRadius: androidx.compose.ui.unit.Dp = 100.dp
    override val infoSectionCornerRadius: androidx.compose.ui.unit.Dp = 20.dp
    override val infoRowSpacing: androidx.compose.ui.unit.Dp = 14.dp
    override val infoIconSize: androidx.compose.ui.unit.Dp = 20.dp
    override val staggerDelayBase: Long = 80L
    override val staggerDelayStep: Long = 100L
    override val pulseMinScale: Float = 1.0f
    override val pulseMaxScale: Float = 1.08f
    override val pulseDuration: Int = 1500
}

fun hexToColor(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    val cleanDefault = basePrimaryDefault.toHexString().removePrefix("#")
    return when (cleanHex.length) {
        6 -> Color(("FF$cleanHex").toLong(16))
        8 -> Color(cleanHex.toLong(16))
        else -> Color(cleanDefault.toLong(16))
    }
}

@Composable
fun AxManagerTheme(
    // Dynamic color is available on Android 12+ ,
    contentCompose: @Composable (SettingsViewModel) -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel<SettingsViewModel>()

    val darkTheme = when (settingsViewModel.getAppThemeId) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }

    val dynamicColor = settingsViewModel.isDynamicColorEnabled
    val customPrimaryColor =
        hexToColor(settingsViewModel.customPrimaryColorHex)
    val customSecondaryColor = settingsViewModel.customSecondaryColorHex?.let { hexToColor(it) }
    val customTertiaryColor = settingsViewModel.customTertiaryColorHex?.let { hexToColor(it) }
    val accentIntensity = settingsViewModel.accentIntensity
    val isAmoled = settingsViewModel.isAmoledEnabled
    val cornerStyle = settingsViewModel.cornerStyle
    val context = LocalContext.current

    val colorScheme = remember(
        dynamicColor,
        darkTheme,
        customPrimaryColor,
        customSecondaryColor,
        customTertiaryColor,
        accentIntensity,
        isAmoled,
        context
    ) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                when {
                    darkTheme -> dynamicDarkColorScheme(context)
                    else -> dynamicLightColorScheme(context)
                }
            }

            darkTheme -> getVortexDarkColorScheme(
                customColor = customPrimaryColor,
                secondaryOverride = customSecondaryColor,
                tertiaryOverride = customTertiaryColor,
                intensity = accentIntensity,
                amoled = isAmoled
            )
            else -> getVortexLightColorScheme(
                customColor = customPrimaryColor,
                secondaryOverride = customSecondaryColor,
                tertiaryOverride = customTertiaryColor,
                intensity = accentIntensity
            )
        }
    }

    val shapes = remember(cornerStyle) { getAppShapes(cornerStyle) }
    val typography = remember(settingsViewModel.isSystemFontEnabled, settingsViewModel.fontChoice) {
        if (settingsViewModel.isSystemFontEnabled) SystemTypography
        else fontTypographyMap[settingsViewModel.fontChoice] ?: fontTypographyMap["jetbrains_mono"]!!
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = shapes,
        typography = typography
    ) {
        val baseDensity = LocalDensity.current
        val uiScale = settingsViewModel.uiScale
        val scaledDensity = remember(baseDensity, uiScale) {
            Density(
                density = baseDensity.density * uiScale,
                fontScale = baseDensity.fontScale
            )
        }
        val homeTokens = HomeTokens(colorScheme)
        CompositionLocalProvider(
            LocalDensity provides scaledDensity
        ) {
            CompositionLocalProvider(LocalHomeTokens provides homeTokens) {
                contentCompose(settingsViewModel)
            }
        }
    }
}