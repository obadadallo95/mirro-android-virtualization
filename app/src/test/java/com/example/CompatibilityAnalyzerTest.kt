package com.example

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.example.domain.analyzer.CompatibilityAnalyzer
import com.example.domain.model.CompatibilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAnalyzerTest {

    private val analyzer = CompatibilityAnalyzer()

    @Test
    fun `chatgpt package evaluates as SUPPORTED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.openai.chatgpt"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.SUPPORTED, report.status)
        assertTrue(report.technicalDetails.any { it.contains("Independent HTTP/REST session") })
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
    fun `standard modern user app evaluates as SUPPORTED`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.notes"
            applicationInfo = ApplicationInfo().apply {
                flags = ApplicationInfo.FLAG_ALLOW_BACKUP
                targetSdkVersion = 34
            }
        }

        val report = analyzer.analyze(packageInfo)
        assertEquals(CompatibilityStatus.SUPPORTED, report.status)
    }
}
