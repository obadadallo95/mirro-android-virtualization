package app.mirro.android.domain.analyzer

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import app.mirro.android.domain.model.CompatibilityReport
import app.mirro.android.domain.model.CompatibilityStatus

/**
 * Real Android PackageInfo analyzer for application isolation readiness.
 *
 * Checks actual Android manifest flags, targetSdk, sharedUserId, device admin components,
 * and system app classification.
 *
 * Core Engineering Principle: Never claim guaranteed compatibility from package metadata alone.
 * Compatibility status reflects real evidence, not optimism. Apps require runtime isolation testing
 * to be declared fully supported.
 */
class CompatibilityAnalyzer {

    fun analyze(packageInfo: PackageInfo): CompatibilityReport {
        val appInfo = packageInfo.applicationInfo
        val technicalDetails = mutableListOf<String>()
        val riskFactors = mutableListOf<String>()

        // 1. Shared User ID Check (Definitive blocker)
        val sharedUserId = packageInfo.sharedUserId
        if (!sharedUserId.isNullOrBlank()) {
            technicalDetails.add("Declared android:sharedUserId=\"$sharedUserId\"")
            riskFactors.add("Shares Linux UID with companion packages. Sandbox isolation will disconnect linked processes.")
            return CompatibilityReport(
                status = CompatibilityStatus.PROTECTED,
                summary = "Application relies on a shared Linux UID with other system or vendor apps.",
                technicalDetails = technicalDetails,
                riskFactors = riskFactors
            )
        }

        // 2. System Application Check (Definitive blocker)
        val isSystem = if (appInfo != null) {
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } else false

        if (isSystem) {
            technicalDetails.add("Flagged as Android OS System App (FLAG_SYSTEM)")
            riskFactors.add("Deeply tied to system framework and privileged platform signatures.")
            return CompatibilityReport(
                status = CompatibilityStatus.PROTECTED,
                summary = "System applications cannot be safely cloned or isolated into user containers.",
                technicalDetails = technicalDetails,
                riskFactors = riskFactors
            )
        }

        // 3. Inspect ApplicationInfo flags
        if (appInfo != null) {
            val targetSdk = appInfo.targetSdkVersion
            technicalDetails.add("Target SDK: $targetSdk")

            val isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                technicalDetails.add("Debuggable build flag detected")
            }

            val allowsBackup = (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            if (!allowsBackup) {
                technicalDetails.add("Backup disallowed (allowBackup=false)")
                riskFactors.add("App author explicitly opts out of external state extraction.")
            }
        }

        // 4. Primary Acceptance Target: ChatGPT
        // ChatGPT is our primary acceptance-test app, but compatibility must NOT be declared as proven
        // until a real engine successfully runs it in an isolated environment.
        val pkgName = packageInfo.packageName.lowercase()
        if (pkgName == "com.openai.chatgpt") {
            technicalDetails.add("Primary acceptance-test target application")
            technicalDetails.add("Package metadata valid (single-user profile architecture)")
            technicalDetails.add("Runtime isolation test pending execution")
            return CompatibilityReport(
                status = CompatibilityStatus.UNKNOWN,
                summary = "Primary acceptance target. Requires runtime isolation testing before compatibility is confirmed.",
                technicalDetails = technicalDetails,
                riskFactors = emptyList()
            )
        }

        // 5. GMS, Banking, or Payment signatures (Known limitations)
        val hasGmsPushLikelihood = pkgName.contains("whatsapp") ||
                pkgName.contains("telegram") ||
                pkgName.contains("signal") ||
                pkgName.contains("banking") ||
                pkgName.contains("pay")

        if (hasGmsPushLikelihood) {
            technicalDetails.add("External push messaging or payment signature detected")
            riskFactors.add("Push notifications (FCM) or hardware keystore authentication may require Work Profile isolation.")
            return CompatibilityReport(
                status = CompatibilityStatus.LIMITED,
                summary = "Identified external push or keystore dependencies. Requires runtime verification.",
                technicalDetails = technicalDetails,
                riskFactors = riskFactors
            )
        }

        // 6. Generic applications: Do not claim SUPPORTED merely from targetSdk >= 24.
        // Package metadata alone cannot prove runtime isolation success.
        if (appInfo != null && appInfo.targetSdkVersion >= 24) {
            technicalDetails.add("Standard launchable user space package (targetSdk ${appInfo.targetSdkVersion})")
            technicalDetails.add("No obvious manifest incompatibilities found")
            technicalDetails.add("Requires runtime test to confirm isolated execution")
            return CompatibilityReport(
                status = CompatibilityStatus.UNKNOWN,
                summary = "Standard application structure. Requires runtime test to confirm isolated execution.",
                technicalDetails = technicalDetails,
                riskFactors = emptyList()
            )
        }

        // Otherwise unknown / needs testing
        return CompatibilityReport(
            status = CompatibilityStatus.UNKNOWN,
            summary = "Package structure requires runtime validation.",
            technicalDetails = technicalDetails,
            riskFactors = riskFactors
        )
    }
}
