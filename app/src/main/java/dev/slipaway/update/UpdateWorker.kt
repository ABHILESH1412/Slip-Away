package dev.slipaway.update

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.slipaway.data.Store
import java.util.concurrent.TimeUnit

/**
 * The automatic update check: about every 12 hours, and when the app is opened (at most every 6 hours).
 * If a call is waiting or ringing, it tries again a little later instead of restarting the app.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = Store.get(applicationContext)
        if (!store.settings.value.autoUpdate) return Result.success()
        store.updateSettings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
        return when (Updates.checkAndInstall(applicationContext)) {
            Updates.Outcome.DONE -> Result.success()
            Updates.Outcome.TRY_LATER -> if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "update-check"
        private const val NOW = "update-check-now"
        private val OPEN_INTERVAL = TimeUnit.HOURS.toMillis(6)

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Turns the periodic check on or off to match the setting. Nothing runs while it's off. */
        fun schedule(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(PERIODIC)
                work.cancelUniqueWork(NOW)
                return
            }
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }

        /** On app open: check if the last check is older than 6 hours. */
        fun checkSoon(context: Context) {
            val settings = Store.get(context).settings.value
            if (!settings.autoUpdate) return
            if (System.currentTimeMillis() - settings.lastUpdateCheck < OPEN_INTERVAL) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
                    .build(),
            )
        }
    }
}
