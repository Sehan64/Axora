package frb.axeron.manager.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import frb.axeron.manager.R
import frb.axeron.manager.ui.component.ClickableItem
import frb.axeron.manager.ui.component.RadioItem
import frb.axeron.manager.ui.component.SettingsCategory
import frb.axeron.manager.ui.component.SwitchItem
import frb.axeron.manager.ui.theme.CORNER_STYLE_DEFAULT
import frb.axeron.manager.ui.theme.CORNER_STYLE_EXTRA_ROUNDED
import frb.axeron.manager.ui.theme.CORNER_STYLE_ROUNDED
import frb.axeron.manager.ui.theme.CORNER_STYLE_SQUARED
import frb.axeron.manager.ui.theme.hexToColor

/**
 * Appearance settings, grouped into Theme / Color / Shape / Layout / Text / Home / Language.
 *
 * Two problems this fixes. First, the rows were previously ordered by when the feature happened
 * to be added, so related controls were scattered - the font picker sat between the theme
 * switches and the colour pickers, and AMOLED (a theme setting) was buried under three colour
 * rows. Second, several rows shared an icon: auto-theme, dark mode and AMOLED all used
 * `DarkMode`, and dynamic-colour, secondary and tertiary all used `FormatColorFill`, so the only
 * way to tell those rows apart was reading the label. Every row now has a distinct icon and
 * lives in a labelled section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(
    searchText: String,
    autoThemeEnabled: Boolean,
    onAutoThemeChange: (Boolean) -> Unit,
    darkModeEnabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    amoledEnabled: Boolean,
    onAmoledChange: (Boolean) -> Unit,
    cornerStyle: Int,
    onCornerStyleChange: (Int) -> Unit,
    accentIntensity: Float,
    onAccentIntensityChange: (Float) -> Unit,
    bottomBarScale: Float,
    onBottomBarScaleChange: (Float) -> Unit,
    uiScale: Float,
    onUiScaleChange: (Float) -> Unit,
    secondaryColorHex: String?,
    onSecondaryPaletteClick: () -> Unit,
    tertiaryColorHex: String?,
    onTertiaryPaletteClick: () -> Unit,
    bannerImagePath: String?,
    onBannerClick: () -> Unit,
    onBannerRemove: () -> Unit,
    systemFontEnabled: Boolean,
    onSystemFontChange: (Boolean) -> Unit,
    fontChoice: String,
    onFontChoiceChange: (String) -> Unit,
    currentLanguageDisplay: String?,
    onLanguageClick: () -> Unit,
    onPaletteClick: () -> Unit,
    onPresetSelected: (String) -> Unit,
    currentColorHex: String
) {
    val categoryTitle = stringResource(R.string.settings_category_appearance)
    val matchCategory = shouldShow(searchText, categoryTitle)

    val themeSection = stringResource(R.string.appearance_section_theme)
    val colorSection = stringResource(R.string.appearance_section_color)
    val shapeSection = stringResource(R.string.appearance_section_shape)
    val layoutSection = stringResource(R.string.appearance_section_layout)
    val textSection = stringResource(R.string.appearance_section_text)
    val homeSection = stringResource(R.string.appearance_section_home)
    val languageSection = stringResource(R.string.appearance_section_language)

    val languageTitle = stringResource(R.string.settings_language)
    val showLanguage = matchCategory || shouldShow(searchText, languageTitle, currentLanguageDisplay ?: "", languageSection)

    val autoThemeTitle = stringResource(R.string.auto_theme)
    val autoThemeSummary = stringResource(R.string.auto_theme_desc)
    val showAutoTheme = matchCategory || shouldShow(searchText, autoThemeTitle, autoThemeSummary, themeSection)

    val darkThemeTitle = stringResource(R.string.dark_theme)
    val showDarkMode = !autoThemeEnabled && (matchCategory || shouldShow(searchText, darkThemeTitle, themeSection))

    val amoledTitle = stringResource(R.string.amoled_mode)
    val amoledSummary = stringResource(R.string.amoled_mode_desc)
    val showAmoled = !dynamicColorEnabled && darkModeEnabled && (matchCategory || shouldShow(searchText, amoledTitle, amoledSummary, themeSection))

    val dynamicColorTitle = stringResource(R.string.dynamic_color)
    val dynamicColorSummary = stringResource(R.string.dynamic_color_desc)
    val showDynamicColor = matchCategory || shouldShow(searchText, dynamicColorTitle, dynamicColorSummary, colorSection)

    val colorPaletteTitle = stringResource(R.string.color_palette)
    val colorPaletteSummary = stringResource(R.string.customize_color_palette)
    val showColorPalette = !dynamicColorEnabled && (matchCategory || shouldShow(searchText, colorPaletteTitle, colorPaletteSummary, colorSection))

    val secondaryTitle = stringResource(R.string.secondary_color)
    val showSecondary = !dynamicColorEnabled && (matchCategory || shouldShow(searchText, secondaryTitle, colorSection))

    val tertiaryTitle = stringResource(R.string.tertiary_color)
    val showTertiary = !dynamicColorEnabled && (matchCategory || shouldShow(searchText, tertiaryTitle, colorSection))

    val intensityTitle = stringResource(R.string.accent_intensity)
    val intensitySummary = stringResource(R.string.accent_intensity_desc)
    val showIntensity = !dynamicColorEnabled && (matchCategory || shouldShow(searchText, intensityTitle, intensitySummary, colorSection))

    val cornerTitle = stringResource(R.string.corner_style)
    val showCorner = matchCategory || shouldShow(searchText, cornerTitle, shapeSection)

    val bottomBarTitle = stringResource(R.string.bottom_bar_size)
    val bottomBarSummary = stringResource(R.string.bottom_bar_size_desc)
    val showBottomBar = matchCategory || shouldShow(searchText, bottomBarTitle, bottomBarSummary, layoutSection)

    val uiScaleTitle = stringResource(R.string.ui_scale)
    val uiScaleSummary = stringResource(R.string.ui_scale_desc)
    val showUiScale = matchCategory || shouldShow(searchText, uiScaleTitle, uiScaleSummary, layoutSection)

    val systemFontTitle = stringResource(R.string.system_font)
    val systemFontSummary = stringResource(R.string.system_font_desc)
    val showSystemFont = matchCategory || shouldShow(searchText, systemFontTitle, systemFontSummary, textSection)

    val bannerTitle = stringResource(R.string.home_banner)
    val bannerSummary = stringResource(R.string.home_banner_desc)
    val showBanner = matchCategory || shouldShow(searchText, bannerTitle, bannerSummary, homeSection)

    val showCategory = showLanguage || showAutoTheme || showDarkMode || showDynamicColor || showColorPalette ||
        showAmoled || showCorner || showIntensity || showBottomBar || showUiScale || showSecondary || showTertiary || showBanner || showSystemFont

    if (showCategory) {
        SettingsCategory(
            icon = Icons.Filled.Palette,
            title = categoryTitle
        ) {
            // ---------------------------------------------------------------- Theme
            if (showAutoTheme || showDarkMode || showAmoled) {
                AppearanceSectionHeader(themeSection)
            }

            if (showAutoTheme) {
                SwitchItem(
                    icon = Icons.Filled.BrightnessAuto,
                    title = autoThemeTitle,
                    summary = autoThemeSummary,
                    checked = autoThemeEnabled,
                    onCheckedChange = onAutoThemeChange
                )
            }

            if (showDarkMode) {
                SwitchItem(
                    icon = Icons.Filled.DarkMode,
                    title = darkThemeTitle,
                    summary = null,
                    checked = darkModeEnabled,
                    onCheckedChange = onDarkModeChange
                )
            }

            if (showAmoled) {
                SwitchItem(
                    icon = Icons.Filled.Contrast,
                    title = amoledTitle,
                    summary = amoledSummary,
                    checked = amoledEnabled,
                    onCheckedChange = onAmoledChange
                )
            }

            // ---------------------------------------------------------------- Color
            if (showDynamicColor || showColorPalette || showSecondary || showTertiary || showIntensity) {
                AppearanceSectionHeader(colorSection)
            }

            if (showDynamicColor) {
                SwitchItem(
                    icon = Icons.Filled.Palette,
                    title = dynamicColorTitle,
                    summary = dynamicColorSummary,
                    checked = dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChange
                )
            }

            if (showColorPalette) {
                ListItem(
                    headlineContent = { Text(colorPaletteTitle) },
                    supportingContent = {
                        Text(
                            text = colorPaletteSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.FormatColorFill,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        ColorSwatch(hexToColor(currentColorHex))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = true, onClick = onPaletteClick)
                        .padding(horizontal = 8.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.color_presets),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorPresetItem("#00E5FF", R.string.preset_electric_cyan, currentColorHex, onPresetSelected)
                    ColorPresetItem("#FFB487", R.string.preset_original_orange, currentColorHex, onPresetSelected)
                    ColorPresetItem("#39FF14", R.string.preset_neon_green, currentColorHex, onPresetSelected)
                    ColorPresetItem("#FF1493", R.string.preset_hot_pink, currentColorHex, onPresetSelected)
                    ColorPresetItem("#BB86FC", R.string.preset_royal_purple, currentColorHex, onPresetSelected)
                    ColorPresetItem("#03DAC6", R.string.preset_sky_blue, currentColorHex, onPresetSelected)
                    ColorPresetItem("#CF6679", R.string.preset_crimson_red, currentColorHex, onPresetSelected)
                    ColorPresetItem("#FF6D00", R.string.preset_sunset_orange, currentColorHex, onPresetSelected)
                }
            }

            if (showSecondary) {
                ColorStripeRow(
                    icon = Icons.Filled.Colorize,
                    title = secondaryTitle,
                    colorHex = secondaryColorHex,
                    onPaletteClick = onSecondaryPaletteClick
                )
            }

            if (showTertiary) {
                ColorStripeRow(
                    icon = Icons.Filled.Gradient,
                    title = tertiaryTitle,
                    colorHex = tertiaryColorHex,
                    onPaletteClick = onTertiaryPaletteClick
                )
            }

            if (showIntensity) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(intensityTitle) },
                        supportingContent = {
                            Text(
                                text = intensitySummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.Brightness6,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Slider(
                        value = accentIntensity,
                        onValueChange = onAccentIntensityChange,
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Text(
                        text = String.format("%.2fx", accentIntensity),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(horizontal = 24.dp)
                    )
                }
            }

            // ---------------------------------------------------------------- Shape
            if (showCorner) {
                AppearanceSectionHeader(shapeSection)

                val cornerOptions = listOf(
                    CORNER_STYLE_DEFAULT to R.string.corner_style_default,
                    CORNER_STYLE_SQUARED to R.string.corner_style_squared,
                    CORNER_STYLE_ROUNDED to R.string.corner_style_rounded,
                    CORNER_STYLE_EXTRA_ROUNDED to R.string.corner_style_extra_rounded,
                )
                val cornerLabel = cornerOptions.firstOrNull { it.first == cornerStyle }
                    ?.let { stringResource(it.second) }
                    ?: stringResource(R.string.corner_style_default)
                var showCornerPicker by remember { mutableStateOf(false) }

                ClickableItem(
                    icon = Icons.Filled.CropSquare,
                    title = cornerTitle,
                    summary = cornerLabel,
                    onClick = { showCornerPicker = true }
                )

                if (showCornerPicker) {
                    ModalBottomSheet(
                        onDismissRequest = { showCornerPicker = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.extraLarge.copy(
                            bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                            bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 32.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.select_corner_style),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                            )
                            cornerOptions.forEach { (style, labelRes) ->
                                RadioItem(
                                    title = stringResource(labelRes),
                                    selected = style == cornerStyle,
                                    onClick = {
                                        onCornerStyleChange(style)
                                        showCornerPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- Layout
            if (showBottomBar || showUiScale) {
                AppearanceSectionHeader(layoutSection)

                if (showUiScale) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(uiScaleTitle) },
                            supportingContent = {
                                Text(
                                    text = uiScaleSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.FitScreen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        var localUiScale by remember(uiScale) { mutableFloatStateOf(uiScale) }

                        Slider(
                            value = localUiScale,
                            onValueChange = { localUiScale = it },
                            onValueChangeFinished = { onUiScaleChange(localUiScale) },
                            valueRange = 0.75f..1.5f,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Text(
                            text = String.format("%.2fx", localUiScale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 24.dp)
                        )
                    }
                }

                if (showBottomBar) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(bottomBarTitle) },
                            supportingContent = {
                                Text(
                                    text = bottomBarSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Slider(
                            value = bottomBarScale,
                            onValueChange = onBottomBarScaleChange,
                            valueRange = 0.5f..1.5f,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Text(
                            text = String.format("%.2fx", bottomBarScale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- Text
            if (showSystemFont) {
                AppearanceSectionHeader(textSection)

                SwitchItem(
                    icon = Icons.Filled.FontDownload,
                    title = systemFontTitle,
                    summary = systemFontSummary,
                    checked = systemFontEnabled,
                    onCheckedChange = onSystemFontChange
                )

                if (!systemFontEnabled) {
                    val fontOptions = listOf(
                        "jetbrains_mono" to R.string.font_jetbrains_mono,
                        "caskaydia_mono" to R.string.font_caskaydia_mono,
                        "overpass" to R.string.font_overpass,
                        "firacode" to R.string.font_firacode,
                        "hurmit" to R.string.font_hurmit,
                        "heavydata" to R.string.font_heavydata,
                        "droid_sans_mono" to R.string.font_droid_sans_mono,
                    )

                    val selectedLabel = fontOptions.firstOrNull { it.first == fontChoice }?.let { stringResource(it.second) }
                        ?: stringResource(fontOptions[0].second)
                    var showFontPicker by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(selectedLabel) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable { showFontPicker = true }
                            .padding(horizontal = 8.dp)
                    )

                    if (showFontPicker) {
                        ModalBottomSheet(
                            onDismissRequest = { showFontPicker = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = MaterialTheme.shapes.extraLarge.copy(
                                bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                                bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.select_font),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                                )
                                fontOptions.forEach { (key, labelRes) ->
                                    ListItem(
                                        headlineContent = { Text(stringResource(labelRes)) },
                                        leadingContent = {
                                            RadioButton(
                                                selected = key == fontChoice,
                                                onClick = {
                                                    onFontChoiceChange(key)
                                                    showFontPicker = false
                                                }
                                            )
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        modifier = Modifier
                                            .clickable {
                                                onFontChoiceChange(key)
                                                showFontPicker = false
                                            }
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- Home
            if (showBanner) {
                AppearanceSectionHeader(homeSection)

                ListItem(
                    headlineContent = { Text(bannerTitle) },
                    supportingContent = {
                        Text(
                            text = if (bannerImagePath == null) {
                                bannerSummary
                            } else {
                                stringResource(R.string.home_banner_set)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        if (bannerImagePath != null) {
                            IconButton(onClick = onBannerRemove) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = true, onClick = onBannerClick)
                        .padding(horizontal = 8.dp)
                )
            }

            // ---------------------------------------------------------------- Language
            if (showLanguage) {
                AppearanceSectionHeader(languageSection)

                ListItem(
                    headlineContent = { Text(languageTitle) },
                    supportingContent = {
                        Text(
                            text = currentLanguageDisplay ?: stringResource(R.string.system_default),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .clickable(enabled = true, onClick = onLanguageClick)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/** Labelled divider that groups related appearance rows. */
@Composable
private fun AppearanceSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun ColorStripeRow(
    icon: ImageVector,
    title: String,
    colorHex: String?,
    onPaletteClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = if (colorHex != null) colorHex else stringResource(R.string.secondary_tertiary_not_set),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            ColorSwatch(hexToColor(colorHex ?: "#00E5FF"))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(enabled = true, onClick = onPaletteClick)
            .padding(horizontal = 8.dp)
    )
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Composable
fun ColorPresetItem(
    hexColor: String,
    labelRes: Int,
    currentColorHex: String,
    onPresetSelected: (String) -> Unit
) {
    val color = Color(android.graphics.Color.parseColor(hexColor))
    val isSelected = currentColorHex.equals(hexColor, ignoreCase = true)

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onPresetSelected(hexColor) }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}
