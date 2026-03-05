#include "audio_bridge.h"

#include <mgba/core/core.h>
#include <mgba-util/audio-buffer.h>
#include <mgba-util/audio-resampler.h>

static struct mAudioBuffer g_destBuffer;
static struct mAudioResampler g_resampler;
static bool g_audioInitialized = false;

void audioBridgeInit(struct mCore* core, double outputSampleRate) {
    if (g_audioInitialized) {
        audioBridgeDeinit();
    }

    // Destination buffer: hold enough for ~4 frames at output rate
    // At 48000 Hz, ~800 samples/frame, so 4096 is ~5 frames of headroom
    mAudioBufferInit(&g_destBuffer, 4096, 2);
    mAudioResamplerInit(&g_resampler, mINTERPOLATOR_SINC);
    mAudioResamplerSetDestination(&g_resampler, &g_destBuffer, outputSampleRate);
    g_audioInitialized = true;
}

void audioBridgeDeinit() {
    if (g_audioInitialized) {
        mAudioResamplerDeinit(&g_resampler);
        mAudioBufferDeinit(&g_destBuffer);
        g_audioInitialized = false;
    }
}

void audioBridgeProcess(struct mCore* core) {
    if (!g_audioInitialized || !core) return;

    struct mAudioBuffer* srcBuffer = core->getAudioBuffer(core);
    if (!srcBuffer) return;
    double sampleRate = (double)core->audioSampleRate(core);

    mAudioResamplerSetSource(&g_resampler, srcBuffer, sampleRate, true);
    mAudioResamplerProcess(&g_resampler);
}

size_t audioBridgeRead(int16_t* buffer, size_t maxFrames) {
    if (!g_audioInitialized) return 0;
    size_t available = mAudioBufferAvailable(&g_destBuffer);
    size_t toRead = available < maxFrames ? available : maxFrames;
    if (toRead == 0) return 0;
    return mAudioBufferRead(&g_destBuffer, buffer, toRead);
}

size_t audioBridgeAvailable() {
    if (!g_audioInitialized) return 0;
    return mAudioBufferAvailable(&g_destBuffer);
}
