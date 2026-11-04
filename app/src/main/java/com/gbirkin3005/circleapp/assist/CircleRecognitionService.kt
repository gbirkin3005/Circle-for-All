package com.gbirkin3005.circleapp.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Формальный RecognitionService. Нужен, чтобы декларация voice-interaction-service
 * корректно парсилась на всех прошивках и приложение показывалось в списке помощника.
 * Речь мы не распознаём — сразу возвращаем ошибку.
 */
class CircleRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onStopListening(listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {
        // Нечего отменять — сессии не создаём.
    }
}
