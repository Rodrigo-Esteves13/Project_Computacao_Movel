package pt.ismai.farol.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import pt.ismai.farol.FarolApp
import pt.ismai.farol.MainActivity
import pt.ismai.farol.R
import pt.ismai.farol.domain.AuthSession
import pt.ismai.farol.domain.FavoriteAlerts
import pt.ismai.farol.domain.model.ErrorType
import pt.ismai.farol.domain.model.FarolException
import pt.ismai.farol.domain.model.FavoriteInfo

private const val WORK_NAME = "favorites-daily-check"
private const val CHANNEL_ID = "favorite-changes"
private const val NOTIFICATION_ID = 1

/**
 * Re-checks the favorites of the signed-in account and notifies the user if any changed.
 * It does nothing when the user turned the option off, signed out, or has no connection.
 */
class FavoritesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FarolApp).container
        val session = container.auth.session.first { it !is AuthSession.Loading }
        if (session !is AuthSession.SignedIn || !container.settings.dailyCheck.first()) return Result.success()

        val startedAt = System.currentTimeMillis()
        val repository = container.analysisRepository
        try {
            repository.recheckFavorites().collect { }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FarolException) {
            // No connection or rate limit: try again later with WorkManager's back-off.
            if (e.type == ErrorType.NO_INTERNET || e.type == ErrorType.RATE_LIMITED) return Result.retry()
        }
        val changed = FavoriteAlerts.changedSince(repository.favorites().first(), startedAt)
        if (changed.isNotEmpty()) notify(applicationContext, changed)
        return Result.success()
    }

    private fun notify(context: Context, changed: List<FavoriteInfo>) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notification_channel_description) },
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(FavoriteAlerts.message(changed))
            .setContentIntent(open)
            .setAutoCancel(true)
            // The text names the sites the user follows: keep it off the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

/** Turns the daily background check on and off. */
class DailyCheckScheduler(context: Context) {
    private val workManager by lazy { WorkManager.getInstance(context.applicationContext) }

    fun enable() {
        val request = PeriodicWorkRequestBuilder<FavoritesWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        // UPDATE keeps one job, even if the user toggles the option many times.
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun disable() {
        workManager.cancelUniqueWork(WORK_NAME)
    }
}
