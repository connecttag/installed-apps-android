package org.connecttag.platform.installedapps.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.content.pm.PackageInfoCompat
import java.lang.reflect.Method

/**
 * Lightweight, safe package-manager and metadata query helpers.
 * Fully compatible with Android 13+ (API 33), multi-user profiles, and special AppOps permissions.
 */
public object PackageQueries {

    private var getPackageInfoAsUserMethod: Method? = null

    // =========================================================================
    // 1. Multi-User & UID Utilities
    // =========================================================================

    /**
     * Extracts multi-user / work profile user ID from [uid].
     */
    @JvmStatic
    public fun getUserId(uid: Int): Int = if (uid >= 0) uid / 100000 else 0

    /**
     * Extracts base application ID from [uid] (independent of work profile user ID).
     */
    @JvmStatic
    public fun getAppId(uid: Int): Int = if (uid >= 0) uid % 100000 else uid

    /**
     * Safely retrieves [PackageInfo] supporting work profiles and secondary users.
     */
    @JvmStatic
    @JvmOverloads
    public fun getPackageInfoAsUser(
        pm: PackageManager,
        packageName: String,
        flags: Int = 0,
        userId: Int = 0,
    ): PackageInfo? {
        return runCatching {
            requirePackageInfoAsUser(pm, packageName, flags, userId)
        }.getOrNull()
    }

