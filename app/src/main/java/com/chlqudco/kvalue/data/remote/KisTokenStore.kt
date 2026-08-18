/*
 * KIS OAuth 접근 토큰과 만료시각을 앱 전용 SharedPreferences에 저장한다.
 * 메모리 토큰이 사라지는 앱 재시작 뒤에도 유효한 토큰을 재사용해 불필요한 발급 요청을 줄인다.
 * read는 값이 비어 있으면 null을 반환하고, write와 clear는 두 필드를 한 편집 작업으로 갱신한다.
 * 이 저장소는 토큰 재사용을 위한 것이며 APK에 포함된 App Secret 자체를 보호하는 수단은 아니다.
 */
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
