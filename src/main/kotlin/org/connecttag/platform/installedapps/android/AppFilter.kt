package org.connecttag.platform.installedapps.android

/**
 * Public filter contract for installed-application queries.
 */
public data class AppFilter(
    val packageNames: Set<String> = emptySet(),
    val includeSystemApps: Boolean = true,
    val includeUserApps: Boolean = true,
    val includeDisabledApps: Boolean = false,
    val searchQuery: String? = null,
) {
    public fun matches(app: InstalledApp): Boolean {
        if (packageNames.isNotEmpty() && app.packageName !in packageNames) return false
        if (!includeDisabledApps && !app.isEnabled) return false
        if (!includeSystemApps && app.appType == AppType.SYSTEM) return false
        if (!includeUserApps && app.appType == AppType.USER) return false

        val query = searchQuery?.trim()?.takeIf { it.isNotEmpty() } ?: return true
        return app.packageName.contains(query, ignoreCase = true) ||
            app.appName.contains(query, ignoreCase = true)
    }

    public companion object {
        public val Default: AppFilter = AppFilter()
    }
}


