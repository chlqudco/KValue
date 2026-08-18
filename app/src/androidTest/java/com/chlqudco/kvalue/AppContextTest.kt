/*
 * 실제 Android 런타임에서 애플리케이션 ID가 빌드 설정과 일치하는지 확인하는 계측 테스트다.
 * Context가 필요한 검증이므로 JVM 단위 테스트가 아니라 연결된 기기나 에뮬레이터에서 실행한다.
 */
package com.chlqudco.kvalue

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun usesExpectedApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.chlqudco.kvalue", context.packageName)
    }
}
