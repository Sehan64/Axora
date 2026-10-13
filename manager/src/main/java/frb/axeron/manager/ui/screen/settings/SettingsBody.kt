package frb.axeron.manager.ui.screen.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import frb.axeron.manager.R
import frb.axeron.manager.ui.component.PaletteDialog
import frb.axeron.manager.ui.component.SettingsCategoryCard
import frb.axeron.manager.ui.theme.adjust
import frb.axeron.manager.ui.theme.basePrimaryDefault
import frb.axeron.manager.ui.theme.hexToColor
import frb.axeron.manager.ui.util.LocalBottomBarHidden
import frb.axeron.manager.ui.util.LocaleHelper
import frb.axeron.manager.ui.viewmodel.SettingsViewModel
import frb.axeron.manager.ui.viewmodel.ViewModelGlobal
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ColorTarget {
    PRIMARY,
    SECONDARY,
    TERTIARY
}

private fun copyUriToFile(context: android.content.Context, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return File(context.filesDir, "home_banner_${System.currentTimeMillis()}.jpg").absolutePath

    val isGif = bytes.size > 4 &&
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
        bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()

    if (isGif) {
        val file = File(context.filesDir, "home_banner_${System.currentTimeMillis()}.gif")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    val file = File(context.filesDir, "home_banner_${System.currentTimeMillis()}.jpg")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        file.writeBytes(bytes)
        return file.absolutePath
    }

    var sampleSize = 1
    val maxDimension = 1600
    while (bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (bitmap == null) {
        file.writeBytes(bytes)
        return file.absolutePath
    }

    file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
    bitmap.recycle()
    return file.absolutePath
}

/** Title resource for a category, shared by the root cards and the detail top bar. */
fun categoryTitleRes(key: SettingsCategoryKey): Int = when (key) {
    SettingsCategoryKey.Connection -> R.string.settings_category_connection
    SettingsCategoryKey.Appearance -> R.string.settings_category_appearance
    SettingsCategoryKey.Activation -> R.string.settings_category_activation
    SettingsCategoryKey.Path -> R.string.settings_category_path
    SettingsCategoryKey.Other -> R.string.settings_category_other
}

private val CATEGORY_ENTRIES = listOf(
    SettingsCategoryKey.Connection to Icons.Filled.Adb,
    SettingsCategoryKey.Appearance to Icons.Filled.Palette,
    SettingsCategoryKey.Activation to Icons.Filled.RestartAlt,
    SettingsCategoryKey.Path to Icons.Filled.FolderDelete,
    SettingsCategoryKey.Other to Icons.Filled.MoreHoriz
)

/**
 * Shared body for the settings root and each category detail screen.
 *
 * Rendering has three modes:
 *  - root with an empty search box: the five category cards;
 *  - root with a search query: the matching rows inline (no cards), preserving the old
 *    search-everything behaviour;
 *  - a detail screen ([onlyCategory] != null): only that category's rows.
 */
@Composable
fun SettingsBody(
    modifier: Modifier = Modifier,
    navigator: DestinationsNavigator,
    viewModelGlobal: ViewModelGlobal,
    searchText: String,
    onlyCategory: SettingsCategoryKey?,
    onCategoryClick: (SettingsCategoryKey) -> Unit
) {
    val activateViewModel = viewModelGlobal.activateViewModel
    val settings = viewModelGlobal.settingsViewModel

    var colorTarget by remember { mutableStateOf<ColorTarget?>(null) }

    val axeronRunning = activateViewModel.axeronInfo.isRunning()
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    val bannerPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { copyUriToFile(context, uri) }
                    settings.setBannerImage(path)
                }
            }
        }

    var currentLanguageDisplay by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        currentLanguageDisplay =
            AppCompatDelegate.getApplicationLocales()[0]?.displayLanguage?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            } ?: context.getString(R.string.system_default)
    }

    val scrollState = rememberScrollState()
    val bottomBarHidden = LocalBottomBarHidden.current

    // Only the root screen has a bottom bar to hide; the detail screens hide it entirely.
    if (onlyCategory == null) {
        LaunchedEffect(scrollState) {
            var lastOffset = scrollState.value
            snapshotFlow { scrollState.value }
                .collect { currentOffset ->
                    val isScrollingDown = currentOffset > lastOffset + 4
                    val isScrollingUp = currentOffset < lastOffset - 4

                    when {
                        isScrollingDown && !bottomBarHidden.value -> bottomBarHidden.value = true
                        isScrollingUp && bottomBarHidden.value -> bottomBarHidden.value = false
                    }

                    lastOffset = currentOffset
                }
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            onlyCategory == null && searchText.isEmpty() -> {
                CATEGORY_ENTRIES.forEach { (key, icon) ->
                    SettingsCategoryCard(
                        icon = icon,
                        title = stringResource(categoryTitleRes(key)),
                        onClick = { onCategoryClick(key) }
                    )
                }
            }

            onlyCategory == null -> {
                ConnectionSettings(
                    searchText = searchText,
                    isTcpModeEnabled = settings.isTcpModeEnabled,
                    tcpPortInt = settings.tcpPortInt,
                    onTcpModeChange = { settings.setTcpMode(it) },
                    onTcpPortChange = { settings.setTcpPort(it) }
                )

                AppearanceBlock(
                    searchText = searchText,
                    settings = settings,
                    isDarkMode = isDarkMode,
                    currentLanguageDisplay = currentLanguageDisplay,
                    onPrimaryPalette = { colorTarget = ColorTarget.PRIMARY },
                    onSecondaryPalette = { colorTarget = ColorTarget.SECONDARY },
                    onTertiaryPalette = { colorTarget = ColorTarget.TERTIARY },
                    onBannerClick = { bannerPicker.launch("image/*") },
                    onBannerRemove = {
                        settings.bannerImagePath?.let { path ->
                            scope.launch {
                                withContext(Dispatchers.IO) { File(path).delete() }
                            }
                        }
                        settings.removeBannerImage()
                    },
                    onLanguageClick = { LocaleHelper.launchSystemLanguageSettings(context) }
                )

                ActivationSettings(
                    searchText = searchText,
                    axeronRunning = axeronRunning,
                    isActivateOnBootEnabled = settings.isActivateOnBootEnabled,
                    isAutoRestartEnabled = settings.isAutoRestartEnabled,
                    isIgniteWhenRelogEnabled = settings.isIgniteWhenRelogEnabled,
                    onActivateOnBootChange = { settings.setActivateOnBoot(it) },
                    onAutoRestartChange = { settings.setAutoRestart(it) },
                    onIgniteWhenRelogChange = { settings.setIgniteWhenRelog(it) },
                    isShizukuActive = activateViewModel.isShizukuActive,
                    onShizukuInterceptChange = { activateViewModel.setShizukuIntercept(it) }
                )

                if (axeronRunning) {
                    PathSettings(
                        searchText = searchText,
                        navigator = navigator
                    )
                }

                OtherSettings(
                    searchText = searchText,
                    axeronRunning = axeronRunning,
                    navigator = navigator,
                    settingsViewModel = settings
                )
            }

            else -> {
                when (onlyCategory) {
                    SettingsCategoryKey.Connection -> ConnectionSettings(
                        searchText = "",
                        isTcpModeEnabled = settings.isTcpModeEnabled,
                        tcpPortInt = settings.tcpPortInt,
                        onTcpModeChange = { settings.setTcpMode(it) },
                        onTcpPortChange = { settings.setTcpPort(it) }
                    )

                    SettingsCategoryKey.Appearance -> AppearanceBlock(
                        searchText = "",
                        settings = settings,
                        isDarkMode = isDarkMode,
                        currentLanguageDisplay = currentLanguageDisplay,
                        onPrimaryPalette = { colorTarget = ColorTarget.PRIMARY },
                        onSecondaryPalette = { colorTarget = ColorTarget.SECONDARY },
                        onTertiaryPalette = { colorTarget = ColorTarget.TERTIARY },
                        onBannerClick = { bannerPicker.launch("image/*") },
                        onBannerRemove = {
                            settings.bannerImagePath?.let { path ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { File(path).delete() }
                                }
                            }
                            settings.removeBannerImage()
                        },
                        onLanguageClick = { LocaleHelper.launchSystemLanguageSettings(context) }
                    )

                    SettingsCategoryKey.Activation -> ActivationSettings(
                        searchText = "",
                        axeronRunning = axeronRunning,
                        isActivateOnBootEnabled = settings.isActivateOnBootEnabled,
                        isAutoRestartEnabled = settings.isAutoRestartEnabled,
                        isIgniteWhenRelogEnabled = settings.isIgniteWhenRelogEnabled,
                        onActivateOnBootChange = { settings.setActivateOnBoot(it) },
                        onAutoRestartChange = { settings.setAutoRestart(it) },
                        onIgniteWhenRelogChange = { settings.setIgniteWhenRelog(it) },
                        isShizukuActive = activateViewModel.isShizukuActive,
                        onShizukuInterceptChange = { activateViewModel.setShizukuIntercept(it) }
                    )

                    SettingsCategoryKey.Path -> if (axeronRunning) {
                        PathSettings(
                            searchText = "",
                            navigator = navigator
                        )
                    }

                    SettingsCategoryKey.Other -> OtherSettings(
                        searchText = "",
                        axeronRunning = axeronRunning,
                        navigator = navigator,
                        settingsViewModel = settings
                    )
                }
            }
        }
    }

    val currentColor = hexToColor(settings.customPrimaryColorHex)
    val currentSecondaryColor = settings.customSecondaryColorHex?.let { hexToColor(it) }
        ?: basePrimaryDefault.adjust(hueDelta = 20f, satMul = 0.70f)
    val currentTertiaryColor = settings.customTertiaryColorHex?.let { hexToColor(it) }
        ?: basePrimaryDefault.adjust(hueDelta = -60f, satMul = 0.70f)

    when (colorTarget) {
        ColorTarget.PRIMARY -> PaletteDialog(
            initialColor = currentColor,
            onDismiss = { colorTarget = null },
            onConfirm = { hex ->
                settings.setCustomPrimaryColor(hex)
                colorTarget = null
            },
            onReset = {
                settings.removeCustomPrimaryColor()
                colorTarget = null
            }
        )

        ColorTarget.SECONDARY -> PaletteDialog(
            initialColor = currentSecondaryColor,
            onDismiss = { colorTarget = null },
            onConfirm = { hex ->
                settings.setCustomSecondaryColor(hex)
                colorTarget = null
            },
            onReset = {
                settings.removeCustomSecondaryColor()
                colorTarget = null
            }
        )

        ColorTarget.TERTIARY -> PaletteDialog(
            initialColor = currentTertiaryColor,
            onDismiss = { colorTarget = null },
            onConfirm = { hex ->
                settings.setCustomTertiaryColor(hex)
                colorTarget = null
            },
            onReset = {
                settings.removeCustomTertiaryColor()
                colorTarget = null
            }
        )

        null -> {}
    }
}

