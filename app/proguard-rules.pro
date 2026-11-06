# Engineered by uncoalesced
# R8 rules for the release build (T10). Hilt, Room, WorkManager, SQLCipher and ML Kit ship
# their own consumer rules in their AARs; the rules below cover what they do not.

# Tink and its protobuf-lite messages are reached by reflection (KeyTemplates by name,
# generated message classes through GeneratedMessageLite). Keep both.
-keep class com.google.crypto.tink.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# Hilt-injected workers are looked up by class name (hilt-work keeps the names; this keeps
# the assisted factories too).
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class **_HiltModules* { *; }
-keep class **_AssistedFactory* { *; }

# Room entities and DAOs: Room's consumer rules keep the generated implementations; the
# entities are plain data classes read through generated code, kept here for the schema.
-keep class com.uncoalesced.impart.data.local.db.entity.** { *; }

# SQLCipher is reached over JNI (its own rules keep the Java side; this silences the rest).
-dontwarn net.zetetic.database.**

# Kotlin metadata is what Hilt and Room code generation already consumed; nothing at runtime
# needs it, and stripping it removes names from the APK.
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**

# Verbose, debug and info logs are compiled out of the release build. Log.w and Log.e stay:
# they are the redacted, tagged lines adb logcat -s shows a tester.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Crash reports from testers are readable with the mapping file CI uploads; keep line
# numbers so the mapping maps to a line.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
