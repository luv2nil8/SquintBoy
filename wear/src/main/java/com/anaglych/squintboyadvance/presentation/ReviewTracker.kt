package com.anaglych.squintboyadvance.presentation

import android.content.Context
import androidx.core.content.edit

enum class ReviewState { PENDING, LATER, NEVER, DONE }

object ReviewTracker {

    private const val PREFS = "review_tracker"
    private const val KEY_STATE = "state"
    private const val KEY_EXIT_COUNT = "exit_count"
    private const val KEY_TOTAL_PLAY_MS = "total_play_ms"
    private const val KEY_SESSION_START = "session_start"
    private const val LATER_INTERVAL = 5
    private const val PLAY_TIME_THRESHOLD_MS = 10 * 60 * 1000L

    fun getState(ctx: Context): ReviewState =
        ReviewState.valueOf(
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATE, ReviewState.PENDING.name)!!
        )

    fun setState(ctx: Context, state: ReviewState) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_STATE, state.name)
        }
    }

    fun sessionStarted(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_SESSION_START, System.currentTimeMillis())
        }
    }

    /**
     * Accumulates session play time and increments exit count.
     * Returns true if the review prompt should be shown.
     */
    fun shouldPromptOnExit(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = ReviewState.valueOf(prefs.getString(KEY_STATE, ReviewState.PENDING.name)!!)
        if (state == ReviewState.NEVER || state == ReviewState.DONE) return false

        val sessionStart = prefs.getLong(KEY_SESSION_START, 0L)
        val sessionMs = if (sessionStart > 0L) System.currentTimeMillis() - sessionStart else 0L
        val totalMs = prefs.getLong(KEY_TOTAL_PLAY_MS, 0L) + sessionMs
        val exitCount = prefs.getInt(KEY_EXIT_COUNT, 0) + 1

        prefs.edit {
            putLong(KEY_TOTAL_PLAY_MS, totalMs)
            putInt(KEY_EXIT_COUNT, exitCount)
            putLong(KEY_SESSION_START, 0L)
        }

        return when (state) {
            ReviewState.PENDING -> totalMs >= PLAY_TIME_THRESHOLD_MS
            ReviewState.LATER -> exitCount % LATER_INTERVAL == 0
            else -> false
        }
    }
}
