#include <jni.h>

#include <vector>

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
Java_com_rudimentor_app_audio_NativeMetronome_nativeSetSequence(
        JNIEnv *env, jobject, jintArray sequence) {
    if (sequence == nullptr) {
        return;
    }
    const jsize length = env->GetArrayLength(sequence);
    if (length <= 0) {
        return;
    }
    std::vector<jint> steps(static_cast<size_t>(length));
    env->GetIntArrayRegion(sequence, 0, length, steps.data());

    std::vector<int> values(steps.begin(), steps.end());
    engine.setSequence(values.data(), static_cast<int>(values.size()));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_rudimentor_app_audio_NativeMetronome_nativeGetTickCount(JNIEnv *, jobject) {
    return engine.tickCount();
}
