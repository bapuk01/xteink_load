package com.xtink.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Background job run by WorkManager on a timer. Errors are stored in prefs and retried on the next run. */
class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Syncer.run(applicationContext)
        return Result.success()
    }
}
