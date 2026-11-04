package com.gbirkin3005.circleapp.core

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Оркестратор запуска настоящего Google Circle to Search.
 *
 * Механика: пока наш пакет — активный VoiceInteractionService, системный backdoor
 * `IVoiceInteractionManagerService.showSessionFromSession(..., "circle_to_search")`
 * попал бы к нам самим (зацикливание). Поэтому последовательность такая:
 *
 *  1. Запомнить текущего ассистента (это мы).
 *  2. Временно назначить активным VoiceInteractionService приложения Google
 *     (требуется WRITE_SECURE_SETTINGS, выдаётся одноразово через adb).
 *  3. Дождаться, пока VIMS переключится на Google.
 *  4. Дёрнуть `showSessionFromSession` с `omni.entry_point = 1` и
 *     callingFeatureId = "circle_to_search" — Google показывает свой оверлей CTS,
 *     а система сама снимает скриншот предыдущего экрана для его сессии.
 *  5. Через короткую паузу вернуть наш пакет активным ассистентом.
 */
object CtsOrchestrator {

    private const val TAG = "CtsOrchestrator"

    private const val PKG_GOOGLE = "com.google.android.googlequicksearchbox"
    private const val KEY_VOICE_INTERACTION_SERVICE = "voice_interaction_service"
    private const val KEY_ASSISTANT = "assistant"

    private const val REENTRY_GUARD_MS = 1500L
    private const val WATCHDOG_MS = 6000L
    private const val ACTIVE_POLL_MS = 50L
    private const val ACTIVE_TIMEOUT_MS = 2500L
    // Возврат роли нашего приложения откладываем, чтобы CTS успел отрисоваться:
    // раньше времени — VIMS убьёт сессию Google.
    private const val RESTORE_DELAY_SUCCESS_MS = 2000L
    private const val RESTORE_DELAY_FAIL_MS = 1200L

    @Volatile
    private var inProgress = false

    @Volatile
    private var lastAttemptAt = 0L

    private val main = Handler(Looper.getMainLooper())

    /** Текущий активный VoiceInteractionService в системе (кто сейчас «помощник»). */
    fun currentAssistant(context: Context): String? =
        runCatching { Settings.Secure.getString(context.contentResolver, KEY_VOICE_INTERACTION_SERVICE) }.getOrNull()

