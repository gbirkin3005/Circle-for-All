# CircleApp — Circle to Search для любого Android

Запускает **настоящий Google Circle to Search** на устройствах, где эта функция не предусмотрена
производителем или отключена Google для модели устройства. Работает через официальный API
цифрового помощника (`VoiceInteractionService`): приложение назначается помощником по умолчанию,
а при вызове передаёт запрос приложению Google.

![Иконка](app/src/main/res/drawable/ic_launcher_foreground.xml)

## Как это работает

1. CircleApp объявляется `VoiceInteractionService` и появляется в системном списке
   «Цифровой помощник по умолчанию».
2. При вызове помощника (удержание кнопки питания, долгое нажатие «Домой» и т.п.)
   система показывает нашу невидимую сессию.
3. Сессия временно передаёт роль помощника Google и вызывает системный механизм
   `IVoiceInteractionManagerService.showSessionFromSession(...)` с параметрами
   `omni.entry_point = 1` и `callingFeatureId = "circle_to_search"` — тот же путь,
   который используют лаунчеры Samsung/Pixel. Google показывает свой оверлей
   Circle to Search, а система сама снимает скриншот текущего экрана.
4. Через пару секунд роль помощника автоматически возвращается CircleApp.

Пока CircleApp — активный помощник, backdoor из п. 3 попадал бы к нам самим, поэтому
переключение роли обязательно. Для него требуется право `WRITE_SECURE_SETTINGS`
(выдаётся один раз через adb).

## Требования

- **Android 10+** (minSdk 29).
- **Приложение Google** (`com.google.android.googlequicksearchbox`) установлено
  и обновлено; в настройках системы разрешены автозапуск и работа без ограничений
  фона (критично на Xiaomi/HyperOS, Honor, Huawei).
- **Одноразовая команда с ПК** (включена отладка по USB):

  ```bash
  adb shell pm grant com.gbirkin3005.circleapp android.permission.WRITE_SECURE_SETTINGS
  ```

## Установка и настройка

1. Скачайте APK из [Releases](https://github.com/gbirkin3005/Circle-for-All/releases)
   и установите на устройство.
2. Откройте CircleApp → «Назначить CircleApp помощником по умолчанию»
   (откроется системный выбор помощника).
3. Выполните adb-команду из раздела «Требования» (в приложении есть кнопка
   копирования команды).
4. В строке «Готовность» в приложении должно появиться «готово ✓».
   Проверить можно кнопкой «Тест: запустить Circle to Search».

## Как вызывать

| Способ | Работает? |
|---|---|
| Удержание кнопки питания (HyperOS: настройки жестов) | ✅ |
| Долгое нажатие «Домой» (трёхкнопочная навигация) | ✅ |
| Жестовая навигация: свайп от нижнего угла к центру | ❌ на HyperOS |

Свайп от угла на HyperOS/MIUI жёстко привязан к Google: лаунчер перехватывает жест
и уведомляет Google напрямую через приватную библиотеку `gsa.launcherclient`,
минуя системный выбор помощника. Сторонние приложения туда попасть не могут (без root).

## Ограничения

- Если Google отключил Circle to Search (omni) для вашей модели устройства —
  оверлей не появится. Это лечится только root-способами
  ([GMS-Flags](https://github.com/polodarb/GMS-Flags), спуф модели устройства),
  обычным приложением не обойтись.
- Скриншот текущего экрана снимает система для сессии Google; в приложениях
  с `FLAG_SECURE` скриншот будет пустым — это ограничение Android.

## Сборка

```bash
# Требуется JDK 17+ (например, встроенный в Android Studio)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash / Windows

./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # release APK (нужна подпись)
```

Ветка `main` всегда собирается Gradle Wrapper'ом (Gradle 9.8.2, AGP 9.5.0,
встроенный Kotlin). `local.properties` с путём к SDK не коммитится — создайте
сами или откройте проект в Android Studio.

## Структура проекта

```
app/src/main/java/com/gbirkin3005/circleapp/
├── MainActivity.kt            # Экран настройки, статус, тестовый запуск
├── assist/
│   ├── CircleVoiceService.kt          # VoiceInteractionService (появляемся в списке помощника)
│   ├── CircleSessionService.kt        # Сессия помощника → триггер оркестратора
│   ├── CircleRecognitionService.kt    # Формальный RecognitionService
│   └── AssistEntryActivity.kt         # Точка входа на прошивках с assist-интентами
└── core/
    └── CtsOrchestrator.kt     # Передача роли Google → backdoor → возврат роли
```

## Благодарности и источники

- [muhammadhaseebiqbal-dev/CircleToSearch](https://github.com/muhammadhaseebiqbal-dev/CircleToSearch) —
  реализация вызова backdoor без root.
- [parallelcc/MiCTS](https://github.com/parallelcc/MiCTS) — исследование системных
  механизмов Circle to Search (VIS/CSMS, gating omni).
- [AKS-Labs/CircleToSearch](https://github.com/AKS-Labs/CircleToSearch) —
  эталонная реализация помощника через VoiceInteractionService.
- [lsposed/hidden-api-bypass](https://github.com/LSPosed/AndroidHiddenApiBypass) —
  обход hidden API restrictions.

## Лицензия

[GPL-3.0](LICENSE)
