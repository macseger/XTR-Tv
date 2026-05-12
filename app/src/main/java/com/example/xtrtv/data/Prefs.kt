package com.example.xtrtv.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("xtr_prefs", Context.MODE_PRIVATE)

    fun saveUser(userData: UserData) {
        prefs.edit().apply {
            putString("url", userData.url)
            putString("username", userData.username)
            putString("password", userData.password)
            apply()
        }
    }

    var isTunnelingEnabled: Boolean
        get() = prefs.getBoolean("tunneling_enabled", false)
        set(value) = prefs.edit().putBoolean("tunneling_enabled", value).apply()

    var isFrameRateMatchingEnabled: Boolean
        get() = prefs.getBoolean("frame_rate_matching", false)
        set(value) = prefs.edit().putBoolean("frame_rate_matching", value).apply()

    var lastChannelId: Int
        get() = prefs.getInt("last_channel_id", -1)
        set(value) = prefs.edit().putInt("last_channel_id", value).apply()

    fun getUser(): UserData? {
        val url = prefs.getString("url", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        return UserData(url, username, password)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
