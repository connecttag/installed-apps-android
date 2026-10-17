package org.connecttag.platform.installedapps.android

/**
 * Pure Kotlin search matcher with Arabic normalization and multi-field matching support.
 *
 * Single Source of Truth for application query filtering across ViewModels, lists, and search queries.
 * Handles tashkeel removal, alef variant harmonization, taa marbuta/alef maksura normalization,
 * and optional numeric UID query matching.
 */
public object AppSearchMatcher {

    private val TASHKEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
    private val ALEF_VARIANTS_REGEX = Regex("[إأآا]")

    /**
     * Normalizes Arabic text by removing tashkeel and harmonizing letter variants.
     */
    public fun normalizeArabic(text: String): String {
        return text.lowercase()
            .replace(TASHKEEL_REGEX, "")
            .replace(ALEF_VARIANTS_REGEX, "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace("ؤ", "و")
            .replace("ئ", "ي")
    }

    /**
     * Checks if [name], [packageName], or optional [uid] matches [query] with Arabic-aware normalization.
     */
    public fun matches(name: String?, packageName: String?, uid: Int? = null, query: String): Boolean {
        if (query.isBlank()) return true
        val cleanQuery = query.trim()
        val normQuery = normalizeArabic(cleanQuery)
        val lowerQuery = cleanQuery.lowercase()

        if (!name.isNullOrBlank()) {
            val normName = normalizeArabic(name)
            if (normName.contains(normQuery) || name.contains(cleanQuery, ignoreCase = true)) {
                return true
            }
        }

        if (!packageName.isNullOrBlank()) {
            if (packageName.lowercase().contains(lowerQuery)) {
                return true
            }
        }

        if (uid != null && uid >= 0 && uid.toString().contains(cleanQuery)) {
            return true
        }

        return false
    }

    /**
     * Checks if any given name or package in collections matches [query].
     */
    public fun matchesAny(names: Iterable<String>?, packages: Iterable<String>?, query: String): Boolean {
        if (query.isBlank()) return true
        names?.forEach { if (matches(it, null, null, query)) return true }
        packages?.forEach { if (matches(null, it, null, query)) return true }
        return false
    }
}
