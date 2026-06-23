package com.example.thermal

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ThermalConfig
import com.example.data.ThermalDatabase
import com.example.data.ThermalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ThermalViewModel(application: Application) : AndroidViewModel(application) {

    private val db = ThermalDatabase.getInstance(application)
    private val thermalManager = ThermalManager(application)

    private val _cpuZone = MutableStateFlow("thermal_zone21")
    val cpuZone = _cpuZone.asStateFlow()

    private val _gpuZone = MutableStateFlow("thermal_zone31")
    val gpuZone = _gpuZone.asStateFlow()

    private val _useShizuku = MutableStateFlow(false)
    val useShizuku = _useShizuku.asStateFlow()

    private val _updateIntervalMs = MutableStateFlow(1000L)
    val updateIntervalMs = _updateIntervalMs.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(false)
    val overlayEnabled = _overlayEnabled.asStateFlow()

    private val _overlayBgHex = MutableStateFlow("#B80D1118")
    val overlayBgHex = _overlayBgHex.asStateFlow()

    private val _overlayCpuHex = MutableStateFlow("#FF33B5E5")
    val overlayCpuHex = _overlayCpuHex.asStateFlow()

    private val _overlayGpuHex = MutableStateFlow("#FFFF4444")
    val overlayGpuHex = _overlayGpuHex.asStateFlow()

    private val _overlayScale = MutableStateFlow(1.0f)
    val overlayScale = _overlayScale.asStateFlow()

    private val _overlayBgOpacity = MutableStateFlow(0.72f)
    val overlayBgOpacity = _overlayBgOpacity.asStateFlow()

    private val _currentCpuTemp = MutableStateFlow<Float?>(null)
    val currentCpuTemp = _currentCpuTemp.asStateFlow()

    private val _currentGpuTemp = MutableStateFlow<Float?>(null)
    val currentGpuTemp = _currentGpuTemp.asStateFlow()

    private val _zoneList = MutableStateFlow<List<ThermalZoneInfo>>(emptyList())
    val zoneList = _zoneList.asStateFlow()

    private val _telemetryHistory = MutableStateFlow<List<ThermalReading>>(emptyList())
    val telemetryHistory = _telemetryHistory.asStateFlow()

    private val _shizukuInstalled = MutableStateFlow(false)
    val shizukuInstalled = _shizukuInstalled.asStateFlow()

    private val _shizukuRunning = MutableStateFlow(false)
    val shizukuRunning = _shizukuRunning.asStateFlow()

    private val _shizukuPermissionGranted = MutableStateFlow(false)
    val shizukuPermissionGranted = _shizukuPermissionGranted.asStateFlow()

    private val _useWinlatorSdk = MutableStateFlow(false)
    val useWinlatorSdk = _useWinlatorSdk.asStateFlow()

    private val _winlatorInstalled = MutableStateFlow(false)
    val winlatorInstalled = _winlatorInstalled.asStateFlow()

    private val _winlatorPermissionGranted = MutableStateFlow(false)
    val winlatorPermissionGranted = _winlatorPermissionGranted.asStateFlow()

    private val _overlayPermissionGranted = MutableStateFlow(false)
    val overlayPermissionGranted = _overlayPermissionGranted.asStateFlow()

    private val _cpuName = MutableStateFlow("CPU")
    val cpuName = _cpuName.asStateFlow()

    private val _gpuName = MutableStateFlow("GPU")
    val gpuName = _gpuName.asStateFlow()

    init {
        loadAllConfig()
        checkOverlayPermission()
        checkShizukuStatus()
        checkWinlatorStatus()
        observeTelemetry()
        startDashboardPolling()
        loadHardwareNames()
    }

    private fun loadHardwareNames() {
        viewModelScope.launch(Dispatchers.Default) {
            val cpu = thermalManager.getCpuName()
            val gpu = thermalManager.getGpuName()
            _cpuName.value = cpu
            _gpuName.value = gpu
        }
    }

    private fun loadAllConfig() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _cpuZone.value = db.dao().getConfig("cpu_zone")?.value ?: "thermal_zone21"
                _gpuZone.value = db.dao().getConfig("gpu_zone")?.value ?: "thermal_zone31"
                _useShizuku.value = db.dao().getConfig("use_shizuku")?.value?.toBoolean() ?: false
                _useWinlatorSdk.value = db.dao().getConfig("use_winlator_sdk")?.value?.toBoolean() ?: false
                _updateIntervalMs.value = db.dao().getConfig("update_interval_ms")?.value?.toLongOrNull() ?: 1000L
                _overlayBgHex.value = db.dao().getConfig("overlay_background_color")?.value ?: "#B80D1118"
                _overlayCpuHex.value = db.dao().getConfig("overlay_cpu_color")?.value ?: "#FF33B5E5"
                _overlayGpuHex.value = db.dao().getConfig("overlay_gpu_color")?.value ?: "#FFFF4444"
                _overlayScale.value = db.dao().getConfig("overlay_size_scale")?.value?.toFloatOrNull() ?: 1.0f
                _overlayBgOpacity.value = db.dao().getConfig("overlay_background_opacity")?.value?.toFloatOrNull() ?: 0.72f
                
                val savedOverlayEnabled = db.dao().getConfig("overlay_enabled")?.value?.toBoolean() ?: false
                val updatedOverlay = if (savedOverlayEnabled && !Settings.canDrawOverlays(getApplication())) {
                    db.dao().insertConfig(ThermalConfig("overlay_enabled", "false"))
                    false
                } else {
                    savedOverlayEnabled
                }
                _overlayEnabled.value = updatedOverlay
            }
            refreshZones()
        }
    }

    fun checkOverlayPermission() {
        val granted = Settings.canDrawOverlays(getApplication())
        _overlayPermissionGranted.value = granted
        if (!granted && _overlayEnabled.value) {
            _overlayEnabled.value = false
            saveConfig("overlay_enabled", "false")
            val context = getApplication<Application>()
            val intent = Intent(context, ThermalOverlayService::class.java)
            context.stopService(intent)
        }
    }

    fun checkShizukuStatus() {
        val pm = getApplication<Application>().packageManager
        val isInstalled = try {
            pm.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: Exception) {
            false
        }
        _shizukuInstalled.value = isInstalled

        val isRunning = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
        _shizukuRunning.value = isRunning

        val isGranted = try {
            if (isRunning) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
        _shizukuPermissionGranted.value = isGranted
    }

    fun checkWinlatorStatus() {
        _winlatorInstalled.value = thermalManager.isWinlatorInstalled()
        _winlatorPermissionGranted.value = thermalManager.isWinlatorPermissionGranted()
    }

    fun refreshZones() {
        viewModelScope.launch {
            val zones = withContext(Dispatchers.IO) {
                if (_useShizuku.value && _shizukuPermissionGranted.value) {
                    thermalManager.getThermalZonesViaShizuku()
                } else if (_useWinlatorSdk.value) {
                    thermalManager.getThermalZonesViaWinlatorSdk()
                } else {
                    thermalManager.getThermalZonesDirectly()
                }
            }
            _zoneList.value = zones
            
            // Auto-detect cpu / gpu zone naming defaults
            if (zones.isNotEmpty()) {
                val currentCpu = _cpuZone.value
                val isCurrentCpuValid = zones.any { it.name == currentCpu }
                if (!isCurrentCpuValid || currentCpu == "thermal_zone21") {
                    val bestCpu = zones.find { it.type.contains("cpu", ignoreCase = true) || it.type.contains("cpuss", ignoreCase = true) }?.name
                    if (bestCpu != null) {
                        setCpuZone(bestCpu)
                    }
                }

                val currentGpu = _gpuZone.value
                val isCurrentGpuValid = zones.any { it.name == currentGpu }
                if (!isCurrentGpuValid || currentGpu == "thermal_zone31") {
                    val bestGpu = zones.find { it.type.contains("gpu", ignoreCase = true) || it.type.contains("gpuss", ignoreCase = true) }?.name
                    if (bestGpu != null) {
                        setGpuZone(bestGpu)
                    }
                }
            }
        }
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            db.dao().getRecentReadingsFlow().collect { readings ->
                _telemetryHistory.value = readings.sortedBy { it.timestamp }
            }
        }
    }

    private fun startDashboardPolling() {
        viewModelScope.launch {
            while (true) {
                val (cpu, gpu) = withContext(Dispatchers.IO) {
                    thermalManager.readTemperatures(_cpuZone.value, _gpuZone.value, _useShizuku.value, _useWinlatorSdk.value)
                }
                _currentCpuTemp.value = cpu
                _currentGpuTemp.value = gpu
                
                if (!_overlayEnabled.value && cpu != null && gpu != null) {
                    withContext(Dispatchers.IO) {
                        thermalManager.recordTelemetry(cpu, gpu)
                    }
                }
                
                delay(1000)
            }
        }
    }

    fun setCpuZone(zone: String) {
        _cpuZone.value = zone
        saveConfig("cpu_zone", zone)
        triggerOverlayRefresh()
    }

    fun setGpuZone(zone: String) {
        _gpuZone.value = zone
        saveConfig("gpu_zone", zone)
        triggerOverlayRefresh()
    }

    fun setUseShizuku(use: Boolean) {
        _useShizuku.value = use
        saveConfig("use_shizuku", use.toString())
        refreshZones()
        triggerOverlayRefresh()
    }

    fun setUseWinlatorSdk(use: Boolean) {
        _useWinlatorSdk.value = use
        saveConfig("use_winlator_sdk", use.toString())
        refreshZones()
        triggerOverlayRefresh()
    }

    fun setWinlatorPermissionGranted(granted: Boolean) {
        _winlatorPermissionGranted.value = granted
        refreshZones()
        triggerOverlayRefresh()
    }

    fun setUpdateInterval(intervalMs: Long) {
        _updateIntervalMs.value = intervalMs
        saveConfig("update_interval_ms", intervalMs.toString())
        triggerOverlayRefresh()
    }

    fun setOverlayScale(scale: Float) {
        _overlayScale.value = scale
        saveConfig("overlay_size_scale", scale.toString())
        triggerOverlayRefresh()
    }

    fun setOverlayBgOpacity(opacity: Float) {
        _overlayBgOpacity.value = opacity
        saveConfig("overlay_background_opacity", opacity.toString())
        triggerOverlayRefresh()
    }

    fun setOverlayColors(bg: String, cpu: String, gpu: String) {
        _overlayBgHex.value = bg
        _overlayCpuHex.value = cpu
        _overlayGpuHex.value = gpu
        saveConfig("overlay_background_color", bg)
        saveConfig("overlay_cpu_color", cpu)
        saveConfig("overlay_gpu_color", gpu)
        triggerOverlayRefresh()
    }

    fun toggleOverlay(enabled: Boolean) {
        val context = getApplication<Application>()
        val intent = Intent(context, ThermalOverlayService::class.java)
        if (enabled) {
            if (Settings.canDrawOverlays(context)) {
                _overlayEnabled.value = true
                saveConfig("overlay_enabled", "true")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                _overlayEnabled.value = false
                saveConfig("overlay_enabled", "false")
            }
        } else {
            _overlayEnabled.value = false
            saveConfig("overlay_enabled", "false")
            context.stopService(intent)
        }
    }

    private fun triggerOverlayRefresh() {
        val context = getApplication<Application>()
        if (_overlayEnabled.value && Settings.canDrawOverlays(context)) {
            val intent = Intent(context, ThermalOverlayService::class.java).apply {
                action = ThermalOverlayService.ACTION_REFRESH_SETTINGS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else if (_overlayEnabled.value) {
            _overlayEnabled.value = false
            saveConfig("overlay_enabled", "false")
        }
    }

    private fun saveConfig(key: String, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.dao().insertConfig(ThermalConfig(key, value))
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            db.dao().clearAllReadings()
        }
    }
}
