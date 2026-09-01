# R8 rules for the release build.
#
# Everything here exists because R8 cannot see the reference: the JNI layer calls into
# Kotlin by name, and the course data is deserialised by field name. Anything R8 *can*
# see is deliberately left shrinkable.

# --- JNI -------------------------------------------------------------------------
# native-lib.cpp registers its entry points by mangled name
# (Java_com_rudimentor_app_audio_NativeMetronome_nativeStart and friends), so both the
# holder classes and their native methods must keep their original names. Renaming the
# class silently breaks the lookup at runtime, not at build time.
-keep class com.rudimentor.app.audio.NativeMetronome { *; }
-keep class com.rudimentor.app.audio.NativeMicLab { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Course data needs no keep rules on purpose: AssetCourseLoader reads the JSON with a
# hand-written android.util.JsonReader and matches field names against string literals,
# so nothing is resolved reflectively and the models stay shrinkable.

# --- Diagnostics -----------------------------------------------------------------
# The developer log and the crash report attach stack traces the developer reads by hand;
# without line numbers they are unusable, and there is no symbol upload pipeline.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
