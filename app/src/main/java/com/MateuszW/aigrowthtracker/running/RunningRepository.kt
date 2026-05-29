package com.MateuszW.aigrowthtracker.running

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RunPoint(val latitude: Double, val longitude: Double)

object RunningRepository {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _totalDistanceMeters = MutableStateFlow(0f)
    val totalDistanceMeters: StateFlow<Float> = _totalDistanceMeters

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds

    private val _currentSpeedKmh = MutableStateFlow(0f)
    val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh

    private val _avgPaceSecPerKm = MutableStateFlow(0f)
    val avgPaceSecPerKm: StateFlow<Float> = _avgPaceSecPerKm

    private val _routePoints = MutableStateFlow<List<RunPoint>>(emptyList())
    val routePoints: StateFlow<List<RunPoint>> = _routePoints

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId

    fun setRunning(value: Boolean) { _isRunning.value = value }
    fun setPaused(value: Boolean) { _isPaused.value = value }
    fun setTotalDistance(value: Float) { _totalDistanceMeters.value = value }
    fun setDuration(value: Long) { _durationSeconds.value = value }
    fun setCurrentSpeed(value: Float) { _currentSpeedKmh.value = value }
    fun setAvgPace(value: Float) { _avgPaceSecPerKm.value = value }
    fun addRoutePoint(point: RunPoint) { _routePoints.value = _routePoints.value + point }
    fun setCurrentSessionId(id: Int?) { _currentSessionId.value = id }

    fun reset() {
        _isRunning.value = false
        _isPaused.value = false
        _totalDistanceMeters.value = 0f
        _durationSeconds.value = 0L
        _currentSpeedKmh.value = 0f
        _avgPaceSecPerKm.value = 0f
        _routePoints.value = emptyList()
        _currentSessionId.value = null
    }
}
