package com.anaglych.squintboyadvance.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anaglych.squintboyadvance.data.retention.RetentionRunner
import java.util.concurrent.TimeUnit

class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val PERIODIC_NAME = "retention"
        private const val ONCE_NAME = "retention_once"

        /** Daily pass for age-based decay. Call whenever the feature is enabled. */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RetentionWorker>(24, TimeUnit.HOURS).build(),
            )
        }

        /** Immediate pass, kicked after each ingest batch. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RetentionWorker>().build(),
            )
        }
    }

    override suspend fun doWork(): Result {
        RetentionRunner.runFullPass(applicationContext)
        return Result.success()
    }
}
