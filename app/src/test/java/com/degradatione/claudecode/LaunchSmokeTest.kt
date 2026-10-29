package com.degradatione.claudecode

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Смоук-тест: реально исполняет композицию MainActivity на JVM.
 * Ловит в CI то, что иначе всплывает только на устройстве (шрифты, ресурсы,
 * init-блоки). Обязательный idle() — без него runnables AndroidUiDispatcher
 * не выполняются и падающий экран проходит тест.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchSmokeTest {

    @Test
    fun activityComposesWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(controller.get())
    }
}
