package com.gbirkin3005.circleapp.assist

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import android.view.WindowManager
import com.gbirkin3005.circleapp.core.CtsOrchestrator

/**
 * Сессия помощника: вызывается системой при жесте ассистента (длинное нажатие
 * «Домой», свайп с задержкой и т.п.). Сразу передаёт управление оркестратору,
 * который временно переключает ассистента на Google и запускает Circle to Search.
 */
class CircleSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.d("CircleSessionService", "onNewSession")
        return CircleSession(this)
    }

    inner class CircleSession(context: Context) : VoiceInteractionSession(context) {

        private val handler = Handler(Looper.getMainLooper())

        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            Log.d("CircleSession", "onShow flags=$showFlags")

            // Наша сессия — лишь «триггер-прокси»: не перехватываем касания
            // и не показываем ничего поверх текущего экрана.
            window?.window?.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )

            vibrate(context)

            handler.post {
                CtsOrchestrator.trigger(context, "session")
                // Закрываем свою сессию до того, как Google покажет CTS,
                // чтобы системный скриншот снял предыдущий экран, а не наш оверлей.
                runCatching { hide() }
            }
        }

        override fun onHide() {
            super.onHide()
            Log.d("CircleSession", "onHide")
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        } catch (e: Exception) {
            Log.w("CircleSessionService", "vibrate failed", e)
        }
    }
}
