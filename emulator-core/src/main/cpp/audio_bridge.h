#pragma once

#include <cstdint>
#include <cstddef>

struct mCore;

// Initialize the audio resampler. Call after ROM is loaded.
// outputSampleRate should be the device's native rate (typically 48000).
void audioBridgeInit(struct mCore* core, double outputSampleRate);

// Deinitialize and free resampler resources.
void audioBridgeDeinit();

// Process audio from the core through the resampler.
// Call immediately after runFrame() while still holding the core mutex.
void audioBridgeProcess(struct mCore* core);

// Read resampled interleaved stereo 16-bit samples.
// Returns number of sample frames read (each frame = 2 samples: L + R).
size_t audioBridgeRead(int16_t* buffer, size_t maxFrames);

// Get the number of available resampled audio sample frames.
size_t audioBridgeAvailable();