    /**
     * Strict query for [PackageInfo] across user profiles; throws exception on failure.
     */
    public fun requirePackageInfoAsUser(
        pm: PackageManager,
        packageName: String,
        flags: Int,
        userId: Int,
    ): PackageInfo {
        if (userId == getUserId(Process.myUid())) {
            return pm.getPackageInfoCompat(packageName, flags)
        }
        val method = synchronized(this) {
            getPackageInfoAsUserMethod ?: PackageManager::class.java.getDeclaredMethod(
                "getPackageInfoAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).also { getPackageInfoAsUserMethod = it }
        }
        return try {
            checkNotNull(method.invoke(pm, packageName, flags, userId) as? PackageInfo)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw (e.targetException ?: e)
        }
    }

    // =========================================================================
    // 2. Installation & Version Queries
    // =========================================================================

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
            PackageInfoCompat.getLongVersionCode(packageInfo)
        }.getOrNull()
    }

    public fun isAppInstalledVersionOld(context: Context, packageName: String, versionCode: Long): Boolean {
        return getInstalledAppVersionCode(context, packageName)?.let { it < versionCode } ?: false
    }

    // =========================================================================
    // 3. Enabled State Checking (Accurate System Setting Resolution)
    // =========================================================================

    public fun isAppEnabled(activity: Activity, packageName: String): Boolean {
        return isAppEnabled(activity as Context, packageName)
    }

    public fun isAppEnabled(context: Context, packageName: String): Boolean {
        return runCatching {
            val pm = context.packageManager
            val setting = pm.getApplicationEnabledSetting(packageName)
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                val ai = pm.getApplicationInfoCompat(packageName, 0)
                ai.enabled
            } else {
                setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
        }.getOrDefault(false)
    }

    public fun isAppEnabled(context: Context, info: PackageInfo): Boolean {
        return runCatching {
            val pm = context.packageManager
            val setting = pm.getApplicationEnabledSetting(info.packageName)
            if (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                info.applicationInfo?.enabled ?: false
            } else {
                setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
        }.getOrDefault(false)
    }

    // =========================================================================
    // 4. Permissions & UID Resolution
    // =========================================================================

    public fun hasInternetPermission(context: Context, packageName: String): Boolean {
        return context.packageManager.checkPermission(
            Manifest.permission.INTERNET,
            packageName,
        ) == PackageManager.PERMISSION_GRANTED
    }

    public fun hasInternetPermission(context: Context, uid: Int): Boolean {
        val pkgs = context.packageManager.getPackagesForUid(uid) ?: return false
        return pkgs.any { hasInternetPermission(context, it) }
    }

    public fun getPackagesForUid(context: Context, uid: Int): List<String> {
        return runCatching {
            context.packageManager.getPackagesForUid(uid)?.sorted().orEmpty()
        }.getOrDefault(emptyList())
    }

    public fun getPrimaryPackageName(context: Context, uid: Int): String? {
        return when (uid) {
            0 -> "root"
            1000 -> "android"
            1001 -> "phone"
            1013 -> "mediaserver"
            1020 -> "multicastdnsresponder"
            1021 -> "gps"
            1051 -> "netd"
            9999 -> "nobody"
            else -> getPackagesForUid(context, uid).firstOrNull()
        }
    }

    public fun getFirstInstallTime(context: Context, packageName: String): Long {
        return runCatching {
            context.packageManager.getPackageInfoCompat(packageName, 0).firstInstallTime
        }.getOrDefault(0L)
    }

    public fun getLastUpdateTime(context: Context, packageName: String): Long {
        return runCatching {
            context.packageManager.getPackageInfoCompat(packageName, 0).lastUpdateTime
        }.getOrDefault(0L)
    }

    public fun getApplicationLabel(context: Context, packageName: String): String {
        return runCatching {
            val pm = context.packageManager
            val ai = pm.getApplicationInfoCompat(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
    }

    public fun isValidUid(uid: Int): Boolean = uid > -1 && uid != 1051

    public fun getApkSizeBytes(context: Context, packageName: String): Long {
        return runCatching {
            val ai = context.packageManager.getApplicationInfoCompat(packageName, 0)
            val baseFile = ai.sourceDir?.let { java.io.File(it) }
            val splits = ai.splitSourceDirs?.map { java.io.File(it) }?.filter { it.exists() && it.canRead() }.orEmpty()
            (baseFile?.takeIf { it.exists() }?.length() ?: 0L) + splits.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    public fun getSigningCertificateFingerprint(
        context: Context,
        packageName: String,
        algorithm: String = "SHA-256",
    ): String? {
        return runCatching {
            val pm = context.packageManager
            val certBytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES
                val info = pm.getPackageInfoCompat(packageName, flags)
                val signingInfo = info.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners?.firstOrNull()?.toByteArray()
                    } else {
                        signingInfo.signingCertificateHistory?.firstOrNull()?.toByteArray()
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                val flags = PackageManager.GET_SIGNATURES
                val info = pm.getPackageInfoCompat(packageName, flags)
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            }
            if (certBytes != null) {
                val md = java.security.MessageDigest.getInstance(algorithm)
                val digest = md.digest(certBytes)
                digest.joinToString(":") { "%02X".format(it) }
            } else null
        }.getOrNull()
    }

    @SuppressLint("QueryPermissionsNeeded")
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

    // =========================================================================
    // 5. Model Conversion & Snapshot
    // =========================================================================

    public fun getInstalledApp(context: Context, packageName: String): InstalledApp? {
        return runCatching {
            val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or PackageManager.GET_CONFIGURATIONS
            val packageInfo = context.packageManager.getPackageInfoCompat(packageName, flags)
            toInstalledApp(context, packageInfo)
        }.getOrNull()
    }

    public fun toInstalledApp(
        context: Context,
        packageInfo: PackageInfo,
        userId: Int = getUserId(Process.myUid()),
    ): InstalledApp {
        return toInstalledApp(context.packageManager, packageInfo, context, userId)
    }

    internal fun toInstalledApp(
        packageManager: PackageManager,
        packageInfo: PackageInfo,
        context: Context? = null,
        userId: Int = getUserId(Process.myUid()),
    ): InstalledApp {
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

        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        val uid = applicationInfo.uid
        val actualUserId = if (uid >= 0) getUserId(uid) else userId

        val isSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (applicationInfo.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        } else false

        val isEnabled = if (context != null) {
            isAppEnabled(context, packageInfo)
        } else {
            applicationInfo.enabled
        }

        val isArchived = if (Build.VERSION.SDK_INT >= 35) {
            runCatching {
                val method = ApplicationInfo::class.java.getMethod("isArchived")
                method.invoke(applicationInfo) as? Boolean
            }.getOrNull() ?: false
        } else false

        val isLaunchable = runCatching {
            packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null
        }.getOrDefault(false)

        val permissions = packageInfo.requestedPermissions.orEmpty().mapIndexed { index, name ->
            val permFlags = packageInfo.requestedPermissionsFlags?.getOrNull(index) ?: 0
            val protection = runCatching {
                packageManager.getPermissionInfo(name, 0).protectionLevel
            }.getOrNull()
            val isGranted = effectiveGrant(context, packageInfo.packageName, uid, name, permFlags)
            InstalledAppPermission(name, isGranted, protection)
        }

        val hasInternet = permissions.any { it.name == Manifest.permission.INTERNET && it.isGranted }

        val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applicationInfo.category
        } else null

        val requestedFeatures = packageInfo.reqFeatures?.mapNotNull { it.name }.orEmpty()

        return InstalledApp(
            packageName = packageInfo.packageName,
            activityName = null,
            appName = appName,
            versionName = packageInfo.versionName,
            versionCode = versionCode,
            installerPackageName = resolveInstallerPackageName(packageManager, packageInfo.packageName),
            appType = appType,
            isEnabled = isEnabled,
            isSuspended = isSuspended,
            requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty(),
            installTimeMillis = packageInfo.firstInstallTime,
            updateTimeMillis = packageInfo.lastUpdateTime,
            uid = uid,
            userId = actualUserId,
            hasInternet = hasInternet,
            targetSdk = applicationInfo.targetSdkVersion,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) applicationInfo.minSdkVersion else null,
            processName = applicationInfo.processName,
            category = category,
            isArchived = isArchived,
            isLaunchable = isLaunchable,
            iconResource = applicationInfo.icon,
            sourceDir = applicationInfo.sourceDir,
            publicSourceDir = applicationInfo.publicSourceDir,
            dataDir = applicationInfo.dataDir,
            nativeLibraryDir = applicationInfo.nativeLibraryDir,
            splitSourceDirs = applicationInfo.splitSourceDirs?.toList().orEmpty(),
            requestedFeatures = requestedFeatures,
            permissions = permissions,
        )
    }

    private fun effectiveGrant(
        context: Context?,
        packageName: String,
        uid: Int,
        name: String,
        flags: Int,
    ): Boolean {
        val standard = (flags and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        if (context == null || uid < 0) return standard

        val operation = when (name) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW
            Manifest.permission.PACKAGE_USAGE_STATS -> AppOpsManager.OPSTR_GET_USAGE_STATS
            Manifest.permission.WRITE_SETTINGS -> AppOpsManager.OPSTR_WRITE_SETTINGS
            "android.permission.MANAGE_EXTERNAL_STORAGE" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "android:manage_external_storage" else null
            "android.permission.SCHEDULE_EXACT_ALARM" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "android:schedule_exact_alarm" else null
            else -> null
        } ?: return standard

        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return standard
        return runCatching {
            val mode = appOps.checkOpNoThrow(operation, uid, packageName)
            if (mode == AppOpsManager.MODE_DEFAULT) standard else mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(standard)
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
internal fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }

@Suppress("DEPRECATION")
internal fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int): ApplicationInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
    } else {
        getApplicationInfo(packageName, flags)
    }
