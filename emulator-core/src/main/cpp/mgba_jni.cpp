#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <cstring>
#include <cstdlib>
#include <fcntl.h>

#include <mgba/core/core.h>
#include <mgba/core/serialize.h>
#include <mgba/core/config.h>
#include <mgba-util/vfs.h>
#include <mgba-util/image.h>

#include "audio_bridge.h"

#define LOG_TAG "mGBA-JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static struct mCore* g_core = nullptr;
static mColor* g_videoBuffer = nullptr;
static unsigned g_bufferWidth = 0;   // Full buffer size (e.g. 256x224 for SGB)
static unsigned g_bufferHeight = 0;
static unsigned g_width = 0;         // Actual display size (e.g. 160x144 for GB)
static unsigned g_height = 0;
static unsigned g_bufferStride = 0;  // Stride for pixel offset calculation

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeLoadRom(
        JNIEnv* env, jobject /* this */, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);

    // Clean up any existing core
    if (g_core) {
        audioBridgeDeinit();
        g_core->deinit(g_core);
        g_core = nullptr;
    }
    if (g_videoBuffer) {
        free(g_videoBuffer);
        g_videoBuffer = nullptr;
    }

    const char* romPath = env->GetStringUTFChars(path, nullptr);
    if (!romPath) {
        LOGE("Failed to get ROM path string");
        return JNI_FALSE;
    }

    LOGI("Loading ROM: %s", romPath);

    // Auto-detect platform from ROM file
    g_core = mCoreFind(romPath);
    if (!g_core) {
        LOGE("Failed to find core for ROM: %s", romPath);
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }

    if (!g_core->init(g_core)) {
        LOGE("Failed to init core");
        g_core = nullptr;
        env->ReleaseStringUTFChars(path, romPath);
        return JNI_FALSE;
    }

    // Initialize config before any config access (required before reset)
    mCoreInitConfig(g_core, nullptr);

    // Load ROM via VFile
    struct VFile* vf = VFileOpen(romPath, O_RDONLY);
    env->ReleaseStringUTFChars(path, romPath);

    if (!vf) {
        LOGE("Failed to open ROM file");
        g_core->deinit(g_core);
        g_core = nullptr;
        return JNI_FALSE;
    }

    if (!g_core->loadROM(g_core, vf)) {
        LOGE("Failed to load ROM");
        vf->close(vf);
        g_core->deinit(g_core);
        g_core = nullptr;
        return JNI_FALSE;
    }

    // Disable SGB borders for GB/GBC (we want native 160x144)
    mCoreConfigSetValue(&g_core->config, "sgb.borders", "0");

    // Allocate video buffer at base size (SGB: 256x224, GBA: 240x160)
    g_core->baseVideoSize(g_core, &g_bufferWidth, &g_bufferHeight);
    g_bufferStride = g_bufferWidth;
    g_videoBuffer = (mColor*)malloc(g_bufferWidth * g_bufferHeight * sizeof(mColor));
    if (!g_videoBuffer) {
        LOGE("Failed to allocate video buffer");
        g_core->deinit(g_core);
        g_core = nullptr;
        return JNI_FALSE;
    }
    g_core->setVideoBuffer(g_core, g_videoBuffer, g_bufferStride);

    // Set up audio buffer — smaller = lower latency
    g_core->setAudioBufferSize(g_core, 1024);

    // Reset to start
    g_core->reset(g_core);

    // Get actual display dimensions (160x144 for GB/GBC, 240x160 for GBA)
    g_core->currentVideoSize(g_core, &g_width, &g_height);

    LOGI("ROM loaded successfully: display %ux%u (buffer %ux%u)", g_width, g_height, g_bufferWidth, g_bufferHeight);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeInitAudio(
        JNIEnv* /* env */, jobject /* this */, jint outputSampleRate) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) return;

    audioBridgeInit(g_core, (double)outputSampleRate);
    LOGI("Audio resampler initialized: core %d Hz -> output %d Hz",
         g_core->audioSampleRate(g_core), outputSampleRate);
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeRunFrame(
        JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_core) {
        g_core->runFrame(g_core);
    }
}

// Combined run frame + resample audio in one atomic JNI call.
// Eliminates mutex contention between separate runFrame/readAudio calls.
JNIEXPORT jint JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeRunFrameWithAudio(
        JNIEnv* env, jobject /* this */, jshortArray buffer, jint maxFrames) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) return 0;

    g_core->runFrame(g_core);
    audioBridgeProcess(g_core);

    if (!buffer) return 0;
    jshort* samples = env->GetShortArrayElements(buffer, nullptr);
    size_t read = audioBridgeRead(samples, (size_t)maxFrames);
    env->ReleaseShortArrayElements(buffer, samples, 0);
    return (jint)read;
}

