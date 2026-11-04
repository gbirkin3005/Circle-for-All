package com.gbirkin3005.circleapp

import android.app.Activity
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.gbirkin3005.circleapp.core.CtsOrchestrator

class MainActivity : Activity() {

    companion object {
        private const val REQ_ASSISTANT_ROLE = 42
        private const val PKG_GOOGLE = "com.google.android.googlequicksearchbox"
        private const val ADB_COMMAND =
            "adb shell pm grant com.gbirkin3005.circleapp android.permission.WRITE_SECURE_SETTINGS"
    }

    private lateinit var statusView: TextView
    private lateinit var testButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(TextView(this).apply {
            text = "Circle to Search для любого Android"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(this).apply {
            text = "\nВызывайте Google Circle to Search жестом помощника " +
                "(длинное нажатие «Домой» в трёхкнопочной навигации или удержание кнопки питания " +
                "в настройках жестов), даже на устройствах, где эта функция не предусмотрена.\n"
            textSize = 15f
        })

        statusView = TextView(this).apply { textSize = 14f }
        root.addView(statusView)

        root.addView(TextView(this).apply {
            text = "Настройка (один раз):"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
        })

        val roleButton = Button(this).apply {
            text = "1. Назначить CircleApp помощником по умолчанию"
            setOnClickListener { requestAssistantRole() }
        }
        root.addView(roleButton)

        root.addView(TextView(this).apply {
            text = "\n2. С ПК, к которому подключён телефон (включена отладка по USB), выполните:"
            textSize = 15f
        })

        root.addView(TextView(this).apply {
            text = ADB_COMMAND
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(0, 8.dp(), 0, 8.dp())
        })

        val copyButton = Button(this).apply {
            text = "Скопировать команду"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("adb", ADB_COMMAND))
                Toast.makeText(this@MainActivity, "Команда скопирована", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(copyButton)

        val refreshButton = Button(this).apply {
            text = "Обновить статус"
            setOnClickListener { refreshStatus() }
        }
        root.addView(refreshButton)

        testButton = Button(this).apply {
            text = "Тест: запустить Circle to Search"
            setOnClickListener { CtsOrchestrator.trigger(this@MainActivity, "manual") }
        }
        root.addView(testButton)

        root.addView(TextView(this).apply {
            text = "\nЕсли срабатывание есть, а Circle to Search не появляется:\n" +
                "• обновите приложение Google;\n" +
                "• в настройках системы разрешите Google автозапуск и работу без ограничений фона " +
                "(важно на Xiaomi, Honor, Huawei);\n" +
                "• Google мог отключить Circle to Search для модели вашего устройства — " +
                "тогда без root-способа (GMS-Flags, спуф модели) не обойтись.\n"
            textSize = 13f
        })

        val scroll = ScrollView(this).apply { addView(root) }

        // Отступы под системные панели и вырез камеры.
        // Боковые системные инсеты (на Xiaomi — компенсация закруглённых углов)
        // учитываем только в альбомной ориентации, где реально есть боковая панель;
        // в портрете достаточно выреза камеры и собственного поля.
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val padL = 16.dp()
        val padT = 12.dp()
        val padR = 16.dp()
        val padB = 12.dp()
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val cut = insets.getInsets(android.view.WindowInsets.Type.displayCutout())
                // Боковые инсеты только в альбоме: в портрете HyperOS добавляет
                // туда компенсацию закруглённых углов, и поля раздуваются.
                val sideL = if (landscape) bars.left + cut.left else 0
                val sideR = if (landscape) bars.right + cut.right else 0
                v.setPadding(
                    padL + sideL,
                    padT + bars.top,
                    padR + sideR,
                    padB + bars.bottom
                )
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val cutout = insets.displayCutout
                val sideL = if (landscape)
                    insets.systemWindowInsetLeft + (cutout?.safeInsetLeft ?: 0) else 0
                val sideR = if (landscape)
                    insets.systemWindowInsetRight + (cutout?.safeInsetRight ?: 0) else 0
                v.setPadding(
                    padL + sideL,
                    padT + insets.systemWindowInsetTop + (cutout?.safeInsetTop ?: 0),
                    padR + sideR,
                    padB + insets.systemWindowInsetBottom + (cutout?.safeInsetBottom ?: 0)
                )
                insets
            }
        }
        scroll.requestApplyInsets()

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ASSISTANT_ROLE) {
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        val assistant = CtsOrchestrator.currentAssistant(this)
        val ours = assistant?.startsWith(packageName) == true
        val assistantLabel = when {
            assistant == null -> "не задан"
            ours -> "CircleApp ✓"
            else -> assistant
        }
        val secureGranted = CtsOrchestrator.hasSecureSettingsPermission(this)
        val googleInstalled = isGoogleInstalled()
        val googleVis = CtsOrchestrator.googleVoiceService(this)

        statusView.text = buildString {
            append("Состояние:\n")
            append("• Помощник по умолчанию: ").append(assistantLabel).append('\n')
            append("• Право через adb: ").append(
                if (secureGranted) "выдано ✓" else "НЕ выдано (шаг 2)"
            ).append('\n')
            append("• Приложение Google: ").append(
                when {
                    !googleInstalled -> "НЕ установлено!"
                    googleVis != null -> "установлено ✓"
                    else -> "установлено, но помощник недоступен!"
                }
            ).append('\n')
            append("• Готовность: ").append(
                when {
                    !googleInstalled || googleVis == null -> "не готово"
                    ours && secureGranted -> "готово ✓"
                    else -> "требует настройки"
                }
            )
        }

        testButton.isEnabled = googleInstalled && googleVis != null
        testButton.alpha = if (testButton.isEnabled) 1f else 0.5f
    }

    private fun isGoogleInstalled(): Boolean = try {
        packageManager.getPackageInfo(PKG_GOOGLE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun requestAssistantRole() {
        // Android 14+: официальный API роли ассистента. Ниже — переход в системные настройки.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                        Toast.makeText(this, "CircleApp уже назначен помощником", Toast.LENGTH_SHORT).show()
                        return
                    }
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                    startActivityForResult(intent, REQ_ASSISTANT_ROLE)
                    return
                }
            }
        } catch (e: Exception) {
            // падаем в переход к настройкам ниже
        }
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
            }
        }
    }
}
