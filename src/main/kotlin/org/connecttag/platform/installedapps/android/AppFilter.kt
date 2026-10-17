package org.connecttag.platform.installedapps.android

/**
 * Public filter contract for installed-application queries.
 */
public data class AppFilter(
    val packageNames: Set<String> = emptySet(),
    val includeSystemApps: Boolean = true,
    val includeUserApps: Boolean = true,
    val includeDisabledApps: Boolean = false,
    val includeArchivedApps: Boolean = true,
    val onlyLaunchable: Boolean = false,
    val onlyWithInternet: Boolean = false,
    val categories: Set<Int> = emptySet(),
    val userIds: Set<Int> = emptySet(),
    val searchQuery: String? = null,
) {
    public fun matches(app: InstalledApp): Boolean {
        if (packageNames.isNotEmpty() && app.packageName !in packageNames) return false
        if (userIds.isNotEmpty() && app.userId !in userIds) return false
        if (!includeDisabledApps && !app.isEnabled) return false
        if (!includeSystemApps && app.appType == AppType.SYSTEM) return false
        if (!includeUserApps && app.appType == AppType.USER) return false
        if (!includeArchivedApps && app.isArchived) return false
        if (onlyLaunchable && !app.isLaunchable) return false
        if (onlyWithInternet && !app.hasInternet) return false
        if (categories.isNotEmpty() && (app.category == null || app.category !in categories)) return false

        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return AppSearchMatcher.matches(
            name = app.appName,
            packageName = app.packageName,
            uid = app.uid.takeIf { it >= 0 },
            query = query
        )
    }

    public companion object {
        public val Default: AppFilter = AppFilter()
    }
}
