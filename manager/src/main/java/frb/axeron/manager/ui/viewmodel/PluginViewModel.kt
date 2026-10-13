package frb.axeron.manager.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import frb.axeron.api.Axeron
import frb.axeron.manager.AxeronApplication.Companion.axeronApp
import frb.axeron.manager.ui.util.HanziToPinyin
import frb.axeron.server.PluginInfo
import frb.axeron.server.PluginInstaller
import frb.axeron.shared.AxeronApiConstant
import frb.axeron.shared.PathHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class PluginViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("plugin_settings", Context.MODE_PRIVATE)

    companion object {
        const val TAG = "PluginsViewModel"
    }

    var search by mutableStateOf("")

    var getSelectedAsc by mutableIntStateOf(
        prefs.getInt("selected_asc", 0)
    )
        private set
    var getSelectedSort by mutableIntStateOf(
        prefs.getInt("selected_sort", 0)
    )
        private set

    fun setSelectedAsc(value: Int) {
        getSelectedAsc = value
        prefs.edit { putInt("selected_asc", value) }
    }

    fun setSelectedSort(value: Int) {
        getSelectedSort = value
        prefs.edit { putInt("selected_sort", value) }
    }

    val pluginList by derivedStateOf {
        val comparator: Comparator<PluginInfo> = if (getSelectedAsc == 0) {
            when (getSelectedSort) {
                1 -> compareBy { it.size }
                2 -> compareBy { it.enabled }
                3 -> compareBy { it.hasActionScript }
                4 -> compareBy { it.hasWebUi }
                else -> compareBy<PluginInfo> { it.prop.name }
            }
        } else {
            when (getSelectedSort) {
                1 -> compareByDescending { it.size }
                2 -> compareByDescending { it.enabled }
                3 -> compareByDescending { it.hasActionScript }
                4 -> compareByDescending { it.hasWebUi }
                else -> compareByDescending<PluginInfo> { it.prop.name }
            }
        }.thenBy { it.prop.id }

        plugins.filter { plugin ->
            val name = plugin.prop.name
            val pinyin = HanziToPinyin.getInstance().toPinyinString(name)
            plugin.prop.id.contains(search, ignoreCase = true) ||
                    name.contains(search, ignoreCase = true) ||
                    pinyin.contains(search, ignoreCase = true)
        }.sortedWith(comparator)
    }

    var plugins by mutableStateOf<List<PluginInfo>>(ArrayList())
        private set

    var isNeedReignite by mutableStateOf(false)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isNeedRefresh by mutableStateOf(false)
        private set

    var pluginEnabledCount by mutableIntStateOf(0)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    private val _updateMap = MutableStateFlow<Map<String, Triple<String, String, String>>>(emptyMap())
    val updateMap: StateFlow<Map<String, Triple<String, String, String>>> = _updateMap.asStateFlow()

    val updateCount: StateFlow<Int> = _updateMap
        .map { map -> map.count { it.value.first.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _bannerCache = mutableStateMapOf<String, ByteArray>()
    val bannerCache: Map<String, ByteArray> get() = _bannerCache

    private suspend fun loadBanners(list: List<PluginInfo>) {
        val isRoot = runCatching { Axeron.getAxeronInfo().isRoot() }.getOrDefault(false)
        val parent = PathHelper.getWorkingPath(isRoot, AxeronApiConstant.folder.PARENT_PLUGIN)
        list.forEach { plugin ->
            val banner = plugin.prop.banner
            if (banner.isEmpty() || banner.startsWith("http", ignoreCase = true)) return@forEach
            if (_bannerCache.containsKey(plugin.prop.id)) return@forEach
            runCatching {
                val bannerFile = File(File(parent, plugin.prop.id), banner)
                Axeron.newFileService()
                    .setFileInputStream(bannerFile.absolutePath)
                    ?.use { it.readBytes() }
            }.getOrNull()?.let { _bannerCache[plugin.prop.id] = it }
        }
    }

    var pluginInstalers by mutableStateOf<List<PluginInstaller>>(emptyList())

    fun updateZipUris(installers: List<PluginInstaller>) {
        pluginInstalers = installers
    }

    fun clearZipUris() {
        pluginInstalers = emptyList()
    }

    fun preloadUpdates(list: List<PluginInfo>) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = list.map { plugin ->
                async {
                    val update = checkUpdate(plugin)
                    plugin.prop.id to update
                }
            }.awaitAll().toMap()
            _updateMap.value = result
        }
    }

    fun fetchModuleList() {
        viewModelScope.launch {
            isRefreshing = true

            withContext(Dispatchers.IO) {
                val start = SystemClock.elapsedRealtime()
                val oldPluginList = plugins

                runCatching {
                    isNeedReignite = false

                    val loadedPlugins = Axeron.getPlugins()
                    loadBanners(loadedPlugins)
                    plugins = loadedPlugins

                    isNeedReignite = plugins.any { it.update }

                    isNeedRefresh = false
                }.onFailure { e ->
                    Log.e(TAG, "fetchModuleList: ", e)
                    isRefreshing = false
                }

                withContext(Dispatchers.IO) {
                    pluginEnabledCount = plugins.count {
                        it.enabled
                    }
                }

                val loadCost = SystemClock.elapsedRealtime() - start
                Log.i(TAG, "load cost: $loadCost, modules: $plugins")

                preloadUpdates(plugins)

                // when both old and new is kotlin.collections.EmptyList
                // moduleList update will don't trigger
                isRefreshing = false
            }

        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(pluginInfo: PluginInfo): Triple<String, String, String> {
        val empty = Triple("", "", "")
        if (pluginInfo.prop.updateJson.isEmpty() || pluginInfo.remove || pluginInfo.update || !pluginInfo.enabled) {
            return empty
        }
        // download updateJson
        val result = kotlin.runCatching {
            val url = pluginInfo.prop.updateJson
            Log.i(TAG, "checkUpdate url: $url")
            val response = axeronApp.okhttpClient.newCall(
                Request.Builder().url(url).build()
            ).execute()
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                ""
            }
        }.getOrDefault("")
        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return empty
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return empty

        var version = updateJson.optString("version", "")
        version = sanitizeVersionString(version)
        val versionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (versionCode <= pluginInfo.prop.versionCode || zipUrl.isEmpty()) {
            return empty
        }

        return Triple(zipUrl, version, changelog)
    }

}