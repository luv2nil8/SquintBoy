package com.anaglych.squintboyadvance.presentation.sync

/**
 * Decides when a polled SRAM hash is worth archiving.
 *
 * A sample is archived only when it repeats (two consecutive equal hashes) and
 * differs from the last archived hash. The repeat requirement filters torn reads
 * of the mmap-backed .sav mid-write and games that write SRAM continuously —
 * those never stabilize and are caught by [onFinalSample] at pause/stop instead,
 * when the emulator thread is halted and the file is quiescent.
 */
class StabilityTracker(initialArchivedHash: String?) {

    private var lastArchivedHash: String? = initialArchivedHash
    private var lastSeenHash: String? = null

    /** Poll-loop decision. Returns true when the sample should be archived. */
    fun onSample(hash: String): Boolean {
        val stable = hash == lastSeenHash && hash != lastArchivedHash
        lastSeenHash = hash
        return stable
    }

    /** Pause/stop decision: emulation is halted, so no repeat is needed. */
    fun onFinalSample(hash: String): Boolean = hash != lastArchivedHash

    fun onArchived(hash: String) {
        lastArchivedHash = hash
    }
}
