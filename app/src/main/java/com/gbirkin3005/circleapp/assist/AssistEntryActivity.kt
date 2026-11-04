package com.gbirkin3005.circleapp.assist

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.gbirkin3005.circleapp.core.CtsOrchestrator

/**
 * Прозрачная точка входа для assist-интентов на прошивках, где система маршрутизирует
 * вызов помощника через активити (ACTION_ASSIST / VOICE_ASSIST /
 * LAUNCH_CONTEXTUAL_SEARCH), а не напрямую в VoiceInteractionSession.
 * Просто передаёт событие оркестратору и закрывается.
 */
class AssistEntryActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        Log.d("AssistEntryActivity", "assist intent: ${intent?.action}")
        CtsOrchestrator.trigger(this, "activity:${intent?.action}")
        finish()
        overridePendingTransition(0, 0)
    }
}