JNIEXPORT jintArray JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeGetVideoBuffer(
        JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core || !g_videoBuffer) {
        return nullptr;
    }

    int totalPixels = g_width * g_height;
    jintArray result = env->NewIntArray(totalPixels);
    if (!result) {
        return nullptr;
    }

    // Convert from mGBA's XBGR8888 to Android's ARGB8888
    // Handle stride: buffer may be wider than display (e.g. SGB 256-wide buffer, 160 display)
    jint* pixels = env->GetIntArrayElements(result, nullptr);
    for (unsigned y = 0; y < g_height; y++) {
        for (unsigned x = 0; x < g_width; x++) {
            uint32_t xbgr = g_videoBuffer[y * g_bufferStride + x];
            uint8_t r = (xbgr >>  0) & 0xFF;
            uint8_t g = (xbgr >>  8) & 0xFF;
            uint8_t b = (xbgr >> 16) & 0xFF;
            pixels[y * g_width + x] = (int32_t)(0xFF000000u | (r << 16) | (g << 8) | b);
        }
    }
    env->ReleaseIntArrayElements(result, pixels, 0);

    return result;
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeGetVideoBufferInto(
        JNIEnv* env, jobject /* this */, jintArray outBuffer) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core || !g_videoBuffer || !outBuffer) {
        return;
    }

    int totalPixels = g_width * g_height;
    int arrLen = env->GetArrayLength(outBuffer);
    if (arrLen < totalPixels) {
        return;
    }

    jint* pixels = env->GetIntArrayElements(outBuffer, nullptr);
    for (unsigned y = 0; y < g_height; y++) {
        for (unsigned x = 0; x < g_width; x++) {
            uint32_t xbgr = g_videoBuffer[y * g_bufferStride + x];
            uint8_t r = (xbgr >>  0) & 0xFF;
            uint8_t g = (xbgr >>  8) & 0xFF;
            uint8_t b = (xbgr >> 16) & 0xFF;
            pixels[y * g_width + x] = (int32_t)(0xFF000000u | (r << 16) | (g << 8) | b);
        }
    }
    env->ReleaseIntArrayElements(outBuffer, pixels, 0);
}

JNIEXPORT jint JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeGetWidth(
        JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (jint)g_width;
}

JNIEXPORT jint JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeGetHeight(
        JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (jint)g_height;
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeSetKeys(
        JNIEnv* /* env */, jobject /* this */, jint keys) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_core) {
        g_core->setKeys(g_core, (uint32_t)keys);
    }
}

JNIEXPORT jint JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeGetAudioSampleRate(
        JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) {
        return 32768;
    }
    return (jint)g_core->audioSampleRate(g_core);
}

JNIEXPORT jboolean JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeSaveState(
        JNIEnv* env, jobject /* this */, jstring path, jint flags) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) {
        return JNI_FALSE;
    }

    const char* savePath = env->GetStringUTFChars(path, nullptr);
    LOGI("Saving state to: %s (flags=%d)", savePath, flags);

    struct VFile* vf = VFileOpen(savePath, O_RDWR | O_CREAT | O_TRUNC);
    if (!vf) {
        LOGE("Failed to open save state file for writing: %s", savePath);
        env->ReleaseStringUTFChars(path, savePath);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(path, savePath);

    bool result = mCoreSaveStateNamed(g_core, vf, flags);
    vf->close(vf);
    if (!result) {
        LOGE("mCoreSaveStateNamed returned false");
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeLoadState(
        JNIEnv* env, jobject /* this */, jstring path, jint flags) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) {
        return JNI_FALSE;
    }

    const char* savePath = env->GetStringUTFChars(path, nullptr);
    LOGI("Loading state from: %s (flags=%d)", savePath, flags);

    struct VFile* vf = VFileOpen(savePath, O_RDONLY);
    if (!vf) {
        LOGE("Failed to open save state file for reading: %s", savePath);
        env->ReleaseStringUTFChars(path, savePath);
        return JNI_FALSE;
    }
    env->ReleaseStringUTFChars(path, savePath);

    bool result = mCoreLoadStateNamed(g_core, vf, flags);
    vf->close(vf);
    if (!result) {
        LOGE("mCoreLoadStateNamed returned false");
    } else {
        LOGI("State loaded successfully");
    }
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jintArray JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeCaptureScreenshot(
        JNIEnv* env, jobject /* this */) {
    // Same as getVideoBuffer — captures current frame
    return Java_com_example_squintboyadvance_core_NativeBridge_nativeGetVideoBuffer(env, nullptr);
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeSetSaveDir(
        JNIEnv* env, jobject /* this */, jstring path) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_core) {
        return;
    }

    const char* saveDir = env->GetStringUTFChars(path, nullptr);
    mCoreConfigSetValue(&g_core->config, "savegamePath", saveDir);
    mCoreConfigSetValue(&g_core->config, "savestatePath", saveDir);
    env->ReleaseStringUTFChars(path, saveDir);
}

JNIEXPORT void JNICALL
Java_com_example_squintboyadvance_core_NativeBridge_nativeDestroy(
        JNIEnv* /* env */, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    audioBridgeDeinit();
    if (g_core) {
        g_core->deinit(g_core);
        g_core = nullptr;
    }
    if (g_videoBuffer) {
        free(g_videoBuffer);
        g_videoBuffer = nullptr;
    }
    g_width = 0;
    g_height = 0;
    g_bufferWidth = 0;
    g_bufferHeight = 0;
    g_bufferStride = 0;
    LOGI("Core destroyed");
}

} // extern "C"