@Composable
private fun AppearanceBlock(
    searchText: String,
    settings: SettingsViewModel,
    isDarkMode: Boolean,
    currentLanguageDisplay: String?,
    onPrimaryPalette: () -> Unit,
    onSecondaryPalette: () -> Unit,
    onTertiaryPalette: () -> Unit,
    onBannerClick: () -> Unit,
    onBannerRemove: () -> Unit,
    onLanguageClick: () -> Unit
) {
    AppearanceSettings(
        searchText = searchText,
        autoThemeEnabled = settings.getAppThemeId == 0,
        onAutoThemeChange = { enabled ->
            if (enabled) {
                settings.setAppTheme(0)
            } else {
                settings.setAppTheme(if (isDarkMode) 1 else 2)
            }
        },
        darkModeEnabled = settings.getAppThemeId == 1,
        onDarkModeChange = { enabled ->
            settings.setAppTheme(if (enabled) 1 else 2)
        },
        dynamicColorEnabled = settings.isDynamicColorEnabled,
        onDynamicColorChange = { settings.setDynamicColor(it) },
        amoledEnabled = settings.isAmoledEnabled,
        onAmoledChange = { settings.setAmoled(it) },
        cornerStyle = settings.cornerStyle,
        onCornerStyleChange = { settings.updateCornerStyle(it) },
        accentIntensity = settings.accentIntensity,
        onAccentIntensityChange = { settings.updateAccentIntensity(it) },
        bottomBarScale = settings.bottomBarScale,
        onBottomBarScaleChange = { settings.updateBottomBarScale(it) },
        uiScale = settings.uiScale,
        onUiScaleChange = { settings.updateUiScale(it) },
        secondaryColorHex = settings.customSecondaryColorHex,
        onSecondaryPaletteClick = onSecondaryPalette,
        tertiaryColorHex = settings.customTertiaryColorHex,
        onTertiaryPaletteClick = onTertiaryPalette,
        bannerImagePath = settings.bannerImagePath,
        onBannerClick = onBannerClick,
        onBannerRemove = onBannerRemove,
        systemFontEnabled = settings.isSystemFontEnabled,
        onSystemFontChange = { settings.setSystemFont(it) },
        fontChoice = settings.fontChoice,
        onFontChoiceChange = { settings.updateFontChoice(it) },
        currentLanguageDisplay = currentLanguageDisplay,
        onLanguageClick = onLanguageClick,
        onPaletteClick = onPrimaryPalette,
        onPresetSelected = { hex ->
            settings.setCustomPrimaryColor(hex)
        },
        currentColorHex = settings.customPrimaryColorHex
    )
}
