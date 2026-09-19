package com.example

import com.example.data.local.entity.CloneInstanceEntity
import com.example.domain.model.CloneEngineType
import com.example.domain.model.CloneInstance
import com.example.domain.model.StorageMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CloneModelTest {

    @Test
    fun `clone instance domain and entity bidirectional mapping`() {
        val domain = CloneInstance(
            id = "test-uuid-1",
            originalPackageName = "com.openai.chatgpt",
            originalAppLabel = "ChatGPT",
            customName = "ChatGPT Work",
            badgeColorHex = "#10B981",
            badgeSymbol = "W",
            engineType = CloneEngineType.BLUEPRINT_STAGING,
            isFrozen = false,
            isLocked = true,
            storageSizeBytes = 1024 * 1024 * 15L,
            createdAt = 1700000000000L,
            lastLaunchedAt = 1700000500000L
        )

        val entity = CloneInstanceEntity.fromDomainModel(domain)
        assertEquals("test-uuid-1", entity.id)
        assertEquals("com.openai.chatgpt", entity.originalPackageName)
        assertEquals("ChatGPT Work", entity.customName)
        assertEquals("BLUEPRINT_STAGING", entity.engineType)

        val restoredDomain = entity.toDomainModel()
        assertEquals(domain.id, restoredDomain.id)
        assertEquals(domain.originalPackageName, restoredDomain.originalPackageName)
        assertEquals(domain.customName, restoredDomain.customName)
        assertEquals(domain.badgeColorHex, restoredDomain.badgeColorHex)
        assertEquals(domain.badgeSymbol, restoredDomain.badgeSymbol)
        assertEquals(domain.engineType, restoredDomain.engineType)
        assertFalse(restoredDomain.isFrozen)
        assertEquals(domain.isLocked, restoredDomain.isLocked)
    }

    @Test
    fun `storage metrics formatting formats bytes accurately`() {
        assertEquals("0 B", StorageMetrics.formatBytes(0L))
        assertEquals("512 B", StorageMetrics.formatBytes(512L))
        assertEquals("1.0 KB", StorageMetrics.formatBytes(1024L))
        assertEquals("2.5 MB", StorageMetrics.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.0 GB", StorageMetrics.formatBytes(1024L * 1024 * 1024))
    }
}
