package com.gbirkin3005.circleapp.assist

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * Декларация приложения как цифрового помощника. Именно этот сервис
 * помещает CircleApp в список «Цифровой помощник по умолчанию».
 * Сама логика вызова — в [CircleSessionService] и CtsOrchestrator.
 */
class CircleVoiceService : VoiceInteractionService() {

    companion object {
        private const val TAG = "CircleVoiceService"

        /** Живой экземпляр, пока система держит нас активным помощником. */
        @Volatile
        var instance: CircleVoiceService? = null
            private set
    }

    override fun onReady() {
        super.onReady()
        instance = this
        Log.i(TAG, "Assistant service ready")
    }

    override fun onShutdown() {
        if (instance === this) instance = null
        super.onShutdown()
        Log.i(TAG, "Assistant service shutdown")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
