package org.connecttag.platform.installedapps.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-backed implementation of [InstalledAppsProvider].
 */
public class AndroidInstalledAppsProvider(
    private val context: Context,
    private val packageManager: PackageManager = context.packageManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InstalledAppsProvider {

    public override suspend fun getInstalledApps(filter: AppFilter): List<InstalledApp> = withContext(dispatcher) {
        getInstalledPackages()
            .asSequence()
            .map(::toInstalledApp)
            .filter(filter::matches)
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    public override suspend fun getInstalledApp(packageName: String): InstalledApp? = withContext(dispatcher) {
        runCatching { getPackageInfo(packageName) }
            .getOrNull()
            ?.let(::toInstalledApp)
    }

    public override suspend fun getLauncherApps(filter: AppFilter): List<InstalledApp> = withContext(dispatcher) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }

        resolveInfos.mapNotNull { info -> toInstalledAppFromResolveInfo(info) }
            .filter(filter::matches)
            .sortedBy { it.appName.lowercase() }
    }

    public override fun isDefaultApp(packageName: String, category: DefaultAppCategory): Boolean {
        val defaultPackage = when (category) {
            DefaultAppCategory.LAUNCHER -> resolveDefaultLauncherPackage()
            DefaultAppCategory.BROWSER -> resolveDefaultBrowserPackage()
            DefaultAppCategory.DIALER -> (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
            DefaultAppCategory.SMS -> Telephony.Sms.getDefaultSmsPackage(context)
            DefaultAppCategory.ASSISTANT -> resolveDefaultAssistantPackage()
        }
        return defaultPackage?.equals(packageName, ignoreCase = true) ?: false
    }

    private fun resolveDefaultLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun resolveDefaultBrowserPackage(): String? {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolveInfo = packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun resolveDefaultAssistantPackage(): String? {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).addCategory(Intent.CATEGORY_DEFAULT)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackages(): List<PackageInfo> {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getInstalledPackages(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfo(packageName: String): PackageInfo {
        val flags = PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    private fun toInstalledAppFromResolveInfo(resolveInfo: ResolveInfo): InstalledApp? {
        val activityInfo = resolveInfo.activityInfo ?: return null
        val packageName = activityInfo.packageName

        return runCatching {
            val packageInfo = getPackageInfo(packageName)
            toInstalledApp(packageInfo).copy(activityName = activityInfo.name)
        }.getOrNull()
    }

    private fun toInstalledApp(packageInfo: PackageInfo): InstalledApp {
        return PackageQueries.toInstalledApp(packageManager, packageInfo)
    }
}
