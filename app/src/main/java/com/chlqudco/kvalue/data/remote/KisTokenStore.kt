package com.chlqudco.kvalue.data.remote

import android.content.SharedPreferences
import androidx.core.content.edit

internal data class StoredKisToken(
    val value: String,
    val expiresAtEpochMillis: Long
)

internal class KisTokenStore(
    private val preferences: SharedPreferences
) {
    fun read(): StoredKisToken? {
        val value = preferences.getString(TOKEN, null).orEmpty()
        val expiresAt = preferences.getLong(EXPIRES_AT, 0L)
        return value.takeIf(String::isNotBlank)?.let {
            StoredKisToken(it, expiresAt)
        }
    }

    fun write(token: StoredKisToken) {
        preferences.edit {
            putString(TOKEN, token.value)
            putLong(EXPIRES_AT, token.expiresAtEpochMillis)
        }
    }

    fun clear() {
        preferences.edit {
            remove(TOKEN)
            remove(EXPIRES_AT)
        }
    }

    private companion object {
        const val TOKEN = "access_token"
        const val EXPIRES_AT = "expires_at"
    }
}
