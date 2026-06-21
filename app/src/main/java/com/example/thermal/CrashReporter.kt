package com.example.thermal

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val TAG = "CrashReporter"
    
    private val sdcardDir: File
        get() = File(Environment.getExternalStorageDirectory(), "Thermal Overlay")

    private val sdcardFallbackDir: File
        get() = File("/sdcard/Thermal Overlay")

    /**
     * Initializes the uncaught exception handler to intercept crashes and log them to file.
     */
    fun initialize(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(context, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing crash log: ${e.message}", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    /**
     * Checks if /sdcard/Thermal Overlay is writable.
     */
    fun checkDirectoryStatus(): DirectoryStatus {
        val target = sdcardDir
        var exists = target.exists()
        var canWrite = false
        try {
            if (!exists) {
                exists = target.mkdirs()
            }
            if (exists) {
                val testFile = File(target, ".write_test")
                val created = testFile.createNewFile()
                if (testFile.exists()) {
                    testFile.delete()
                    canWrite = true
                }
            }
        } catch (e: Exception) {
            canWrite = false
        }

        return DirectoryStatus(
            path = target.absolutePath,
            exists = exists,
            writable = canWrite
        )
    }

    /**
     * Writes a manual test crash log without crashing the application.
     */
    fun writeTestLog(context: Context): String {
        val dummyException = RuntimeException("Manually triggered test log for Thermal Overlay")
        val thread = Thread.currentThread()
        val filePaths = writeCrashLog(context, thread, dummyException, isTest = true)
        return filePaths.firstOrNull() ?: "Failed to write crash log (Permission Denied)"
    }

    /**
     * Writes the exception stack trace and device metadata to the external directory
     * /sdcard/Thermal Overlay. Fallback locations are used if external storage is inaccessible.
     */
    fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable, isTest: Boolean = false): List<String> {
        val formattedLog = formatException(context, thread, throwable, isTest)
        val writtenPaths = mutableListOf<String>()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val suffix = if (isTest) "_test" else ""
        val fileNameTimestamped = "crash_log_$timestamp$suffix.txt"
        val fileNameLatest = "crash_log$suffix.txt"

        val targets = listOf(
            sdcardDir,
            sdcardFallbackDir,
            context.getExternalFilesDir(null),
            context.cacheDir
        ).filterNotNull()

        for (dir in targets) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                if (dir.exists()) {
                    val fileTimestamped = File(dir, fileNameTimestamped)
                    val fileLatest = File(dir, fileNameLatest)

                    FileWriter(fileTimestamped).use { it.write(formattedLog) }
                    FileWriter(fileLatest).use { it.write(formattedLog) }

                    writtenPaths.add(fileTimestamped.absolutePath)
                    Log.d(TAG, "Success writing log to: ${fileTimestamped.absolutePath}")
                    
                    // Stop on first successful directory write
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing crash log to ${dir.absolutePath}: ${e.message}")
            }
        }
        return writtenPaths
    }

    private fun formatException(context: Context, thread: Thread, throwable: Throwable, isTest: Boolean): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTraceStr = sw.toString()

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val appVersionName = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "1.0"
        }

        return buildString {
            appendLine("==================================================")
            appendLine("             THERMAL OVERLAY CRASH LOG            ")
            appendLine("==================================================")
            appendLine("Type: ${if (isTest) "MANUAL TEST LOG" else "UNCAUGHT EXCEPTION CRASH"}")
            appendLine("App Name: Thermal Overlay")
            appendLine("Package: ${context.packageName}")
            appendLine("App Version: $appVersionName")
            appendLine("Current Time: $timeStr")
            appendLine("--------------------------------------------------")
            appendLine("Device Information:")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("Hardware: ${Build.HARDWARE}")
            appendLine("OS Release: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Fingerprint: ${Build.FINGERPRINT}")
            appendLine("--------------------------------------------------")
            appendLine("Thread Context:")
            appendLine("Thread ID: ${thread.id}")
            appendLine("Thread Name: ${thread.name}")
            appendLine("Thread Priority: ${thread.priority}")
            appendLine("--------------------------------------------------")
            appendLine("Exception details:")
            appendLine("Exception Class: ${throwable.javaClass.name}")
            appendLine("Exception Message: ${throwable.message ?: "No message provided"}")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(stackTraceStr)
            appendLine("==================================================")
        }
    }

    data class DirectoryStatus(
        val path: String,
        val exists: Boolean,
        val writable: Boolean
    )
}
