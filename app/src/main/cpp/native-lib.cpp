#include <jni.h>

#include "MetronomeEngine.h"

namespace {
MetronomeEngine engine;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeStart(JNIEnv *, jobject) {
    return engine.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeStop(JNIEnv *, jobject) {
    engine.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeSetBpm(JNIEnv *, jobject, jint bpm) {
    engine.setBpm(bpm);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeSetPattern(
        JNIEnv *, jobject, jint beatCount, jint accentMask) {
    engine.setPattern(beatCount, accentMask);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeGetTickCount(JNIEnv *, jobject) {
    return engine.tickCount();
}
