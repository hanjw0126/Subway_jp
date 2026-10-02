package io.github.jpsubway.app.data.repo

import android.content.Context
import io.github.jpsubway.app.BuildConfig
import io.github.jpsubway.app.data.remote.OdptClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _regionId = MutableStateFlow(prefs.getString(KEY_REGION, DEFAULT_REGION) ?: DEFAULT_REGION)
    val regionId: StateFlow<String> = _regionId.asStateFlow()

    private val _userToken = MutableStateFlow(prefs.getString(KEY_TOKEN, "") ?: "")
    val userToken: StateFlow<String> = _userToken.asStateFlow()

    fun setRegion(id: String) {
        prefs.edit().putString(KEY_REGION, id).apply()
        _regionId.value = id
    }

    fun setUserToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token.trim()).apply()
        _userToken.value = token.trim()
    }

    /**
     * 사용자가 입력한 토큰 > (debug 빌드 전용) local.properties 토큰 > 중계 서버.
     * 배포 APK 에는 ODPT 키가 들어가지 않는다.
     */
    fun consumerKey(): String = _userToken.value
        .ifBlank { BuildConfig.ODPT_CONSUMER_KEY }
        .ifBlank { if (BuildConfig.ODPT_PROXY_URL.isNotBlank()) OdptClient.PROXY_KEY else "" }

    fun usingProxy(): Boolean = consumerKey() == OdptClient.PROXY_KEY

    fun hasToken(): Boolean = consumerKey().isNotBlank()

    private companion object {
        const val KEY_REGION = "region"
        const val KEY_TOKEN = "odpt_token"
        const val DEFAULT_REGION = "tokyo"
    }
}
