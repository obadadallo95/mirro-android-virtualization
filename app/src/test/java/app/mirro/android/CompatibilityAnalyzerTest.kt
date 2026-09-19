package app.mirro.android

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CompatibilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAnalyzerTest {

    private val analyzer = CompatibilityAnalyzer()

    @Test
    fun `chatgpt package in container evaluates as SUPPORTED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.openai.chatgpt"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(
            packageInfo = packageInfo,
            engineType = CloneEngineType.VIRTUALIZED_CONTAINER
        )
        assertEquals(CompatibilityStatus.SUPPORTED, report.status)
        assertTrue(report.technicalDetails.any { it.contains("Primary acceptance-test target") })
    }

    @Test
    fun `chatgpt package in container with verified launch evaluates as CONTAINER_LAUNCH_VERIFIED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.openai.chatgpt"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(
            packageInfo = packageInfo,
            engineType = CloneEngineType.VIRTUALIZED_CONTAINER,
            isRuntimeLaunchVerified = true
        )
        assertEquals(CompatibilityStatus.CONTAINER_LAUNCH_VERIFIED, report.status)
        assertTrue(report.technicalDetails.any { it.contains("Container runtime initialized and executed successfully") })
        assertTrue(report.technicalDetails.any { it.contains("classloader policy") })
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
}
