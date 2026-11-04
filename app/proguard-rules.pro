# Reflection targets are accessed through HiddenApiBypass; keep them unobfuscated.
-keep class android.os.ServiceManager { *; }
-keep class com.android.internal.app.IVoiceInteractionManagerService* { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
