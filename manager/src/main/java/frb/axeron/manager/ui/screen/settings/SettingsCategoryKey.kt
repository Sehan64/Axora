package frb.axeron.manager.ui.screen.settings

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Navigation argument identifying which settings category a detail screen should render.
 *
 * Named "Key" to avoid clashing with the [frb.axeron.manager.ui.component.SettingsCategory]
 * composable, which is the visual group used inside each category.
 */
@Parcelize
sealed class SettingsCategoryKey : Parcelable {
    data object Connection : SettingsCategoryKey()
    data object Appearance : SettingsCategoryKey()
    data object Activation : SettingsCategoryKey()
    data object Path : SettingsCategoryKey()
    data object Other : SettingsCategoryKey()
}
