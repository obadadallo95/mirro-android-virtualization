package app.mirro.android.domain.engine

import app.mirro.android.domain.model.CloneEngineType

/**
 * Registry holding available cloning engines for Mirro.
 */
class EngineRegistry(
    private val defaultEngine: CloneEngine
) {
    fun getEngine(type: CloneEngineType = CloneEngineType.VIRTUALIZED_CONTAINER): CloneEngine {
        return defaultEngine
    }

    fun getDefaultEngine(): CloneEngine = defaultEngine

    fun getAllEngines(): List<CloneEngine> = listOf(defaultEngine)
}