    /** Выдано ли приложению WRITE_SECURE_SETTINGS. Проверяем безопасной пробной записью. */
    fun hasSecureSettingsPermission(context: Context): Boolean {
        val probeKey = "circleapp_probe"
        return try {
            Settings.Secure.putString(context.contentResolver, probeKey, "1")
            Settings.Secure.putString(context.contentResolver, probeKey, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Установлено ли приложение Google и содержит ли оно VoiceInteractionService. */
    fun googleVoiceService(context: Context): ComponentName? = findGoogleVoiceInteractionService(context.packageManager)

    fun isBusy(): Boolean = inProgress

    /**
     * Запуск всей последовательности. Безопасно вызывать из любого места
     * (сессия помощника, прозрачное активити, кнопка в MainActivity).
     */
    fun trigger(context: Context, source: String) {
        val app = context.applicationContext
        val now = SystemClock.elapsedRealtime()
        if (inProgress) {
            Log.d(TAG, "trigger($source) ignored: already in progress")
            return
        }
        if (now - lastAttemptAt < REENTRY_GUARD_MS) {
            Log.d(TAG, "trigger($source) ignored: re-entry guard")
            return
        }
        lastAttemptAt = now
        inProgress = true
        watchdog()
        main.post { doTrigger(app, source) }
    }

    private fun watchdog() {
        main.postDelayed({
            if (inProgress) {
                Log.w(TAG, "Watchdog: releasing stuck lock")
                inProgress = false
            }
        }, WATCHDOG_MS)
    }

    private fun doTrigger(app: Context, source: String) {
        val googleVis = findGoogleVoiceInteractionService(app.packageManager)
        if (googleVis == null) {
            toast(app, "Приложение Google не найдено на устройстве")
            inProgress = false
            return
        }

        val resolver = app.contentResolver
        val savedVi = currentAssistant(app)
        val savedAssistant = runCatching {
            Settings.Secure.getString(resolver, KEY_ASSISTANT)
        }.getOrNull()

        var fired = false
        try {
            if (!setActiveVoiceInteractionService(app, googleVis)) {
                toast(app, "Нет права WRITE_SECURE_SETTINGS — выдайте его через adb (инструкция в приложении)")
                inProgress = false
                return
            }

            val becameActive = waitForActiveService(app, googleVis, ACTIVE_TIMEOUT_MS)
            if (!becameActive) {
                Log.w(TAG, "Google VIS did not become active in $ACTIVE_TIMEOUT_MS ms, trying anyway")
            }

            fired = fireShowSession(app)
            Log.i(TAG, "showSessionFromSession -> $fired (source=$source)")
            toast(
                app,
                if (fired) "Circle to Search…"
                else "Google не ответил на запуск: обновите Google, проверьте автозапуск и ограничения фона"
            )
        } catch (t: Throwable) {
            Log.e(TAG, "trigger failed", t)
            toast(app, "Ошибка запуска: ${t.javaClass.simpleName}")
        } finally {
            val restoreDelay = if (fired) RESTORE_DELAY_SUCCESS_MS else RESTORE_DELAY_FAIL_MS
            main.postDelayed({
                runCatching {
                    Settings.Secure.putString(resolver, KEY_VOICE_INTERACTION_SERVICE, savedVi)
                }
                runCatching {
                    Settings.Secure.putString(resolver, KEY_ASSISTANT, savedAssistant)
                }
                Log.i(TAG, "Assistant role restored to $savedVi")
                main.postDelayed({ inProgress = false }, 250)
            }, restoreDelay)
        }
    }

    /**
     * Делает указанный компонент активным VoiceInteractionService.
     * Сначала пробуем системный метод VIMS, при неудаче — прямая запись
     * secure-настройки (требует WRITE_SECURE_SETTINGS).
     */
    private fun setActiveVoiceInteractionService(context: Context, component: ComponentName): Boolean {
        val vimsInfo = getVims(context)
        if (vimsInfo != null) {
            try {
                HiddenApiBypass.invoke(
                    vimsInfo.ifaceClass, vimsInfo.binder,
                    "setActiveServiceEnabled", component, true
                )
                Log.i(TAG, "setActiveServiceEnabled(${component.flattenToShortString()}) ok")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "setActiveServiceEnabled failed, falling back to secure settings", e)
            }
        }
        return try {
            Settings.Secure.putString(
                context.contentResolver,
                KEY_VOICE_INTERACTION_SERVICE,
                component.flattenToString()
            )
            Log.i(TAG, "voice_interaction_service set to ${component.flattenToShortString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "secure settings write failed", e)
            false
        }
    }

    /** Ждёт, пока активный VIS в VIMS не станет [component]. */
    private fun waitForActiveService(context: Context, component: ComponentName, timeoutMs: Long): Boolean {
        val vimsInfo = getVims(context) ?: return false
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val active = runCatching {
                HiddenApiBypass.invoke(
                    vimsInfo.ifaceClass, vimsInfo.binder,
                    "getActiveServiceComponentName"
                ) as? ComponentName
            }.getOrNull()
            if (active != null && active.flattenToString() == component.flattenToString()) {
                return true
            }
            SystemClock.sleep(ACTIVE_POLL_MS)
        }
        return false
    }

    /** Сам backdoor: показывает сессию активного помощника (теперь это Google) как CTS. */
    @SuppressLint("PrivateApi")
    private fun fireShowSession(context: Context): Boolean {
        val vimsInfo = getVims(context) ?: return false

        val bundle = Bundle().apply {
            putLong("invocation_time_ms", SystemClock.elapsedRealtime())
            putInt("omni.entry_point", 1)
        }

        val result = if (Build.VERSION.SDK_INT >= 34) {
            HiddenApiBypass.invoke(
                vimsInfo.ifaceClass, vimsInfo.binder,
                "showSessionFromSession", null, bundle, 7, "circle_to_search"
            )
        } else {
            HiddenApiBypass.invoke(
                vimsInfo.ifaceClass, vimsInfo.binder,
                "showSessionFromSession", null, bundle, 7
            )
        }
        return result as? Boolean ?: false
    }

    private class VimsInfo(val ifaceClass: Class<*>, val binder: Any)

    @SuppressLint("PrivateApi")
    private fun getVims(context: Context): VimsInfo? {
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val binder = HiddenApiBypass.invoke(smClass, null, "getService", "voiceinteraction") as? IBinder
                ?: return null
            val ifaceClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")
            val stubClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val vims = HiddenApiBypass.invoke(stubClass, null, "asInterface", binder) ?: return null
            VimsInfo(ifaceClass, vims)
        } catch (e: Exception) {
            Log.e(TAG, "getVims failed", e)
            null
        }
    }

    /** Ищет компонент VoiceInteractionService внутри приложения Google. */
    @Suppress("DEPRECATION")
    private fun findGoogleVoiceInteractionService(pm: PackageManager): ComponentName? {
        val candidates = linkedSetOf(
            // Современные сборки Google app
            "com.google.android.apps.gsa.voiceinteraction.VoiceInteractionServiceImpl",
            // Старые/альтернативные имена
            "com.google.android.voiceinteraction.VoiceInteractionServiceImpl",
            "com.google.android.voiceinteraction.GsaVoiceInteractionService",
            "com.google.android.apps.gsa.voiceinteraction.hotword.HotwordVoiceInteractionService"
        )
        for (name in candidates) {
            val cn = ComponentName(PKG_GOOGLE, name)
            val info = runCatching { pm.getServiceInfo(cn, 0) }.getOrNull()
            if (info != null) return cn
        }
        // Запасной вариант: перебираем все сервисы пакета по манифесту
        return try {
            val pi = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    PKG_GOOGLE,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong())
                )
            } else {
                pm.getPackageInfo(PKG_GOOGLE, PackageManager.GET_SERVICES)
            }
            val svc = pi.services?.firstOrNull {
                it.name.contains("voiceinteraction", ignoreCase = true)
            } ?: return null
            ComponentName(PKG_GOOGLE, svc.name)
        } catch (e: Exception) {
            Log.w(TAG, "Google package services enumeration failed", e)
            null
        }
    }

    private fun toast(context: Context, text: String) {
        main.post {
            try {
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            } catch (ignored: Exception) {
            }
        }
    }
}
