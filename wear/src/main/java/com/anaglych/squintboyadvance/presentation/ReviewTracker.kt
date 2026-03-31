package com.anaglych.squintboyadvance.presentation

import android.content.Context

enum class ReviewState { PENDING, LATER, NEVER, DONE }

object ReviewTracker {
    private const val PREFS = "review_tracker"
    private const val KEY_STATE = "state"
    private const val KEY_TOTAL_MS = "total_ms"
    private const val KEY_EXIT_COUNT = "exit_count"
    private const val KEY_SESSION_START = "session_start"

    private const val MIN_PLAY_MS = 10 * 60 * 1000L   // 10 minutes
    private const val LATER_EVERY_N_EXITS = 5

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getState(ctx: Context): ReviewState = runCatching {
        ReviewState.valueOf(prefs(ctx).getString(KEY_STATE, null) ?: "PENDING")
    }.getOrDefault(ReviewState.PENDING)

    fun setState(ctx: Context, state: ReviewState) {
        prefs(ctx).edit().putString(KEY_STATE, state.name).apply()
    }

    /** Call when an emulator session begins. */
    fun sessionStarted(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_SESSION_START, System.currentTimeMillis()).apply()
    }

    /** Pure check — returns true if we should show the rate prompt. No side effects. */
    fun shouldPrompt(ctx: Context): Boolean {
        val p = prefs(ctx)
        val state = getState(ctx)
        if (state == ReviewState.DONE || state == ReviewState.NEVER) return false

        val sessionStart = p.getLong(KEY_SESSION_START, 0L)
        val sessionMs = if (sessionStart > 0) System.currentTimeMillis() - sessionStart else 0L
        val totalMs = p.getLong(KEY_TOTAL_MS, 0L) + sessionMs

        return when (state) {
            ReviewState.PENDING -> totalMs >= MIN_PLAY_MS
            ReviewState.LATER -> {
                val exits = p.getInt(KEY_EXIT_COUNT, 0)
                totalMs >= MIN_PLAY_MS && exits > 0 && exits % LATER_EVERY_N_EXITS == 0
            }
            else -> false
        }
    }

    /** Call when the user actually exits. Accumulates session time and increments exit count. */
    fun recordExit(ctx: Context) {
        val p = prefs(ctx)
        val sessionStart = p.getLong(KEY_SESSION_START, 0L)
        val sessionMs = if (sessionStart > 0) System.currentTimeMillis() - sessionStart else 0L
        val totalMs = p.getLong(KEY_TOTAL_MS, 0L) + sessionMs
        val exits = p.getInt(KEY_EXIT_COUNT, 0) + 1
        p.edit()
            .putLong(KEY_TOTAL_MS, totalMs)
            .putInt(KEY_EXIT_COUNT, exits)
            .putLong(KEY_SESSION_START, 0L)
            .apply()
    }
}
