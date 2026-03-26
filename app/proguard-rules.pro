# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Orbbec (기존)
-keep class com.orbbec.obsensor.**
-dontwarn com.orbbec.obsensor.**
-keepclassmembers class com.orbbec.obsensor.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# MediaPipe 추가
-keep class com.google.mediapipe.** { *; }
-keepclassmembers class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# MediaPipe Tasks Vision
-keep class com.google.mediapipe.tasks.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# AndroidPacketCreator (크래시 발생 지점)
-keep class com.google.mediapipe.framework.AndroidPacketCreator { *; }
-keepclassmembers class com.google.mediapipe.framework.AndroidPacketCreator {
    native <methods>;
}

# FaceLandmarker
-keep class com.google.mediapipe.tasks.vision.facelandmarker.** { *; }

# MediaPipe 리플렉션 보호
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Protobuf (MediaPipe가 사용)
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**