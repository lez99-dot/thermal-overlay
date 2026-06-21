package com.example.thermal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryController
import com.example.MainActivity
import com.example.data.ThermalDatabase
import kotlinx.coroutines.*

class ThermalOverlayService : Service(), ViewModelStoreOwner {

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var composeView: ComposeView? = null
    
    private val lifecycleOwner = object : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        
        override val lifecycle: Lifecycle = registry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry
        
        init {
            savedStateRegistryController.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
        }
        
        fun start() {
            registry.currentState = Lifecycle.State.STARTED
        }
        
        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private var serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private var updateIntervalMs = 1000L
    private var cpuZone = "thermal_zone21"
    private var gpuZone = "thermal_zone31"
    private var useShizuku = false
    private var useWinlatorSdk = false
    private var overlayBgHex = "#44000000"
    private var cpuHex = "#FF33B5E5"
    private var gpuHex = "#FFFF4444"
    private var scaleFactor = 1.0f
    private var overlayBgOpacity = 0.72f

    private val cpuTempState = mutableStateOf<Float?>(null)
    private val gpuTempState = mutableStateOf<Float?>(null)

    private lateinit var thermalManager: ThermalManager

    companion object {
        const val CHANNEL_ID = "thermal_overlay_channel"
        const val NOTIFICATION_ID = 4512
        const val ACTION_STOP = "com.example.thermal.STOP"
        const val ACTION_REFRESH_SETTINGS = "com.example.thermal.REFRESH"
    }

    override fun onCreate() {
        super.onCreate()
        thermalManager = ThermalManager(this)
        startForegroundCompat()
        
        loadSettingsAndStart()
    }

