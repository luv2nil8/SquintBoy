package com.anaglych.squintboyadvance.data.retention

import com.anaglych.squintboyadvance.data.db.ArchivedSaveEntity
import com.anaglych.squintboyadvance.data.db.LocalState

data class RetentionPolicy(
    /** Delete saves older than this. null = keep forever. */
    val maxAgeDays: Int?,
    /** Keep at most this many saves per game. null = unlimited. */
    val maxCountPerGame: Int?,
    /** Past this age, keep at most one save per day. null = no thinning. */
    val thinAfterDays: Int?,
)

/**
 * Pure retention policy. Invariants:
 *  - pinned saves are never selected;
 *  - the newest non-pinned save of each game always survives, regardless of age;
 *  - thinning runs before the count cap so the cap keeps a longer time span.
 */
object RetentionEngine {

    private const val DAY_MS = 86_400_000L

    fun selectDeletions(
        saves: List<ArchivedSaveEntity>,
        policy: RetentionPolicy,
        nowMs: Long,
    ): List<ArchivedSaveEntity> {
        val deletions = mutableListOf<ArchivedSaveEntity>()

        for ((_, group) in saves.groupBy { it.romId }) {
            val candidates = group
                .filter { it.localState == LocalState.PRESENT && !it.pinned }
                .sortedByDescending { it.timestampMs }
            if (candidates.isEmpty()) continue
            val newest = candidates.first()
            var survivors = candidates

            policy.thinAfterDays?.let { days ->
                val cutoff = nowMs - days * DAY_MS
                val (old, recent) = survivors.partition { it.timestampMs < cutoff }
                val keptPerDay = old.groupBy { it.dayKey }
                    .map { (_, dayGroup) -> dayGroup.maxBy { it.timestampMs } }
                deletions += old - keptPerDay.toSet()
                survivors = (recent + keptPerDay).sortedByDescending { it.timestampMs }
            }

            policy.maxCountPerGame?.let { cap ->
                if (survivors.size > cap) {
                    val dropped = survivors.drop(cap.coerceAtLeast(1))
                    deletions += dropped
                    survivors = survivors - dropped.toSet()
                }
            }

            policy.maxAgeDays?.let { days ->
                val cutoff = nowMs - days * DAY_MS
                deletions += survivors.filter { it.timestampMs < cutoff && it.id != newest.id }
            }
        }
        return deletions
    }
}
