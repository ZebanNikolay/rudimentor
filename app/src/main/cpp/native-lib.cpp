#include <jni.h>

#include <algorithm>
#include <vector>

#include "MetronomeEngine.h"
#include "MicLabEngine.h"

namespace {
MetronomeEngine engine;
MicLabEngine micLab;
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

// ----- MicLab bindings -------------------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeStart(JNIEnv *, jobject) {
    return micLab.start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeStop(JNIEnv *, jobject) {
    micLab.stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeSetBpm(JNIEnv *, jobject, jint bpm) {
    micLab.setBpm(bpm);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeSetClickAudible(
        JNIEnv *, jobject, jboolean audible) {
    micLab.setClickAudible(audible == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeSetSensitivity(
        JNIEnv *, jobject, jfloat sensitivity) {
    micLab.setSensitivity(sensitivity);
}

extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeSetInputLatencyMillis(
        JNIEnv *, jobject, jfloat millis) {
    micLab.setInputLatencyMillis(millis);
}

// Snapshot layout for Kotlin (kept trivial to avoid boxing / allocations):
//   [0] sampleRate
//   [1] inputFrame (low 32 bits)
//   [2] inputFrame (high 32 bits)
//   [3] tickCount  (low 32 bits)
//   [4] tickCount  (high 32 bits)
//   [5] envelope   * 1e6
//   [6] threshold  * 1e6
//   [7] peak       * 1e6
//   [8] clickAudible (0/1)
//   [9] running       (0/1)
//  [10] outputFrame (low 32 bits)
//  [11] outputFrame (high 32 bits)
extern "C" JNIEXPORT void JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeSnapshot(
        JNIEnv *env, jobject, jintArray out) {
    if (out == nullptr || env->GetArrayLength(out) < 12) {
        return;
    }
    const MicLabEngine::Snapshot s = micLab.snapshot();
    jint values[12];
    values[0] = s.sampleRate;
    values[1] = static_cast<jint>(s.inputFrame & 0xFFFFFFFFLL);
    values[2] = static_cast<jint>((s.inputFrame >> 32) & 0xFFFFFFFFLL);
    values[3] = static_cast<jint>(s.tickCount & 0xFFFFFFFFLL);
    values[4] = static_cast<jint>((s.tickCount >> 32) & 0xFFFFFFFFLL);
    values[5] = static_cast<jint>(s.envelope * 1.0e6f);
    values[6] = static_cast<jint>(s.threshold * 1.0e6f);
    values[7] = static_cast<jint>(s.peak * 1.0e6f);
    values[8] = s.clickAudible ? 1 : 0;
    values[9] = s.running ? 1 : 0;
    values[10] = static_cast<jint>(s.outputFrame & 0xFFFFFFFFLL);
    values[11] = static_cast<jint>((s.outputFrame >> 32) & 0xFFFFFFFFLL);
    env->SetIntArrayRegion(out, 0, 12, values);
}

// Hit / tick event layouts, packed as long triples / doubles to keep the
// number of JNI round-trips tiny. Each hit needs (frame, envelope*1e6,
// threshold*1e6); each tick needs (frame, index).
extern "C" JNIEXPORT jint JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeDrainHits(
        JNIEnv *env, jobject, jlongArray out) {
    if (out == nullptr) {
        return 0;
    }
    const jsize capacity = env->GetArrayLength(out);
    const int slots = capacity / 3;
    if (slots <= 0) {
        return 0;
    }
    MicLabEngine::HitEvent buffer[MicLabEngine::kEventCapacity];
    const int copied = micLab.drainHits(buffer,
                                        std::min(slots, MicLabEngine::kEventCapacity));
    std::vector<jlong> packed(static_cast<size_t>(copied) * 3);
    for (int i = 0; i < copied; ++i) {
        packed[i * 3 + 0] = buffer[i].frame;
        packed[i * 3 + 1] = static_cast<jlong>(buffer[i].envelope * 1.0e6f);
        packed[i * 3 + 2] = static_cast<jlong>(buffer[i].threshold * 1.0e6f);
    }
    if (copied > 0) {
        env->SetLongArrayRegion(out, 0, copied * 3, packed.data());
    }
    return copied;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_rudimentor_app_audio_NativeMicLab_nativeDrainTicks(
        JNIEnv *env, jobject, jlongArray out) {
    if (out == nullptr) {
        return 0;
    }
    const jsize capacity = env->GetArrayLength(out);
    const int slots = capacity / 2;
    if (slots <= 0) {
        return 0;
    }
    MicLabEngine::TickEvent buffer[MicLabEngine::kEventCapacity];
    const int copied = micLab.drainTicks(buffer,
                                         std::min(slots, MicLabEngine::kEventCapacity));
    std::vector<jlong> packed(static_cast<size_t>(copied) * 2);
    for (int i = 0; i < copied; ++i) {
        packed[i * 2 + 0] = buffer[i].frame;
        packed[i * 2 + 1] = buffer[i].index;
    }
    if (copied > 0) {
        env->SetLongArrayRegion(out, 0, copied * 2, packed.data());
    }
    return copied;
}
