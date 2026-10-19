package org.connecttag.lib.platform.installedapps.android

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * Parsed X.509 certificate cryptographic details for an application.
 */
public data class ApkCertificateDetails(
    val subject: String,
    val issuer: String,
    val validFrom: Long,
    val validUntil: Long,
    val serialNumber: String
)

/**
 * Inspection details for base and split APKs.
 */
public data class SplitApkDetails(
    val isSplit: Boolean,
    val baseApkFile: File,
    val splitApkFiles: List<File>,
    val totalSizeBytes: Long,
    val splitNames: List<String>
)

/**
 * Comprehensive forensic security report for an installed application package.
 */
public data class AppPackageForensicsReport(
    val packageName: String,
    val appName: String,
    val signatures: List<String>,
    val certificate: ApkCertificateDetails?,
    val apkHashSha256: String?,
    val apkSizeBytes: Long,
    val splitApks: SplitApkDetails?,
    val nativeLibraries: List<String>,
    val paths: Map<String, String>
)

/**
 * Single Source of Truth for Android APK forensics, X.509 certificate parsing,
 * APK SHA-256 fingerprinting, split APK inspection, and native library detection.
 */
public object AppPackageForensics {

    /**
     * Extracts raw signature byte arrays from [PackageInfo] across Android API levels.
     */
    public fun extractRawSignatures(packageInfo: PackageInfo): List<ByteArray> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
                ?: packageInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures?.map { it.toByteArray() } ?: emptyList()
    }

    /**
     * Formats raw signature bytes as colon-delimited uppercase SHA-256 hex string.
     */
    public fun formatFingerprintSha256(signature: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature)
        return digest.joinToString(":") { "%02X".format(Locale.US, it) }
    }

    /**
     * Parses X.509 certificate attributes from signature byte array.
     */
    public fun parseX509Certificate(signature: ByteArray): ApkCertificateDetails? {
        return try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val cert = ByteArrayInputStream(signature).use {
                certFactory.generateCertificate(it) as? X509Certificate
            } ?: return null

            ApkCertificateDetails(
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                validFrom = cert.notBefore.time,
                validUntil = cert.notAfter.time,
                serialNumber = cert.serialNumber.toString(16).uppercase(Locale.US)
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Calculates the SHA-256 hash of a file on disk (buffered streaming).
     */
    public fun calculateFileSha256(file: File): String? {
        if (!file.isFile || !file.canRead()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Inspects signatures for a given package name asynchronously.
     */
    public suspend fun getSignatures(context: Context, packageName: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val pInfo = pm.getPackageInfo(packageName, flags)
            extractRawSignatures(pInfo).map(::formatFingerprintSha256)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Retrieves primary certificate details for a package asynchronously.
     */
    public suspend fun getCertificateDetails(context: Context, packageName: String): ApkCertificateDetails? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val pInfo = pm.getPackageInfo(packageName, flags)
            val raw = extractRawSignatures(pInfo).firstOrNull() ?: return@withContext null
            parseX509Certificate(raw)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Computes the APK file SHA-256 hash asynchronously.
     */
    public suspend fun getApkFileHash(context: Context, packageName: String): String? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val file = File(appInfo.sourceDir)
            calculateFileSha256(file)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Inspects native libraries bundled or extracted for the application.
     */
    public fun extractNativeLibraries(appInfo: ApplicationInfo?, nativeLibraryDir: String?): List<String> {
        nativeLibraryDir?.let(::File)?.takeIf { it.isDirectory }?.list()?.let { return it.toList() }
        if (appInfo == null) return emptyList()
        return listOf("primaryCpuAbi", "secondaryCpuAbi").mapNotNull { field ->
            try {
                ApplicationInfo::class.java.getField(field).get(appInfo) as? String
            } catch (_: ReflectiveOperationException) {
                null
            }
        }.distinct()
    }

    /**
     * Performs complete forensic inspection on an installed application.
     */
    public suspend fun inspect(
        context: Context,
        packageName: String
    ): AppPackageForensicsReport? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val signatureFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                signatureFlags

            val pInfo = pm.getPackageInfo(packageName, flags)
            val appInfo = pInfo.applicationInfo ?: return@withContext null
            val appLabel = pm.getApplicationLabel(appInfo).toString().ifBlank { packageName }

            val baseFile = appInfo.sourceDir?.let(::File)
            val splitFiles = (appInfo.splitSourceDirs ?: emptyArray()).map(::File).filter { it.exists() && it.canRead() }

            val paths = buildMap {
                appInfo.sourceDir?.let { put("sourceDir", it) }
                appInfo.dataDir?.let { put("dataDir", it) }
                appInfo.nativeLibraryDir?.let { put("nativeLibraryDir", it) }
                appInfo.publicSourceDir?.let { put("publicSourceDir", it) }
                appInfo.splitSourceDirs?.forEachIndexed { index, path ->
                    put("splitSourceDir[$index]", path)
                }
            }

            val rawSignatures = extractRawSignatures(pInfo)
            val formattedSignatures = rawSignatures.map(::formatFingerprintSha256)
            val certDetails = rawSignatures.firstOrNull()?.let(::parseX509Certificate)
            val apkHash = baseFile?.takeIf { it.isFile && it.canRead() }?.let(::calculateFileSha256)

            val splitDetails = baseFile?.let { base ->
                SplitApkDetails(
                    isSplit = splitFiles.isNotEmpty(),
                    baseApkFile = base,
                    splitApkFiles = splitFiles,
                    totalSizeBytes = base.length() + splitFiles.sumOf(File::length),
                    splitNames = splitFiles.map { it.name }
                )
            }

            AppPackageForensicsReport(
                packageName = packageName,
                appName = appLabel,
                signatures = formattedSignatures,
                certificate = certDetails,
                apkHashSha256 = apkHash,
                apkSizeBytes = baseFile?.length() ?: 0L,
                splitApks = splitDetails,
                nativeLibraries = extractNativeLibraries(appInfo, appInfo.nativeLibraryDir),
                paths = paths
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Inspects an [InstalledApp] model directly.
     */
    public suspend fun inspectInstalledApp(
        context: Context,
        app: InstalledApp
    ): AppPackageForensicsReport? = inspect(context, app.packageName)
}
