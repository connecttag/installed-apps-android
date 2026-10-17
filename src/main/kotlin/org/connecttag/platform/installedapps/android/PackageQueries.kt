package org.connecttag.platform.installedapps.android

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/** Lightweight synchronous helpers for package-manager queries. */
public object PackageQueries {
    public fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfoCompat(packageName, 0)
            true
        }.getOrDefault(false)
    }

    public fun isAppInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledApplications(0)
            }
            apps.any { it.packageName == packageName }
        }.getOrDefault(false)
    }

    public fun getAppVersion(context: Context, packageName: String): String? {
        return runCatching { context.packageManager.getPackageInfoCompat(packageName, 0).versionName }.getOrNull()
    }

    public fun getInstalledAppVersionCode(context: Context, packageName: String): Long? {
        return runCatching {
            val packageInfo = context.packageManager.getPackageInfoCompat(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        }.getOrNull()
    }

    public fun isAppInstalledVersionOld(context: Context, packageName: String, versionCode: Long): Boolean {
        return getInstalledAppVersionCode(context, packageName)?.let { it < versionCode } ?: false
    }

    public fun isAppEnabled(activity: Activity, packageName: String): Boolean {
        return runCatching {
            activity.packageManager.getApplicationInfoCompat(packageName, 0).enabled
        }.getOrDefault(false)
    }

    public fun getAllPackages(context: Context): List<String> {
        return runCatching {
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getInstalledPackages(flags)
            }
            packages.map { it.packageName }
        }.getOrDefault(emptyList())
    }

    public fun getInstalledApp(context: Context, packageName: String): InstalledApp? {
        return runCatching {
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
            val packageInfo = context.packageManager.getPackageInfoCompat(packageName, flags)
            toInstalledApp(context.packageManager, packageInfo)
        }.getOrNull()
    }

    internal fun toInstalledApp(packageManager: PackageManager, packageInfo: PackageInfo): InstalledApp {
        val applicationInfo = packageInfo.applicationInfo ?: ApplicationInfo().apply {
            packageName = packageInfo.packageName
            enabled = true
        }
        val appName = applicationInfo.loadLabel(packageManager).toString()
        val appType = if ((applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            AppType.SYSTEM
        } else {
            AppType.USER
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } else false

        return InstalledApp(
            packageName = packageInfo.packageName,
            appName = appName,
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            installerPackageName = resolveInstallerPackageName(packageManager, packageInfo.packageName),
            appType = appType,
            isEnabled = applicationInfo.enabled,
            isSuspended = isSuspended,
            requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty(),
            installTimeMillis = packageInfo.firstInstallTime,
            updateTimeMillis = packageInfo.lastUpdateTime,
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveInstallerPackageName(packageManager: PackageManager, packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        } else {
            packageManager.getInstallerPackageName(packageName)
        }
    }.getOrNull()
}

@Suppress("DEPRECATION")
internal fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
private fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getApplicationInfo(packageName, flags)
    }


