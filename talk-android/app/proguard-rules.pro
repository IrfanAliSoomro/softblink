#
# Nextcloud Talk - Android Client
#
# SPDX-FileCopyrightText: 2017-2024 Nextcloud GmbH and Nextcloud contributors
# SPDX-License-Identifier: GPL-3.0-or-later
#

# Keep line numbers for better crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations
-keepattributes *Annotation*

# Keep generic signature for reflection
-keepattributes Signature

# Keep exception information
-keepattributes Exceptions

# ===== Dagger =====
-dontwarn com.google.errorprone.annotations.**
-keep class javax.inject.** { *; }
-keep class dagger.** { *; }
-keep class autodagger.** { *; }

# ===== Retrofit & OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ===== Gson / JSON Serialization =====
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep all model classes (data classes used for API/Database)
-keep class com.nextcloud.talk.models.** { *; }
-keep class com.nextcloud.talk.data.** { *; }

# ===== Room Database =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ===== Kotlinx Serialization =====
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nextcloud.talk.**$$serializer { *; }
-keepclassmembers class com.nextcloud.talk.** {
    *** Companion;
}
-keepclasseswithmembers class com.nextcloud.talk.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== WebRTC =====
-keep class org.webrtc.** { *; }
-keep class org.conscrypt.** { *; }
-dontwarn org.webrtc.**

# ===== Firebase (for gplay flavor) =====
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ===== WorkManager =====
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.** { *; }

# ===== Coil Image Loading =====
-keep class coil.** { *; }
-dontwarn coil.**

# ===== EventBus =====
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-keepclassmembers class * extends org.greenrobot.eventbus.util.ThrowableFailureEvent {
    <init>(java.lang.Throwable);
}

# ===== RxJava =====
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
   long producerIndex;
   long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

# ===== Kotlin =====
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
}

# ===== Parcelize =====
-keep interface android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# ===== Keep Application class =====
-keep class com.nextcloud.talk.application.NextcloudTalkApplication { *; }

# ===== Emoji =====
-keep class com.vanniktech.emoji.** { *; }

# ===== Chat message view holders (instantiated via reflection in ChatKit) =====
-keep class com.nextcloud.talk.adapters.messages.** extends com.stfalcon.chatkit.messages.MessageHolders$BaseMessageViewHolder { *; }
-keepclassmembers class com.nextcloud.talk.adapters.messages.** extends com.stfalcon.chatkit.messages.MessageHolders$BaseMessageViewHolder {
    public <init>(android.view.View, java.lang.Object);
    public <init>(android.view.View);
}
-keep class com.nextcloud.talk.adapters.messages.MessagePayload { *; }

# ===== Security Hardware =====
-keep class de.cotech.hw.** { *; }
-dontwarn de.cotech.hw.**

# ===== Keep Activities, Services, Receivers =====
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ===== Keep View constructors =====
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== Keep enums =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Remove logging in release =====
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# ===== General warnings to ignore =====
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== Optional dependencies (not used at runtime) =====
-dontwarn com.google.common.base.Objects$ToStringHelper
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.slf4j.impl.StaticLoggerBinder
