package com.MateuszW.aigrowthtracker.running

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.*
import com.MateuszW.aigrowthtracker.data.AppDatabase
import com.MateuszW.aigrowthtracker.data.RunRoutePointEntity
import com.MateuszW.aigrowthtracker.data.RunSessionEntity
import kotlinx.coroutines.*

class RunningService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TASK_ID = "EXTRA_TASK_ID"
        const val EXTRA_TASK_NAME = "EXTRA_TASK_NAME"
        const val EXTRA_TARGET_KM = "EXTRA_TARGET_KM"
        const val EXTRA_AI_MAIN_NAME = "EXTRA_AI_MAIN_NAME"
        private const val CHANNEL_ID = "running_gps_channel"
        private const val NOTIFICATION_ID = 1337
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastValidLocation: Location? = null
    private var timerJob: Job? = null
    private var elapsedSeconds = 0L
    private var db: AppDatabase? = null
    private var currentSessionId: Int? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        db = AppDatabase.getDatabase(applicationContext)
        setupLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getIntExtra(EXTRA_TASK_ID, -999)
                val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: "Bieganie"
                val aiMainName = intent.getStringExtra(EXTRA_AI_MAIN_NAME)?.takeIf { it.isNotBlank() }
                val targetKm = if (intent.hasExtra(EXTRA_TARGET_KM))
                    intent.getFloatExtra(EXTRA_TARGET_KM, 0f).takeIf { it > 0f }
                else null
                startRun(taskId, taskName, aiMainName, targetKm)
            }
            ACTION_PAUSE -> pauseRun()
            ACTION_RESUME -> resumeRun()
            ACTION_STOP -> stopRun()
        }
        return START_STICKY
    }

    private fun startRun(taskId: Int, taskName: String, aiMainName: String?, targetKm: Float?) {
        RunningRepository.reset()
        elapsedSeconds = 0L
        lastValidLocation = null

        // Kluczowe: startForeground PRZED jakimkolwiek kodem asynchronicznym
        startForegroundCompat()

        serviceScope.launch {
            val session = RunSessionEntity(
                taskId = taskId,
                taskName = taskName,
                aiMainName = aiMainName,
                targetDistanceKm = targetKm,
                startTime = System.currentTimeMillis()
            )
            val sessionId = (db?.runDao()?.insertSession(session) ?: -1L).toInt()
            if (sessionId > 0) {
                currentSessionId = sessionId
                RunningRepository.setCurrentSessionId(sessionId)
            }
        }

        RunningRepository.setRunning(true)
        RunningRepository.setPaused(false)
        startLocationUpdates()
        startTimer()
    }

    private fun pauseRun() {
        RunningRepository.setPaused(true)
        stopLocationUpdates()
        timerJob?.cancel()
        updateSessionInDb()
        updateNotification()
    }

    private fun resumeRun() {
        RunningRepository.setPaused(false)
        lastValidLocation = null
        startLocationUpdates()
        startTimer()
        updateNotification()
    }

    private fun stopRun() {
        RunningRepository.setRunning(false)
        RunningRepository.setPaused(false)
        stopLocationUpdates()
        timerJob?.cancel()
        updateSessionInDb(isCompleted = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)
                elapsedSeconds++
                RunningRepository.setDuration(elapsedSeconds)
                recalculateStats()
                if (elapsedSeconds % 10 == 0L) {
                    updateSessionInDb()
                    updateNotification()
                }
            }
        }
    }

    private fun recalculateStats() {
        val distance = RunningRepository.totalDistanceMeters.value
        if (distance > 0 && elapsedSeconds > 0) {
            RunningRepository.setCurrentSpeed((distance / elapsedSeconds) * 3.6f)
            val distanceKm = distance / 1000f
            if (distanceKm > 0) RunningRepository.setAvgPace(elapsedSeconds / distanceKm)
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }

    private fun processLocation(location: Location) {
        if (RunningRepository.isPaused.value) return
        if (location.accuracy > 20f) return

        val last = lastValidLocation
        if (last != null) {
            val timeDiffSec = (location.time - last.time) / 1000.0
            if (timeDiffSec > 0) {
                val distance = last.distanceTo(location)
                if ((distance / timeDiffSec) * 3.6 > 25.0) return
                if (distance < 5f) return

                val results = FloatArray(1)
                Location.distanceBetween(
                    last.latitude, last.longitude,
                    location.latitude, location.longitude,
                    results
                )
                RunningRepository.setTotalDistance(
                    RunningRepository.totalDistanceMeters.value + results[0]
                )
            }
        }

        lastValidLocation = location
        RunningRepository.addRoutePoint(RunPoint(location.latitude, location.longitude))

        currentSessionId?.let { id ->
            serviceScope.launch {
                db?.runDao()?.insertRoutePoint(
                    RunRoutePointEntity(
                        sessionId = id,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = location.time,
                        accuracy = location.accuracy
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(true)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {}
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun updateSessionInDb(isCompleted: Boolean = false) {
        val id = currentSessionId ?: return
        serviceScope.launch {
            val existing = db?.runDao()?.getSessionById(id) ?: return@launch
            db?.runDao()?.updateSession(
                existing.copy(
                    totalDistanceMeters = RunningRepository.totalDistanceMeters.value,
                    durationSeconds = RunningRepository.durationSeconds.value,
                    avgSpeedKmh = RunningRepository.currentSpeedKmh.value,
                    avgPaceSecPerKm = RunningRepository.avgPaceSecPerKm.value,
                    endTime = if (isCompleted) System.currentTimeMillis() else existing.endTime,
                    isCompleted = isCompleted
                )
            )
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Śledzenie biegu GPS", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Powiadomienie aktywnego biegu" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val distKm = RunningRepository.totalDistanceMeters.value / 1000f
        val dur = RunningRepository.durationSeconds.value
        val status = if (RunningRepository.isPaused.value) "Pauza" else "Biegniesz"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$status — %.2f km".format(distKm))
            .setContentText("%02d:%02d".format(dur / 60, dur % 60))
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopLocationUpdates()
    }
}
