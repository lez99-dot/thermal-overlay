package com.example.thermal

import android.content.Context
import android.util.Log
import android.os.Build
import android.content.pm.PackageManager
import com.example.data.ThermalDatabase
import com.example.data.ThermalReading
import rikka.shizuku.Shizuku
import java.io.File

data class ThermalZoneInfo(
    val name: String, // e.g. "thermal_zone21"
    val type: String, // e.g. "cpuss-0"
    val temp: Float? = null
)

class ThermalManager(private val context: Context) {
    private val database = ThermalDatabase.getInstance(context)

    fun isWinlatorPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.checkSelfPermission("com.winlator.permission.READ_THERMAL_DATA") == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isWinlatorInstalled(): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.winlator", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getThermalZonesDirectly(): List<ThermalZoneInfo> {
        val list = mutableListOf<ThermalZoneInfo>()
        try {
            val thermalDir = File("/sys/class/thermal")
            if (thermalDir.exists() && thermalDir.isDirectory) {
                val files = thermalDir.listFiles()
                if (files != null && files.isNotEmpty()) {
                    files.forEach { file ->
                        if (file.name.startsWith("thermal_zone")) {
                            val typeFile = File(file, "type")
                            val type = if (typeFile.exists() && typeFile.canRead()) {
                                typeFile.readText().trim()
                            } else {
                                "Unknown"
                            }
                            val temp = readTempFileDirectly(file.name)
                            list.add(ThermalZoneInfo(file.name, type, temp))
                        }
                    }
                } else {
                    scanHardcodedZones(list)
                }
            } else {
                scanHardcodedZones(list)
            }
        } catch (e: Exception) {
            Log.e("ThermalManager", "Error reading zones directly", e)
            scanHardcodedZones(list)
        }
        return list.sortedBy { 
            it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 
        }
    }

    private fun scanHardcodedZones(list: MutableList<ThermalZoneInfo>) {
        for (i in 0..80) {
            val dir = File("/sys/class/thermal/thermal_zone$i")
            if (dir.exists()) {
                val typeFile = File(dir, "type")
                val type = if (typeFile.exists() && typeFile.canRead()) {
                    typeFile.readText().trim()
                } else {
                    "Zone $i"
                }
                val temp = readTempFileDirectly("thermal_zone$i")
                list.add(ThermalZoneInfo("thermal_zone$i", type, temp))
            }
        }
    }

    private fun readTempFileDirectly(zoneName: String): Float? {
        try {
            val tempFile = File("/sys/class/thermal/$zoneName/temp")
            if (tempFile.exists() && tempFile.canRead()) {
                val value = tempFile.readText().trim().toFloatOrNull()
                if (value != null) {
                    return if (value > 1000f || value < -100f) value / 1000f else value
                }
            }
        } catch (e: Exception) {
            // silent fail
        }
        return null
    }

    private fun shizukuNewProcess(commands: Array<String>): java.lang.Process {
        val method = Shizuku::class.java.getMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, commands, null, null) as java.lang.Process
    }

    fun getThermalZonesViaShizuku(): List<ThermalZoneInfo> {
        val list = mutableListOf<ThermalZoneInfo>()
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val cmd = "for f in /sys/class/thermal/thermal_zone*; do " +
                          "if [ -d \"\$f\" ]; then " +
                          "echo \"\$(basename \$f)====\$(cat \$f/type 2>/dev/null)====\$(cat \$f/temp 2>/dev/null)\"; " +
                          "fi; done"
                val process = shizukuNewProcess(arrayOf("sh", "-c", cmd))
                val reader = process.inputStream.bufferedReader()
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.contains("====")) {
                        val parts = line.split("====")
                        if (parts.size >= 3) {
                            val name = parts[0].trim()
                            val type = parts[1].trim()
                            val rawTempValue = parts[2].trim().toFloatOrNull()
                            val temp = if (rawTempValue != null) {
                                if (rawTempValue > 1000f || rawTempValue < -100f) rawTempValue / 1000f else rawTempValue
                            } else null
                            list.add(ThermalZoneInfo(name, type, temp))
                        }
                    }
                    line = reader.readLine()
                }
                process.destroy()
            }
        } catch (e: Exception) {
            Log.e("ThermalManager", "Error reading zones via Shizuku", e)
        }
        return list.sortedBy { 
            it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 
        }
    }

    fun getThermalZonesViaWinlatorSdk(): List<ThermalZoneInfo> {
        val list = mutableListOf<ThermalZoneInfo>()
        try {
            val cmd = "for f in /sys/class/thermal/thermal_zone*; do " +
                      "if [ -d \"\$f\" ]; then " +
                      "echo \"\$(basename \$f)====\$(cat \$f/type 2>/dev/null)====\$(cat \$f/temp 2>/dev/null)\"; " +
                      "fi; done"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = process.inputStream.bufferedReader()
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.contains("====")) {
                    val parts = line.split("====")
                    if (parts.size >= 3) {
                        val name = parts[0].trim()
                        val type = parts[1].trim()
                        val rawTempValue = parts[2].trim().toFloatOrNull()
                        val temp = if (rawTempValue != null) {
                            if (rawTempValue > 1000f || rawTempValue < -100f) rawTempValue / 1000f else rawTempValue
                        } else null
                        list.add(ThermalZoneInfo(name, type, temp))
                    }
                }
                line = reader.readLine()
            }
            process.destroy()
        } catch (e: Exception) {
            Log.e("ThermalManager", "Error reading zones via Winlator SDK", e)
        }
        return list.sortedBy { 
            it.name.removePrefix("thermal_zone").toIntOrNull() ?: 999 
        }
    }

    fun readTemperaturesViaWinlatorSdk(cpuZone: String, gpuZone: String): Pair<Float?, Float?> {
        var cpu: Float? = null
        var gpu: Float? = null
        try {
            val cmd = "cat /sys/class/thermal/$cpuZone/temp 2>/dev/null; echo '----'; cat /sys/class/thermal/$gpuZone/temp 2>/dev/null"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val reader = process.inputStream.bufferedReader()
            val line1 = reader.readLine()
            val sep = reader.readLine()
            val line2 = reader.readLine()
            process.destroy()

            val cpuRaw = line1?.trim()?.toFloatOrNull()
            val gpuRaw = line2?.trim()?.toFloatOrNull()

            if (cpuRaw != null) {
                cpu = if (cpuRaw > 1000f || cpuRaw < -100f) cpuRaw / 1000f else cpuRaw
            }
            if (gpuRaw != null) {
                gpu = if (gpuRaw > 1000f || gpuRaw < -100f) gpuRaw / 1000f else gpuRaw
            }
        } catch (e: Exception) {
            Log.e("ThermalManager", "Error reading cpu/gpu temps via Winlator SDK", e)
        }
        return Pair(cpu, gpu)
    }

    fun readTemperatures(cpuZone: String, gpuZone: String, useShizuku: Boolean, useWinlatorSdk: Boolean = false): Pair<Float?, Float?> {
        var cpu: Float? = null
        var gpu: Float? = null

        if (useShizuku && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val cmd = "cat /sys/class/thermal/$cpuZone/temp 2>/dev/null; echo '----'; cat /sys/class/thermal/$gpuZone/temp 2>/dev/null"
                val process = shizukuNewProcess(arrayOf("sh", "-c", cmd))
                val reader = process.inputStream.bufferedReader()
                val line1 = reader.readLine()
                val sep = reader.readLine()
                val line2 = reader.readLine()
                process.destroy()

                val cpuRaw = line1?.trim()?.toFloatOrNull()
                val gpuRaw = line2?.trim()?.toFloatOrNull()

                if (cpuRaw != null) {
                    cpu = if (cpuRaw > 1000f || cpuRaw < -100f) cpuRaw / 1000f else cpuRaw
                }
                if (gpuRaw != null) {
                    gpu = if (gpuRaw > 1000f || gpuRaw < -100f) gpuRaw / 1000f else gpuRaw
                }
            } catch (e: Exception) {
                Log.e("ThermalManager", "Error reading cpu/gpu temps via Shizuku", e)
            }
        } else if (useWinlatorSdk) {
            val (sdkCpu, sdkGpu) = readTemperaturesViaWinlatorSdk(cpuZone, gpuZone)
            cpu = sdkCpu
            gpu = sdkGpu
        }

        // Direct standard read fallback if Shizuku failed or returned empty
        if (cpu == null) {
            cpu = readTempFileDirectly(cpuZone)
        }
        if (gpu == null) {
            gpu = readTempFileDirectly(gpuZone)
        }

        return Pair(cpu, gpu)
    }

    suspend fun recordTelemetry(cpuTemp: Float, gpuTemp: Float) {
        try {
            val reading = ThermalReading(
                timestamp = System.currentTimeMillis(),
                cpuTemp = cpuTemp,
                gpuTemp = gpuTemp
            )
            database.dao().insertReading(reading)
            
            // Auto clean older telemetry data (e.g. keep last 20 minutes)
            val cutoff = System.currentTimeMillis() - (20 * 60 * 1000)
            database.dao().clearOldReadings(cutoff)
        } catch (e: Exception) {
            Log.e("ThermalManager", "Failed to record telemetry", e)
        }
    }
}
