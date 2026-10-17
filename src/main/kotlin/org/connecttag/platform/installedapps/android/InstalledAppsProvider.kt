package org.connecttag.platform.installedapps.android

/**
 * Lists installed applications and resolves one application by package name.
 */
public interface InstalledAppsProvider {
    /**
     * Returns installed applications that satisfy [filter].
     */
    public suspend fun getInstalledApps(filter: AppFilter = AppFilter.Default): List<InstalledApp>

    /**
     * Returns one installed application, or `null` if the package cannot be resolved.
     */
    public suspend fun getInstalledApp(packageName: String): InstalledApp?

    /**
     * Returns applications that have a launcher activity.
     */
    public suspend fun getLauncherApps(filter: AppFilter = AppFilter.Default): List<InstalledApp>

    /**
     * Checks if the application is the default for a specific category (Launcher, Browser, etc).
     */
    public fun isDefaultApp(packageName: String, category: DefaultAppCategory): Boolean
}

public enum class DefaultAppCategory {
    LAUNCHER, BROWSER, DIALER, SMS, ASSISTANT
}


