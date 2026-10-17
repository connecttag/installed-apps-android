package org.connecttag.platform.installedapps.android

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
    val requestedPermissions: List<String>,
    val installTimeMillis: Long,
    val updateTimeMillis: Long,
)


