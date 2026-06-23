package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.thermal.parseHexColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ThermalReading
import com.example.thermal.ThermalViewModel
import com.example.thermal.ThermalZoneInfo
import com.example.ui.theme.MyApplicationTheme
import rikka.shizuku.Shizuku
import com.example.thermal.CrashReporter
import androidx.compose.foundation.text.selection.SelectionContainer

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
        viewModel.setWinlatorPermissionGranted(isGranted)
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        viewModel.checkShizukuStatus()
        viewModel.refreshZones()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    DashboardScreen(
                        viewModel = viewModel,
                        onRequestOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                        onRequestShizukuPermission = {
                            try {
                                if (Shizuku.pingBinder()) {
                                    Shizuku.requestPermission(1001)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onRequestWinlatorPermission = {
                            try {
                                winlatorPermissionLauncher.launch("com.winlator.permission.READ_THERMAL_DATA")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
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
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

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
    val cpuName by viewModel.cpuName.collectAsState()
    val gpuName by viewModel.gpuName.collectAsState()
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
                                CircleShape
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
                            CircleShape
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
                        onClearHistory = { viewModel.clearHistory() },
                        cpuName = cpuName,
                        gpuName = gpuName
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
    onClearHistory: () -> Unit,
    cpuName: String = "CPU",
    gpuName: String = "GPU"
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
                        maxTemp = 85f,
                        subtitle = cpuName
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedGaugeCard(
                        title = "GPU Core Temp",
                        value = gpuTemp,
                        primaryColor = Color(0xFFFF4444), // Crimson Hot
                        maxTemp = 85f,
                        subtitle = gpuName
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
    maxTemp: Float,
    subtitle: String? = null
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
            
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
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
    Canvas(modifier = modifier) {
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
        Box(modifier = Modifier.size(8.dp).background(Color(0xFF33B5E5), CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text("CPU TEMPERATURE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 20.dp))

        Box(modifier = Modifier.size(8.dp).background(Color(0xFFFF4444), CircleShape))
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
        // Shizuku Toggle configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Elevated Permissions (Shizuku)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Required on modern Android if direct file-access is restricted by standard sandboxed user limits",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        
                        Switch(
                            checked = useShizuku,
                            onCheckedChange = { viewModel.setUseShizuku(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }

                    if (useShizuku) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Shizuku Service Status",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        !shizukuInstalled -> "Shizuku Manager is not installed."
                                        !shizukuRunning -> "Shizuku Manager installed, binder inactive."
                                        !shizukuPermissionGranted -> "Shizuku running. Permission Required!"
                                        else -> "Connected & Authorized."
                                    },
                                    color = if (shizukuRunning && shizukuPermissionGranted) MaterialTheme.colorScheme.primary else Color(0xFFFF5E3A),
                                    fontSize = 11.sp
                                )
                            }

                            if (shizukuRunning && !shizukuPermissionGranted) {
                                Button(
                                    onClick = onRequestShizukuPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("AUTHORIZE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (!shizukuInstalled) {
                                Button(
                                    onClick = { /* trigger url intent */ },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("GET SHIZUKU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Winlator SDK Toggle configuration
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Winlator SDK Permission Mode",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Reads thermal registers status without Shizuku when running inside/alongside custom Winlator environments",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        
                        Switch(
                            checked = useWinlatorSdk,
                            onCheckedChange = { viewModel.setUseWinlatorSdk(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }

                    if (useWinlatorSdk) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Winlator SDK / Permission Status",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = when {
                                        !winlatorPermissionGranted -> "com.winlator.permission.READ_THERMAL_DATA: Required!"
                                        else -> "Authorized & active."
                                    },
                                    color = if (winlatorPermissionGranted) MaterialTheme.colorScheme.primary else Color(0xFFFF5E3A),
                                    fontSize = 11.sp
                                )
                            }

                            if (!winlatorPermissionGranted) {
                                Button(
                                    onClick = onRequestWinlatorPermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("GRANT PERMISSION", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Zone Picking config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Thermal Mapping Config",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Map core registers representing hardware components",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("CPU TEMPERATURE SOURCE REGISTER", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ZoneSelectorDropdown(
                        onSelected = { viewModel.setCpuZone(it) },
                        selectedZone = cpuZone,
                        zoneList = zoneList
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("GPU TEMPERATURE SOURCE REGISTER", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    ZoneSelectorDropdown(
                        onSelected = { viewModel.setGpuZone(it) },
                        selectedZone = gpuZone,
                        zoneList = zoneList
                    )
                }
            }
        }

        // Overlay Style customization Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Visual Overlay Customization",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Style floating widget dimensions & transparency presets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Update frequency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Telemetry Query Rate", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Text(
                            text = "${updateIntervalMs}ms",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = updateIntervalMs.toFloat(),
                        onValueChange = { viewModel.setUpdateInterval(it.toLong()) },
                        valueRange = 250f..5000f,
                        steps = 19,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.05f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Widget sizing scale factor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Widget Dimension Scale", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Text(
                            text = String.format("%.2fx", scaleFactor),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = scaleFactor,
                        onValueChange = { viewModel.setOverlayScale(it) },
                        valueRange = 0.6f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Sizing Quick Input & Step controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Size Fine-tuning:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        // Decrement Button
                        IconButton(
                            onClick = {
                                val nextScale = (scaleFactor - 0.1f).coerceIn(0.6f, 2.5f)
                                viewModel.setOverlayScale(nextScale)
                            },
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text("-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Text Input Field
                        OutlinedTextField(
                            value = sizeInputText,
                            onValueChange = onSizeTextChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.width(76.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Increment Button
                        IconButton(
                            onClick = {
                                val nextScale = (scaleFactor + 0.1f).coerceIn(0.6f, 2.5f)
                                viewModel.setOverlayScale(nextScale)
                            },
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text("+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Background Transparency Opacity Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Background Opacity", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                        Text(
                            text = String.format("%d%%", (overlayBgOpacity * 100).toInt()),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = overlayBgOpacity,
                        onValueChange = { viewModel.setOverlayBgOpacity(it) },
                        valueRange = 0.10f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Opacity Quick Input & Step controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity Fine-tuning (%):",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        // Decrement Button
                        IconButton(
                            onClick = {
                                val nextOpacity = (overlayBgOpacity - 0.05f).coerceIn(0.10f, 1.0f)
                                viewModel.setOverlayBgOpacity(nextOpacity)
                            },
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text("-", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Text Input Field
                        OutlinedTextField(
                            value = opacityInputText,
                            onValueChange = onOpacityTextChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.width(76.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        // Increment Button
                        IconButton(
                            onClick = {
                                val nextOpacity = (overlayBgOpacity + 0.05f).coerceIn(0.10f, 1.0f)
                                viewModel.setOverlayBgOpacity(nextOpacity)
                            },
                            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            Text("+", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Preset palette selections
                    Text("OVERLAY STYLE PRESETS", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PresetChip(
                                name = "Sophisticated",
                                previewBg = "#E01C1B1F",
                                previewCpu = "#FFD0BCFF",
                                previewGpu = "#FFEFB8C8",
                                onClick = { viewModel.setOverlayColors("#E01C1B1F", "#FFD0BCFF", "#FFEFB8C8") }
                            )
                        }
                        item {
                            PresetChip(
                                name = "Matrix",
                                previewBg = "#D0000000",
                                previewCpu = "#FF39FF14",
                                previewGpu = "#FF00FF00",
                                onClick = { viewModel.setOverlayColors("#E0000000", "#FF39FF14", "#FF00FF00") }
                            )
                        }
                        item {
                            PresetChip(
                                name = "Volcanic",
                                previewBg = "#C81A0000",
                                previewCpu = "#FFFF8800",
                                previewGpu = "#FFFF4444",
                                onClick = { viewModel.setOverlayColors("#C81A0000", "#FFFF8800", "#FFFF4444") }
                            )
                        }
                        item {
                            PresetChip(
                                name = "Cyberpunk",
                                previewBg = "#B82F002F",
                                previewCpu = "#FFFF00FF",
                                previewGpu = "#FF00FFFF",
                                onClick = { viewModel.setOverlayColors("#B82F002F", "#FFFF00FF", "#FF00FFFF") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresetChip(
    name: String,
    previewBg: String,
    previewCpu: String,
    previewGpu: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .width(48.dp)
                    .height(20.dp)
                    .background(parseHexColor(previewBg), RoundedCornerShape(4.dp))
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).background(parseHexColor(previewCpu), CircleShape))
                Box(modifier = Modifier.size(6.dp).background(parseHexColor(previewGpu), CircleShape))
            }
        }
    }
}

@Composable
fun ZoneSelectorDropdown(
    onSelected: (String) -> Unit,
    selectedZone: String,
    zoneList: List<ThermalZoneInfo>
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val matchingLabel = zoneList.find { it.name == selectedZone }?.let { "${it.name} (${it.type})" } ?: selectedZone
                Text(
                    text = matchingLabel,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(text = if (expanded) "▲" else "▼", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)))
        ) {
            if (zoneList.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No thermal zones detected", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { expanded = false }
                )
            } else {
                zoneList.forEach { zone ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = zone.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = zone.type,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        onClick = {
                            onSelected(zone.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticsTab(
    zoneList: List<ThermalZoneInfo>,
    useShizuku: Boolean,
    onRefresh: () -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("ALL") }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Thermal Registry Explorer",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Diagnose raw thermal registries",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SCAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Query filter Row
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Filter by type/name...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 13.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        focusedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fast categories Selector Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("ALL", "CPU", "GPU", "BATTERY").forEach { cat ->
                        val isSel = categoryFilter == cat
                        val bg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        val textCol = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable { categoryFilter = cat }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(cat, color = textCol, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Crash Logging diagnostics mapping
        var directoryStatus by remember { mutableStateOf(CrashReporter.checkDirectoryStatus()) }
        var statusMessage by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "External Crash Logging",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure and verify uncaught exception reporting",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Log Directory",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = directoryStatus.path,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            directoryStatus = CrashReporter.checkDirectoryStatus()
                        },
                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            if (directoryStatus.writable) Color(0xFF1E3A20) else Color(0xFF3D211A),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (directoryStatus.writable) Color(0xFF4CAF50) else Color(0xFFFF5E3A),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (directoryStatus.writable) {
                            "WRITABLE (Directory Active)"
                        } else {
                            "READ-ONLY (Fallback Enabled)"
                        },
                        color = if (directoryStatus.writable) Color(0xFF81C784) else Color(0xFFFF8A65),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!directoryStatus.writable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Android restriction: modern system isolation is in effect. Handled exception details will gracefully save to internal fallback paths too.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                statusMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val path = CrashReporter.writeTestLog(context)
                            directoryStatus = CrashReporter.checkDirectoryStatus()
                            statusMessage = "Test Log written to:\n$path"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("WRITE TEST LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            throw RuntimeException("User-triggered simulated crash in Thermal Overlay")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB3261E),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("SIMULATE CRASH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val filteredList = zoneList.filter { zone ->
            val matchesSearch = zone.name.contains(searchFilter, ignoreCase = true) || zone.type.contains(searchFilter, ignoreCase = true)
            val matchesCategory = when (categoryFilter) {
                "CPU" -> zone.type.contains("cpu", ignoreCase = true) || zone.type.contains("cpuss", ignoreCase = true)
                "GPU" -> zone.type.contains("gpu", ignoreCase = true) || zone.type.contains("gpuss", ignoreCase = true)
                "BATTERY" -> zone.type.contains("bat", ignoreCase = true) || zone.type.contains("battery", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesCategory
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching indices discovered",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList) { zone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = zone.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = zone.type,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        
                        Text(
                            text = zone.temp?.let { String.format("%.1f°C", it) } ?: "--",
                            color = if (zone.temp == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else if (zone.temp > 50f) Color(0xFFFF5E3A) else MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
