# =========================================================================
# R8 / ProGuard 规则（Release 压缩混淆保护）
# =========================================================================

# 1. Kotlinx Serialization（保留所有 @Serializable 类、伴生对象与序列化器）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ryancalendar.android.data.remote.**$$serializer { *; }
-keepclassmembers class com.ryancalendar.android.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.ryancalendar.android.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.ryancalendar.android.data.remote.** { *; }

# 2. Retrofit 2 & Kotlin Coroutines suspend 接口签名
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep interface com.ryancalendar.android.data.remote.CalendarApiService { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# 3. Room 数据库实体与 DAO
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.ryancalendar.android.data.local.** { *; }

# 4. WorkManager & BroadcastReceiver（防止定时通知 Worker 被混淆导致反射创建失败）
-keep class com.ryancalendar.android.notification.** { *; }
