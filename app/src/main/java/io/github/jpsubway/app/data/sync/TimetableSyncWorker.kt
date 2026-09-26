package io.github.jpsubway.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.jpsubway.app.JpSubwayApp
import io.github.jpsubway.app.core.time.DayType
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** 주 1회(와이파이) 현재 지역 시간표를 ODPT 에서 새로 받아 캐시 */
class TimetableSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as JpSubwayApp).container
        if (!c.settings.hasToken()) return Result.success()
        return try {
            val region = c.networks.region(c.settings.regionId.value)
            if (!region.realtime) return Result.success()
            val net = c.networks.network(region.id)
            DayType.entries.forEach { c.timetables.get(region, net, it, forceRefresh = true) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val NAME = "timetable-sync"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<TimetableSyncWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
