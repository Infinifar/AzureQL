package com.qinglong.app.scripts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qinglong.core.data.remote.QLApiService
import com.qinglong.core.model.ScriptFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptsViewModel @Inject constructor(
    private val api: QLApiService
) : ViewModel() {

    private val _scripts = MutableStateFlow<List<ScriptFile>>(emptyList())
    val scripts = _scripts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _content = MutableStateFlow<String?>(null)
    val content = _content.asStateFlow()

    init { loadScripts() }

    fun loadScripts() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val r = api.getScripts()
                if (r.code == 200 && r.data != null) _scripts.value = r.data ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun loadContent(file: ScriptFile) {
        val name = file.title ?: return
        // path 是文件所在目录（相对脚本根目录）；根目录文件 parent 为 "" / null
        val path = file.parent ?: ""
        viewModelScope.launch {
            try {
                val r = api.getScriptContent(name, path)
                _content.value = if (r.code == 200) r.data else "加载失败"
            } catch (_: Exception) { _content.value = "加载失败" }
        }
    }

    fun clearContent() { _content.value = null }
}
