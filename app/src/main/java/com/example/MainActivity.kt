package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.thermal.ThermalViewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: ThermalViewModel by viewModels()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkOverlayPermission()
    }

    private val winlatorPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadZones()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val logFile = File(getExternalFilesDir(null), "error_log.txt")
            logFile.appendText("\n--- CRASH AT ${System.currentTimeMillis()} ---\n")
            logFile.appendText(throwable.stackTraceToString())
            System.exit(2)
        }

        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        viewModel = viewModel,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestShizukuPermission = { requestShizukuPermission() },
                        onRequestWinlatorPermission = { requestWinlatorPermission() }
                    )
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestWinlatorPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            winlatorPermissionLauncher.launch("com.winlator.permission.READ_THERMAL_DATA")
        }
    }

    private fun requestShizukuPermission() {
        // Add implementation for Shizuku permission request
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkOverlayPermission()
        viewModel.checkShizukuStatus()
        viewModel.checkWinlatorStatus()
        viewModel.refreshZones()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

// Composable functions below
@Composable
fun DashboardScreen(
    viewModel: ThermalViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRequestWinlatorPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cpuTemp by viewModel.currentCpuTemp.collectAsState()
    val gpuTemp by viewModel.currentGpuTemp.collectAsState()
    val overlayEnabled by viewModel.overlayEnabled.collectAsState()
    val useShizuku by viewModel.useShizuku.collectAsState()
    val useWinlatorSdk by viewModel.useWinlatorSdk.collectAsState()
    val updateIntervalMs by viewModel.updateIntervalMs.collectAsState()
    val overlayScale by viewModel.overlayScale.collectAsState()
    val overlayBgOpacity by viewModel.overlayBgOpacity.collectAsState()
    
    val shizukuInstalled by viewModel.shizukuInstalled.collectAsState()
    val shizukuRunning by viewModel.shizukuRunning.collectAsState()
    val shizukuPermissionGranted by viewModel.shizukuPermissionGranted.collectAsState()
    val winlatorInstalled by viewModel.winlatorInstalled.collectAsState()
    val winlatorPermissionGranted by viewModel.winlatorPermissionGranted.collectAsState()
    val overlayPermissionGranted by viewModel.overlayPermissionGranted.collectAsState()

    val telemetryHistory by viewModel.telemetryHistory.collectAsState()
    val zoneList by viewModel.zoneList.collectAsState()
    val cpuZone by viewModel.cpuZone.collectAsState()
    val gpuZone by viewModel.gpuZone.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Live, 1: Preferences, 2: Diagnostics

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // App Header
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Thermal Overlay",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (shizukuRunning && shizukuPermissionGranted) MaterialTheme.colorScheme.primary else Color(0xFFFF5E3A),
                                shape = RoundedCornerShape(3.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (shizukuRunning && shizukuPermissionGranted) "Shizuku Connected" else "Direct Reading Mode",
                        color = if (shizukuRunning && shizukuPermissionGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Pulse Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (overlayEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (overlayEnabled) "WIDGET ACTIVE" else "IDLE",
                    color = if (overlayEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector Row
        TabSelector(
            tabs = listOf("LIVE DISPLAY", "PREFERENCES", "DIAGNOSTICS"),
            selectedTab = activeTab,
            onTabSelected = { activeTab = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Main Tab Content
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                0 -> {
                    LiveDisplayTab(
                        cpuTemp = cpuTemp,
                        gpuTemp = gpuTemp,
                        telemetryHistory = telemetryHistory,
                        overlayEnabled = overlayEnabled,
                        overlayPermissionGranted = overlayPermissionGranted,
                        shizukuRunning = shizukuRunning,
                        shizukuPermissionGranted = shizukuPermissionGranted,
                        useShizuku = useShizuku,
                        onToggleOverlay = { viewModel.toggleOverlay(it) },
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
                1 -> {
                    PreferencesTab(
                        viewModel = viewModel,
                        overlayEnabled = overlayEnabled,
                        useShizuku = useShizuku,
                        useWinlatorSdk = useWinlatorSdk,
                        updateIntervalMs = updateIntervalMs,
                        scaleFactor = overlayScale,
                        overlayBgOpacity = overlayBgOpacity,
                        shizukuInstalled = shizukuInstalled,
                        shizukuRunning = shizukuRunning,
                        shizukuPermissionGranted = shizukuPermissionGranted,
                        winlatorInstalled = winlatorInstalled,
                        winlatorPermissionGranted = winlatorPermissionGranted,
                        overlayPermissionGranted = overlayPermissionGranted,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onRequestShizukuPermission = onRequestShizukuPermission,
                        onRequestWinlatorPermission = onRequestWinlatorPermission,
                        zoneList = zoneList,
                        cpuZone = cpuZone,
                        gpuZone = gpuZone
                    )
                }
                2 -> {
                    DiagnosticsTab(
                        zoneList = zoneList,
                        useShizuku = useShizuku,
                        onRefresh = { viewModel.refreshZones() }
                    )
                }
            }
        }
    }
}

@Composable
fun TabSelector(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = selectedTab == index
            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(animatedBgColor)
                    .clickable { onTabSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

@Composable
fun LiveDisplayTab(
    cpuTemp: Float?,
    gpuTemp: Float?,
    telemetryHistory: List<ThermalReading>,
    overlayEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    shizukuRunning: Boolean,
    shizukuPermissionGranted: Boolean,
    useShizuku: Boolean,
    onToggleOverlay: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Overlay Enable Quick Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Temp Screen Overlay",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (overlayPermissionGranted) {
                                "Tap to activate hovering overlay indicators"
                            } else {
                                "Needs System Alert Window permission"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    
                    if (!overlayPermissionGranted) {
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("GRANT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Switch(
                            checked = overlayEnabled,
                            onCheckedChange = { onToggleOverlay(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
        }

        // Live Real-Time Gauges Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedGaugeCard(
                        title = "CPU Core Temp",
                        value = cpuTemp,
                        primaryColor = Color(0xFF33B5E5), // Cold Blue
                        maxTemp = 85f
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedGaugeCard(
                        title = "GPU Core Temp",
                        value = gpuTemp,
                        primaryColor = Color(0xFFFF4444), // Crimson Hot
                        maxTemp = 85f
                    )
                }
            }
        }

        // Shizuku Warnings State Alert
        if (useShizuku && (!shizukuRunning || !shizukuPermissionGranted)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D211A)),
                    border = BorderStroke(1.dp, Color(0xFFFF5E3A).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠",
                            color = Color(0xFFFF5E3A),
                            fontSize = 20.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "Shizuku Service Inactive",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Preferences dictate Shizuku reads, but it is currently not running or not granted.",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Visual Historical Telemetry Chart
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Historic Telemetry Log",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Temperature changes over operating timeline",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        
                        Text(
                            text = "RESET LOGS",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onClearHistory() }
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (telemetryHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No readings logged yet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text("Telemetry is recorded periodically in background", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                        }
                    } else {
                        ThermalDashboardChart(
                            history = telemetryHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedGaugeCard(
    title: String,
    value: Float?,
    primaryColor: Color,
    maxTemp: Float
) {
    val displayVal = value ?: 0.0f
    val sweepAngle = (displayVal / maxTemp).coerceIn(0f, 1.0f) * 180f
    
    val animatedSweep by animateFloatAsState(
        targetValue = sweepAngle,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw outer track
                    drawArc(
                         color = Color.White.copy(alpha = 0.03f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    
                    // Draw filled temp arc
                    drawArc(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryColor.copy(alpha = 0.6f), primaryColor)
                        ),
                        startAngle = 180f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = 12.dp)
                ) {
                    Text(
                        text = value?.let { String.format("%.1f°", it) } ?: "--.-°",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "CELSIUS",
                        color = primaryColor.copy(alpha = 0.8f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ThermalDashboardChart(
    history: List<ThermalReading>,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (history.isEmpty()) return@Canvas

        val maxCpu = history.maxOfOrNull { it.cpuTemp } ?: 80f
        val maxGpu = history.maxOfOrNull { it.gpuTemp } ?: 80f
        val maxVal = maxOf(60f, maxOf(maxCpu, maxGpu)) + 5f

        val minCpu = history.minOfOrNull { it.cpuTemp } ?: 30f
        val minGpu = history.minOfOrNull { it.gpuTemp } ?: 30f
        val minVal = minOf(30f, minOf(minCpu, minGpu)) - 5f

        val valueRange = maxVal - minVal

        // Grid lines
        val cols = 5
        val colWidth = size.width / (cols - 1)
        for (i in 0 until cols) {
            val x = i * colWidth
            drawLine(
                color = Color.White.copy(alpha = 0.03f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }

        val rows = 3
        val rowHeight = size.height / (rows - 1)
        for (i in 0 until rows) {
            val y = i * rowHeight
            drawLine(
                color = Color.White.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val itemsCount = history.size
        if (itemsCount < 2) return@Canvas

        val stepX = size.width / (itemsCount - 1)

        val cpuPath = Path()
        val gpuPath = Path()

        history.forEachIndexed { index, reading ->
            val cx = index * stepX
            
            val cpuY = size.height - ((reading.cpuTemp - minVal) / valueRange) * size.height
            val gpuY = size.height - ((reading.gpuTemp - minVal) / valueRange) * size.height

            if (index == 0) {
                cpuPath.moveTo(cx, cpuY)
                gpuPath.moveTo(cx, gpuY)
            } else {
                cpuPath.lineTo(cx, cpuY)
                gpuPath.lineTo(cx, gpuY)
            }
        }

        // Draw CPU path
        drawPath(
            path = cpuPath,
            color = Color(0xFF33B5E5),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw GPU path
        drawPath(
            path = gpuPath,
            color = Color(0xFFFF4444),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(Color(0xFF33B5E5), RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text("CPU TEMPERATURE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 20.dp))

        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF4444), RoundedCornerShape(4.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Text("GPU TEMPERATURE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PreferencesTab(
    viewModel: ThermalViewModel,
    overlayEnabled: Boolean,
    useShizuku: Boolean,
    useWinlatorSdk: Boolean,
    updateIntervalMs: Long,
    scaleFactor: Float,
    overlayBgOpacity: Float,
    shizukuInstalled: Boolean,
    shizukuRunning: Boolean,
    shizukuPermissionGranted: Boolean,
    winlatorInstalled: Boolean,
    winlatorPermissionGranted: Boolean,
    overlayPermissionGranted: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onRequestWinlatorPermission: () -> Unit,
    zoneList: List<ThermalZoneInfo>,
    cpuZone: String,
    gpuZone: String
) {
    var showColorMenu by remember { mutableStateOf(false) }

    var sizeInputText by remember { mutableStateOf("") }
    var opacityInputText by remember { mutableStateOf("") }

    // Sync from viewmodel when they change
    LaunchedEffect(scaleFactor) {
        sizeInputText = String.format("%.2f", scaleFactor)
    }
    LaunchedEffect(overlayBgOpacity) {
        opacityInputText = String.format("%d", (overlayBgOpacity * 100).toInt())
    }

    val onSizeTextChange = { newVal: String ->
        val filtered = newVal.filter { it.isDigit() || it == '.' }
        if (filtered.length <= 4) {
            sizeInputText = filtered
            val parsed = filtered.toFloatOrNull()
            if (parsed != null && parsed in 0.6f..2.5f) {
                viewModel.setOverlayScale(parsed)
            }
        }
    }

    val onOpacityTextChange = { newVal: String ->
        val filtered = newVal.filter { it.isDigit() }
        if (filtered.length <= 3) {
            opacityInputText = filtered
            val parsed = filtered.toFloatOrNull()
            if (parsed != null && parsed in 10f..100f) {
                viewModel.setOverlayBgOpacity(parsed / 100f)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Preferences Tab Placeholder", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DiagnosticsTab(
    zoneList: List<ThermalZoneInfo>,
    useShizuku: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("Diagnostics Tab Placeholder", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// Data classes
data class ThermalReading(
    val cpuTemp: Float,
    val gpuTemp: Float
)

data class ThermalZoneInfo(
    val name: String,
    val type: String
)
