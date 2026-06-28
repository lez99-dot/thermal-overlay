# Thermal Overlay

**Thermal Overlay** is a high-precision, low-overhead, real-time CPU & GPU temperature and performance tracker for Android. It is designed to help power users, gamers, and developers monitor device thermals and processor utilization dynamically. By providing an interactive floating overlay window that hovers over other applications, users can monitor their system hardware health during demanding tasks like mobile gaming, device emulation (e.g., Winlator, termux), or heavy compiling.

---

## What the App is Made For

On modern Android devices, thermal throttling can severely impact gaming, emulator performance, and device stability. Standard system monitoring tools are often bulky, inaccurate, or fail to display while full-screen applications are active. 

**Thermal Overlay** is custom-engineered to solve this:
- **Game & Emulator Companion**: Run alongside demanding applications (like Windows/PC emulators or high-end 3D games) to visualize precisely when and why performance drops due to thermal limits.
- **Hardware Diagnostics**: Easily auto-detect, audit, and log the system's thermal zone configurations (`/sys/class/thermal`).
- **Precision Auditing**: Map the internal SoC models to their official marketing names and track precise active loads under stress.

---

## Core Capabilities & Features

### 1. Real-Time Hardware Performance Monitor
- **Dual Gauge Dashboard**: Features high-contrast, visually responsive, Material 3 gauges for both CPU Core Temperature and GPU Core Temperature.
- **Dynamic Usage Load**: Real-time load calculations for both CPU and GPU, calculated directly from Linux kernel statistics (`/proc/stat` and `/sys/class/kgsl`).
- **Processor Identification**: Maps low-level SoC chip definitions (like Qualcomm's SM/MSM parts, MediaTek's MT parts, Samsung's Exynos series, and Google's Tensor GS series) directly to user-friendly marketing names (e.g., *Snapdragon 8 Gen 3* or *Google Tensor G4*).

### 2. High-Customization Floating Overlay
- **Persistent Overlay View**: A fully lightweight floating UI window that can stay visible over other games and full-screen apps.
- **Live Usage Percentage Toggle**: Option to toggle processor load percentage numbers (`% Load`) alongside temperatures directly on the overlay.
- **Aesthetic Controls**: Complete personalization of the overlay theme—adjust background opacity, scaling sizes (from small indicator to readable panel), and custom RGB accent colors for CPU and GPU values.
- **Intuitive Gestures**: Drag the overlay freely across any screen edge, with seamless persistence.

### 3. Advanced Integration & Elevated Permission Modes
- **Multiple Thermal Backends**: Works out-of-the-box on standard devices using public Linux read APIs.
- **Shizuku Shell Mode**: Integrated support for **Shizuku** to execute ADB-privileged commands, bypassing SELinux directory restrictions to read protected kernel parameters on locked down environments.
- **Winlator SDK Support**: Direct compatibility with Winlator's thermal permission system (`com.winlator.permission.READ_THERMAL_DATA`) to retrieve direct statistics in virtual environments.
- **Custom Zone Selection**: Manual zone-picker enabling users to bind custom thermal paths (e.g., matching custom kernels, battery zones, or specific cluster sensors).

### 4. Telemetry Logging & Diagnostics
- **Historical Chart Logging**: Automatically logs thermal activity to an offline SQLite database (powered by Room) to audit long-term thermal trends.
- **Diagnostic Panel**: Live diagnostics of permissions, system services, and device specifications.
