package com.example.squintboyadvance.core

import android.util.Log
import java.io.File

/**
 * Manages two independent 5-deep FILO stacks:
 *
 * 1. **Save states** (emulator snapshots) — full emulator freeze.
 *    Files: {romId}.ss0 … {romId}.ss4  (0 = newest)
 *
 * 2. **SRAM saves** (in-game battery saves written by the core).
 *    Files: {romId}.sav is the live file mGBA writes to.
 *    Backups: {romId}.sav.0 … {romId}.sav.4  (0 = newest)
 *
 * On every focus-loss event the caller should invoke [onFocusLost] which:
 *   - Pushes a new save state onto the stack (shifting 0→1→2→3→4, dropping 4)
 *   - Backs up the current .sav file onto the SRAM stack the same way
 *
 * On ROM launch the caller should invoke [restoreAll] which:
 *   - Restores the newest valid SRAM backup into the live .sav path
 *   - Loads the newest valid save state
 */
class SaveStateManager(
    private val stateDir: File,
    private val sramDir: File,
    private val romId: String,
    private val emulator: MgbaEmulator
) {
    companion object {
        private const val TAG = "SaveStateManager"
        private const val STACK_DEPTH = 5
    }

    init {
        stateDir.mkdirs()
        sramDir.mkdirs()
    }

    // ── Save state files ────────────────────────────────────────────────
    private fun stateFile(slot: Int): File = File(stateDir, "$romId.ss$slot")

    // ── SRAM files ──────────────────────────────────────────────────────
    /** The live .sav file that mGBA reads/writes directly. */
    private fun liveSramFile(): File = File(sramDir, "$romId.sav")
    /** Backup slot for SRAM. */
    private fun sramBackup(slot: Int): File = File(sramDir, "$romId.sav.$slot")

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Called on every focus-loss event (pause, back, close).
     * Pushes both a save state and an SRAM backup onto their stacks.
     */
    fun onFocusLost() {
        pushSaveState()
        pushSramBackup()
    }

    /**
     * Called on ROM launch after loadRom + setSaveDir.
     * Restores the newest valid SRAM backup, then the newest valid save state.
     */
    fun restoreAll() {
        restoreSram()
        restoreSaveState()
    }

    // ── Save state stack ────────────────────────────────────────────────

    private fun pushSaveState() {
        // Shift stack down: 3→4, 2→3, 1→2, 0→1
        for (i in STACK_DEPTH - 1 downTo 1) {
            val src = stateFile(i - 1)
            val dst = stateFile(i)
            if (src.exists()) {
                src.renameTo(dst)
            } else {
                dst.delete()
            }
        }
        // Write newest to slot 0
        val success = emulator.saveState(stateFile(0).absolutePath)
        if (success) {
            Log.i(TAG, "Save state pushed to slot 0")
        } else {
            Log.e(TAG, "Failed to write save state")
        }
    }

    private fun restoreSaveState() {
        for (i in 0 until STACK_DEPTH) {
            val file = stateFile(i)
            if (file.exists() && file.length() > 0) {
                if (emulator.loadState(file.absolutePath)) {
                    Log.i(TAG, "Restored save state from slot $i")
                    return
                } else {
                    Log.w(TAG, "Save state slot $i is corrupt, trying next")
                }
            }
        }
        Log.i(TAG, "No valid save state found, starting fresh")
    }

    // ── SRAM backup stack ───────────────────────────────────────────────

    private fun pushSramBackup() {
        val live = liveSramFile()
        if (!live.exists() || live.length() == 0L) return

        // Shift stack down: 3→4, 2→3, 1→2, 0→1
        for (i in STACK_DEPTH - 1 downTo 1) {
            val src = sramBackup(i - 1)
            val dst = sramBackup(i)
            if (src.exists()) {
                src.renameTo(dst)
            } else {
                dst.delete()
            }
        }
        // Copy (not move) live .sav to slot 0 — mGBA still owns the live file
        try {
            live.copyTo(sramBackup(0), overwrite = true)
            Log.i(TAG, "SRAM backup pushed to slot 0")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup SRAM: ${e.message}")
        }
    }

    private fun restoreSram() {
        val live = liveSramFile()

        // If a live .sav already exists and has content, it's probably fine
        if (live.exists() && live.length() > 0) {
            Log.i(TAG, "Live SRAM file exists, using it")
            return
        }

        // Otherwise, restore from newest valid backup
        for (i in 0 until STACK_DEPTH) {
            val backup = sramBackup(i)
            if (backup.exists() && backup.length() > 0) {
                try {
                    backup.copyTo(live, overwrite = true)
                    Log.i(TAG, "Restored SRAM from backup slot $i")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "SRAM backup slot $i failed to restore: ${e.message}")
                }
            }
        }
        Log.i(TAG, "No SRAM backups found, starting fresh")
    }
}
