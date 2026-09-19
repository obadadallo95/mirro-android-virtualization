package app.mirro.android

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.model.CompatibilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAnalyzerTest {

    private val analyzer = CompatibilityAnalyzer()

    @Test
    fun `chatgpt package evaluates as UNKNOWN pending runtime test`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.openai.chatgpt"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        // Primary acceptance-test app must not claim SUPPORTED without real runtime testing
        assertEquals(CompatibilityStatus.UNKNOWN, report.status)
        assertTrue(report.technicalDetails.any { it.contains("Primary acceptance-test target") })
        assertTrue(report.technicalDetails.any { it.contains("Runtime isolation test pending") })
    }

    @Test
    fun `system app evaluates as PROTECTED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.android.settings"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_SYSTEM
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.PROTECTED, report.status)
        assertTrue(report.riskFactors.isNotEmpty())
    }

    @Test
    fun `app with sharedUserId evaluates as PROTECTED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.vendor.service"
            sharedUserId = "android.uid.system"
            applicationInfo = ApplicationInfo().apply {
                flags = 0
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.PROTECTED, report.status)
        assertTrue(report.technicalDetails.any { it.contains("android:sharedUserId") })
    }

    @Test
    fun `messaging app with GMS push evaluates as LIMITED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.whatsapp"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.LIMITED, report.status)
    }

    @Test
    fun `standard user app evaluates as UNKNOWN pending runtime test`() {
        val packageInfo = PackageInfo().apply {
            packageName = "org.thoughtcrime.securesms"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.UNKNOWN, report.status)
        assertTrue(report.technicalDetails.any { it.contains("Requires runtime test") })
    }
}
