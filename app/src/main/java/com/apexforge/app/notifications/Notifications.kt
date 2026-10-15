package com.apexforge.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apexforge.app.MainActivity
import com.apexforge.app.R
import com.apexforge.app.data.prefs.PreferencesRepository
import com.apexforge.app.data.prefs.UserPrefs
import com.apexforge.app.data.repo.RoutineRepository
import com.apexforge.app.data.repo.StatsRepository
import com.apexforge.app.data.repo.WaterRepository
import com.apexforge.app.data.repo.WorkoutRepository
import com.apexforge.app.domain.Analytics
import com.apexforge.app.domain.UnitSystem
import com.apexforge.app.domain.WaterCalc
import com.apexforge.app.domain.WaterUnit
import com.apexforge.app.domain.fromKg
import com.apexforge.app.domain.fromMl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

object NotificationChannels {
    const val WORKOUT = "workout_reminders"
    const val WATER = "water_reminders"
    const val WEEKLY = "weekly_summary"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WORKOUT, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A daily nudge at the time you choose"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(WATER, "Hydration reminders", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Gentle reminders to drink water during the day"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(WEEKLY, "Weekly summary", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A short recap of your week"
            },
        )
    }
}

object Notifier {
    @SuppressLint("MissingPermission")
    fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        // Covers both the Android 13+ permission and the user's per-app notification switch.
        if (!manager.areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Makes WorkManager match the user's reminder settings. [replace] = true restarts the schedule
     * (used when a setting changed); false keeps existing schedules (used on app start).
     * Android may deliver reminders a few minutes late to save battery.
     */
    fun sync(prefs: UserPrefs, replace: Boolean) {
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP

        if (prefs.workoutReminder) {
            val request = PeriodicWorkRequestBuilder<WorkoutReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntilMinuteOfDay(prefs.workoutReminderMinutes), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORKOUT_WORK, policy, request)
        } else {
            workManager.cancelUniqueWork(WORKOUT_WORK)
        }

        if (prefs.waterReminder) {
            val hours = prefs.waterReminderHours.coerceIn(1, 6).toLong()
            val request = PeriodicWorkRequestBuilder<WaterReminderWorker>(hours, TimeUnit.HOURS)
                .setInitialDelay(hours, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(WATER_WORK, policy, request)
        } else {
            workManager.cancelUniqueWork(WATER_WORK)
        }

        if (prefs.weeklySummary) {
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextSunday(), TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(WEEKLY_WORK, policy, request)
        } else {
            workManager.cancelUniqueWork(WEEKLY_WORK)
        }
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WORKOUT_WORK)
        workManager.cancelUniqueWork(WATER_WORK)
        workManager.cancelUniqueWork(WEEKLY_WORK)
    }

    private fun delayUntilMinuteOfDay(minutes: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(minutes / 60, minutes % 60)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
    }

    private fun delayUntilNextSunday(): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(18, 0)
        if (!target.isAfter(now)) target = target.plusWeeks(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
    }

    private companion object {
        const val WORKOUT_WORK = "workout_reminder"
        const val WATER_WORK = "water_reminder"
        const val WEEKLY_WORK = "weekly_summary"
    }
}

@HiltWorker
class WorkoutReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PreferencesRepository,
    private val routines: RoutineRepository,
    private val workouts: WorkoutRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.prefs.first()
        if (!settings.workoutReminder) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        if (workouts.hasWorkoutBetween(start, end)) return Result.success()

        val all = routines.routines.first()
        val dayBit = 1 shl (today.dayOfWeek.value - 1)
        val scheduledToday = all.firstOrNull { (it.routine.scheduledDays and dayBit) != 0 }
        val usesSchedule = all.any { it.routine.scheduledDays != 0 }
        // With a schedule, only remind on scheduled days. Without one, remind every day.
        if (usesSchedule && scheduledToday == null) return Result.success()

        val text = if (scheduledToday != null) "Today's plan: ${scheduledToday.routine.name}. Ready when you are."
        else "Time to train. Open ApexForge and start a workout."
        Notifier.post(applicationContext, NotificationChannels.WORKOUT, 1001, "Workout time", text)
        return Result.success()
    }
}

@HiltWorker
class WaterReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PreferencesRepository,
    private val water: WaterRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.prefs.first()
        if (!settings.waterReminder) return Result.success()

        val hour = LocalDateTime.now().hour
        if (hour < 8 || hour >= 22) return Result.success() // no night-time reminders

        val total = WaterCalc.total(water.observeDay(LocalDate.now()).first().map { it.amountMl })
        if (total >= settings.waterGoalMl) return Result.success()

        val remaining = WaterCalc.remaining(total, settings.waterGoalMl)
        val amount = settings.waterUnit.fromMl(remaining).roundToInt()
        val text = "About $amount ${settings.waterUnit.short} left to reach today's goal."
        Notifier.post(applicationContext, NotificationChannels.WATER, 1002, "Time for some water", text)
        return Result.success()
    }
}

@HiltWorker
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: PreferencesRepository,
    private val stats: StatsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = prefs.prefs.first()
        if (!settings.weeklySummary) return Result.success()

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val snapshot = stats.snapshot.first()
        val week = Analytics.periodStats(snapshot.sessions, today.minusDays(6), today, zone)
        val text = if (week.workouts == 0) {
            "No workouts logged this week. Even a short session keeps the habit alive."
        } else {
            val volume = settings.units.fromKg(week.volumeKg).roundToInt()
            "${week.workouts} workouts, ${week.sets} sets and $volume ${settings.units.weightUnit} lifted this week."
        }
        Notifier.post(applicationContext, NotificationChannels.WEEKLY, 1003, "Your week in review", text)
        return Result.success()
    }
}
