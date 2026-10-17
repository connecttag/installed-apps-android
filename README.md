# Installed Apps Android

[![](https://jitpack.io/v/connecttag/installed-apps-android.svg)](https://jitpack.io/#connecttag/installed-apps-android)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A modern, high-performance Android library for querying, filtering, and inspecting installed applications. Fully compatible with Android 11+ Package Visibility (`<queries>`) and Android 13+ `PackageManager.ResolveInfoFlags`.

## Features

- 📱 **Package Querying**: List all installed packages or launcher applications asynchronously via Kotlin Coroutines.
- 🎯 **Advanced AppFilter**: Filter by system apps, user apps, disabled packages, specific package sets, or live search query.
- ⭐ **Default Apps Detection**: Check default system applications for Launcher, Web Browser, Dialer, SMS, and Voice Assistant.
- 📦 **Framework-Decoupled Model**: `InstalledApp` encapsulates app name, package name, version name/code, permissions, and install/update timestamps without leaking framework objects.
- 🛡️ **Zero Runtime Overhead**: Uses optimized native package queries and handles platform API deprecations gracefully.

## Installation

Add JitPack repository to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
    }
}
```

Add the dependency to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.connecttag:installed-apps-android:1.0.0")
}
```

## Quick Start

### 1. Retrieve Installed Applications

```kotlin
import org.connecttag.platform.installedapps.android.AndroidInstalledAppsProvider
import org.connecttag.platform.installedapps.android.AppFilter

val provider = AndroidInstalledAppsProvider(context)

lifecycleScope.launch {
    // Get all user-installed applications
    val userApps = provider.getInstalledApps(
        filter = AppFilter(
            includeSystemApps = false,
            includeUserApps = true
        )
    )
    userApps.forEach { app ->
        println("${app.appName} (${app.packageName}) v${app.versionName}")
    }
}
```

### 2. Query Launcher Activities

```kotlin
lifecycleScope.launch {
    val launcherApps = provider.getLauncherApps()
    println("Total launcher apps: ${launcherApps.size}")
}
```

### 3. Check Default Applications

```kotlin
import org.connecttag.platform.installedapps.android.DefaultAppCategory

val isDefaultBrowser = provider.isDefaultApp("com.android.chrome", DefaultAppCategory.BROWSER)
```

## License

```
Copyright 2026 Connect Tag

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
