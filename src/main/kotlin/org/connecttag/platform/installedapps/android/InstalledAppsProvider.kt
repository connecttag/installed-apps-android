package org.connecttag.platform.installedapps.android

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Represents package change lifecycle events on Android devices.
 */
public sealed interface PackageChangeEvent {
    public val packageName: String
    public val userId: Int

    public data class Added(
        override val packageName: String,
        override val userId: Int,
        val isReplacing: Boolean = false,
    ) : PackageChangeEvent

    public data class Removed(
        override val packageName: String,
        override val userId: Int,
        val isReplacing: Boolean = false,
    ) : PackageChangeEvent

    public data class Changed(
        override val packageName: String,
        override val userId: Int,
    ) : PackageChangeEvent

    public data class Replaced(
        override val packageName: String,
        override val userId: Int,
    ) : PackageChangeEvent
}

/**
 * Lists installed applications, resolves application records, and observes live package updates.
 */
public interface InstalledAppsProvider {
    /**
     * Returns installed applications that satisfy [filter].
     */
    public suspend fun getInstalledApps(filter: AppFilter = AppFilter.Default): List<InstalledApp>

    /**
     * Returns one installed application for current user, or `null` if the package cannot be resolved.
     */
    public suspend fun getInstalledApp(packageName: String): InstalledApp?

    /**
     * Returns one installed application for a specific [userId] profile (e.g. Work Profile).
     */
    public suspend fun getInstalledApp(packageName: String, userId: Int): InstalledApp? = getInstalledApp(packageName)

    /**
     * Returns applications associated with a specific Linux [uid] (supports shared UIDs).
     */
    public suspend fun getAppsForUid(uid: Int): List<InstalledApp> = emptyList()

    /**
     * Returns applications that have a launcher activity.
     */
    public suspend fun getLauncherApps(filter: AppFilter = AppFilter.Default): List<InstalledApp>

    /**
     * Checks if the application is the default for a specific category (Launcher, Browser, etc).
     */
    public fun isDefaultApp(packageName: String, category: DefaultAppCategory): Boolean

    /**
     * Emits events when applications are added, removed, updated, or changed on the device.
     */
    public fun observePackageChanges(): Flow<PackageChangeEvent> = emptyFlow()

    /**
     * Emits a reactive stream of installed applications that satisfy [filter], updating whenever packages change.
     */
    public fun observeInstalledApps(filter: AppFilter = AppFilter.Default): Flow<List<InstalledApp>> = emptyFlow()
}

public enum class DefaultAppCategory {
    LAUNCHER, BROWSER, DIALER, SMS, ASSISTANT
}
