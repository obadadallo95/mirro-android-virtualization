package com.example.domain.engine

import com.example.domain.model.CloneEngineType

/**
 * Registry holding available isolation engines.
 */
class EngineRegistry(
    private val engines: List<CloneEngine>
) {
    fun getEngine(type: CloneEngineType): CloneEngine {
        return engines.firstOrNull { it.engineType == type }
            ?: engines.first { it.engineType == CloneEngineType.BLUEPRINT_STAGING }
    }

    fun getAllEngines(): List<CloneEngine> = engines
}