    private fun startForegroundCompat() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= 34) {
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (e: Exception) {
                    Log.e("ThermalOverlayService", "Failed to start foreground with TYPE_SPECIAL_USE, attempting fallback", e)
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REFRESH_SETTINGS) {
            loadSettingsAndStart()
        }
        return START_STICKY
    }

    private fun loadSettingsAndStart() {
        serviceScope.launch {
            val db = ThermalDatabase.getInstance(this@ThermalOverlayService)
            withContext(Dispatchers.IO) {
                cpuZone = db.dao().getConfig("cpu_zone")?.value ?: "thermal_zone21"
                gpuZone = db.dao().getConfig("gpu_zone")?.value ?: "thermal_zone31"
                useShizuku = db.dao().getConfig("use_shizuku")?.value?.toBoolean() ?: false
                useWinlatorSdk = db.dao().getConfig("use_winlator_sdk")?.value?.toBoolean() ?: false
                updateIntervalMs = db.dao().getConfig("update_interval_ms")?.value?.toLongOrNull() ?: 1000L
                overlayBgHex = db.dao().getConfig("overlay_background_color")?.value ?: "#B80D1118"
                cpuHex = db.dao().getConfig("overlay_cpu_color")?.value ?: "#FF33B5E5"
                gpuHex = db.dao().getConfig("overlay_gpu_color")?.value ?: "#FFFF4444"
                scaleFactor = db.dao().getConfig("overlay_size_scale")?.value?.toFloatOrNull() ?: 1.0f
                overlayBgOpacity = db.dao().getConfig("overlay_background_opacity")?.value?.toFloatOrNull() ?: 0.72f
            }
            
            setupFloatingWindow()
            restartUpdater()
        }
    }

    private var updaterJob: Job? = null
    private fun restartUpdater() {
        updaterJob?.cancel()
        updaterJob = serviceScope.launch {
            while (isActive) {
                val (cpu, gpu) = withContext(Dispatchers.IO) {
                    thermalManager.readTemperatures(cpuZone, gpuZone, useShizuku, useWinlatorSdk)
                }
                
                cpuTempState.value = cpu
                gpuTempState.value = gpu
                
                if (cpu != null && gpu != null) {
                    withContext(Dispatchers.IO) {
                        thermalManager.recordTelemetry(cpu, gpu)
                    }
                }
                
                delay(updateIntervalMs)
            }
        }
    }

    private fun setupFloatingWindow() {
        if (windowManager == null) {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        }

        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            // ignore
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            
            val db = ThermalDatabase.getInstance(this@ThermalOverlayService)
            serviceScope.launch {
                val lastX = withContext(Dispatchers.IO) { db.dao().getConfig("overlay_x")?.value?.toIntOrNull() } ?: 100
                val lastY = withContext(Dispatchers.IO) { db.dao().getConfig("overlay_y")?.value?.toIntOrNull() } ?: 200
                x = lastX
                y = lastY
                try {
                    windowManager?.updateViewLayout(overlayView, this@apply)
                } catch (e: Exception) {}
            }
        }

        overlayView = FrameLayout(this)
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(this@ThermalOverlayService)
            
            setContent {
                val cpuTemp by cpuTempState
                val gpuTemp by gpuTempState
                
                FloatingOverlayUI(
                    cpuTemp = cpuTemp,
                    gpuTemp = gpuTemp,
                    backgroundColor = parseHexColor(overlayBgHex).copy(alpha = overlayBgOpacity),
                    cpuColor = parseHexColor(cpuHex),
                    gpuColor = parseHexColor(gpuHex),
                    scale = scaleFactor,
                    onDrag = { dx, dy ->
                        layoutParams.x = (layoutParams.x + dx).coerceAtLeast(0)
                        layoutParams.y = (layoutParams.y + dy).coerceAtLeast(0)
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    },
                    onDragEnd = {
                        serviceScope.launch(Dispatchers.IO) {
                            val db = ThermalDatabase.getInstance(this@ThermalOverlayService)
                            db.dao().insertConfig(com.example.data.ThermalConfig("overlay_x", layoutParams.x.toString()))
                            db.dao().insertConfig(com.example.data.ThermalConfig("overlay_y", layoutParams.y.toString()))
                        }
                    }
                )
            }
        }
        
        overlayView?.addView(composeView)
        
        try {
            lifecycleOwner.start()
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ThermalOverlayService", "Failed to add system overlay view.", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updaterJob?.cancel()
        serviceJob.cancel()
        
        lifecycleOwner.stop()
        store.clear()
        
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Thermal Overlay Active Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ThermalOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Thermal Monitor Active")
            .setContentText("CPU/GPU Floating Overlay is running in background")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .addAction(0, "STOP OVERLAY", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}

@Composable
fun FloatingOverlayUI(
    cpuTemp: Float?,
    gpuTemp: Float?,
    backgroundColor: Color,
    cpuColor: Color,
    gpuColor: Color,
    scale: Float,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    var accumulatedDx by remember { mutableStateOf(0f) }
    var accumulatedDy by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDx = 0f
                        accumulatedDy = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDx += dragAmount.x
                        accumulatedDy += dragAmount.y
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    },
                    onDragEnd = {
                        onDragEnd()
                    }
                )
            }
            .padding(4.dp)
            .width((140 * scale).dp)
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(vertical = 6.dp, horizontal = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
            )
            
            Spacer(modifier = Modifier.height(5.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CPU",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = (11 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = cpuTemp?.let { String.format("%.1f°C", it) } ?: "--.-°C",
                    color = cpuColor,
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPU",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = (11 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = gpuTemp?.let { String.format("%.1f°C", it) } ?: "--.-°C",
                    color = gpuColor,
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length == 8) {
            val alpha = cleaned.substring(0, 2).toInt(16)
            val red = cleaned.substring(2, 4).toInt(16)
            val green = cleaned.substring(4, 6).toInt(16)
            val blue = cleaned.substring(6, 8).toInt(16)
            Color(red, green, blue, alpha)
        } else if (cleaned.length == 6) {
            val red = cleaned.substring(0, 2).toInt(16)
            val green = cleaned.substring(2, 4).toInt(16)
            val blue = cleaned.substring(4, 6).toInt(16)
            Color(red, green, blue, 255)
        } else {
            Color.Black.copy(alpha = 0.7f)
        }
    } catch (e: Exception) {
        Color.Black.copy(alpha = 0.7f)
    }
}
