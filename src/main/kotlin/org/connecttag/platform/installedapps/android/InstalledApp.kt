package org.connecttag.platform.installedapps.android

/**
 * Detailed permission observation for an installed application.
 */
public data class InstalledAppPermission(
    val name: String,
    val isGranted: Boolean,
    val protectionLevel: Int? = null,
)

/**
 * Stable application model exposed by the public API.
 *
 * Android framework objects are intentionally excluded so callers can depend on
 * this model without leaking `PackageInfo` or `ApplicationInfo` through their own APIs.
 */
public data class InstalledApp(
    val packageName: String,
    val activityName: String? = null,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val installerPackageName: String?,
    val appType: AppType,
    val isEnabled: Boolean,
    val isHidden: Boolean = false,
    val isSuspended: Boolean = false,
    val isDefault: Boolean = false,
    val requestedPermissions: List<String> = emptyList(),
    val installTimeMillis: Long = 0L,
    val updateTimeMillis: Long = 0L,
    val uid: Int = -1,
    val userId: Int = if (uid >= 0) uid / 100000 else 0,
    val hasInternet: Boolean = false,
    val targetSdk: Int? = null,
    val minSdk: Int? = null,
    val processName: String? = null,
    val category: Int? = null,
    val isArchived: Boolean = false,
    val isLaunchable: Boolean = false,
    val iconResource: Int = 0,
    val sourceDir: String? = null,
    val publicSourceDir: String? = null,
    val dataDir: String? = null,
    val nativeLibraryDir: String? = null,
    val splitSourceDirs: List<String> = emptyList(),
    val requestedFeatures: List<String> = emptyList(),
    val permissions: List<InstalledAppPermission> = emptyList(),
) {
    /**
     * Convenient URI format for loading application icon directly: `android.resource://[packageName]/[iconResource]`.
     */
    val iconUri: String? get() = iconResource.takeIf { it != 0 }?.let { "android.resource://$packageName/$it" }

    /**
     * Unique stable key combining package name and multi-user profile ID.
     */
    val appKey: String get() = if (userId > 0) "$packageName#$userId" else packageName

    /**
     * Fast fingerprint/revision hash to detect changes in mutable state (permissions, enabled, updates).
     */
    val revision: String get() = buildString {
        append(uid).append(':').append(versionCode).append(':').append(updateTimeMillis)
        append(':').append(installTimeMillis).append(':').append(isEnabled).append(':').append(isSuspended)
        append(':').append(isArchived).append(':').append(installerPackageName).append(':').append(appName)
        append(':').append(sourceDir)
        permissions.sortedBy { it.name }.forEach {
            append('|').append(it.name).append(':').append(it.isGranted).append(':').append(it.protectionLevel)
        }
    }
}
