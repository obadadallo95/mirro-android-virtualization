package app.mirro.android.ui.container

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * Process-level callback coordinator for browser auth launched by a virtual target.
 * Only routing metadata is retained; OAuth secrets are never persisted or logged.
 */
internal class TargetAuthSessionRegistry(
    context: Context? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val SESSION_TIMEOUT_MS = 10 * 60 * 1000L
        private const val PREFS_NAME = "mirro_auth_routing"
        private const val RECORD_PREFIX = "session_"
        private const val SEPARATOR = "\u001f"
    }

    internal data class CallbackIdentity(
        val scheme: String?, val host: String?, val path: String?
    ) {
        fun matches(uri: Uri): Boolean =
            scheme == uri.scheme && host == uri.host && path == uri.path

        fun describe(): String =
            "scheme=${scheme ?: "<none>"}, host=${host ?: "<none>"}, path=${path ?: "<none>"}"
    }

    internal data class PendingSession(
        val cloneId: String,
        val targetActivityClassName: String,
        val requestCode: Int,
        val callback: CallbackIdentity?,
        val createdAt: Long
    )

    private val preferences = context?.applicationContext
        ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pendingRequests = mutableMapOf<String, PendingSession>()

    init {
        restorePersistedSessions()
        purgeExpired()
    }

    @Synchronized
    fun remember(
        cloneId: String,
        targetActivityClassName: String,
        requestCode: Int,
        intent: Intent
    ): PendingSession? {
        if (cloneId.isBlank() || targetActivityClassName.isBlank() || requestCode < 0) return null
        purgeExpired()
        val session = PendingSession(
            cloneId, targetActivityClassName, requestCode,
            extractCallbackIdentity(intent), clock()
        )
        pendingRequests[key(session)] = session
        persist(session)
        return session
    }

    /** Compatibility overload retained for focused unit tests and older callers. */
    @Synchronized
    fun remember(requestCode: Int, intent: Intent) {
        remember("test", "unknown", requestCode, intent)
    }

    @Synchronized
    fun forget(requestCode: Int, cloneId: String? = null) {
        pendingRequests.values.filter {
            it.requestCode == requestCode && (cloneId == null || it.cloneId == cloneId)
        }.forEach(::remove)
    }

    @Synchronized
    fun consumeActivityResult(cloneId: String, requestCode: Int, data: Intent?): PendingSession? {
        purgeExpired()
        val session = pendingRequests.values.firstOrNull {
            it.cloneId == cloneId && it.requestCode == requestCode
        } ?: return null
        val resultUri = data?.data
        if (resultUri != null && session.callback?.matches(resultUri) != true) return null
        remove(session)
        return session
    }

    /** Compatibility overload retained for the existing registry unit tests. */
    @Synchronized
    fun consumeActivityResult(requestCode: Int, data: Intent?): Boolean =
        consumeActivityResult("test", requestCode, data) != null

    @Synchronized
    fun consumeNewIntent(intent: Intent): PendingSession? {
        return consumeNewIntent(null, intent)
    }

    @Synchronized
    fun consumeNewIntent(cloneId: String?, intent: Intent): PendingSession? {
        purgeExpired()
        val resultUri = intent.data ?: return null
        val session = pendingRequests.values.firstOrNull {
            (cloneId == null || it.cloneId == cloneId) && it.callback?.matches(resultUri) == true
        } ?: return null
        remove(session)
        return session
    }

    @Synchronized
    fun pendingDiagnostics(): List<String> {
        purgeExpired()
        return pendingRequests.values.sortedBy { it.createdAt }.map {
            "cloneId=${it.cloneId}, activity=${it.targetActivityClassName}, " +
                "requestCode=${it.requestCode}, callback=${it.callback?.describe() ?: "unknown"}, " +
                "ageMs=${(clock() - it.createdAt).coerceAtLeast(0L)}"
        }
    }

    @Synchronized
    fun clear() {
        pendingRequests.values.toList().forEach(::remove)
        preferences?.edit()?.clear()?.apply()
    }

    private fun extractCallbackIdentity(intent: Intent): CallbackIdentity? =
        intent.data?.getQueryParameter("redirect_uri")?.let(Uri::parse)?.let {
            CallbackIdentity(it.scheme, it.host, it.path)
        }

    private fun purgeExpired() {
        pendingRequests.values.filter { clock() - it.createdAt >= SESSION_TIMEOUT_MS }
            .forEach(::remove)
    }

    private fun key(session: PendingSession): String = "${session.cloneId}:${session.requestCode}"

    private fun remove(session: PendingSession) {
        pendingRequests.remove(key(session))
        preferences?.edit()?.remove(RECORD_PREFIX + encode(key(session)))?.apply()
    }

    private fun persist(session: PendingSession) {
        val callback = session.callback
        val value = listOf(
            session.cloneId, session.targetActivityClassName, session.requestCode.toString(),
            callback?.scheme.orEmpty(), callback?.host.orEmpty(), callback?.path.orEmpty(),
            session.createdAt.toString()
        ).joinToString(SEPARATOR)
        preferences?.edit()?.putString(RECORD_PREFIX + encode(key(session)), value)?.apply()
    }

    private fun restorePersistedSessions() {
        preferences?.all?.forEach { (name, raw) ->
            if (!name.startsWith(RECORD_PREFIX) || raw !is String) return@forEach
            val fields = raw.split(SEPARATOR)
            if (fields.size != 7) return@forEach
            val session = runCatching {
                val scheme = fields[3].ifBlank { null }
                val host = fields[4].ifBlank { null }
                val path = fields[5].ifBlank { null }
                PendingSession(
                    fields[0], fields[1], fields[2].toInt(),
                    if (scheme == null && host == null && path == null) null
                    else CallbackIdentity(scheme, host, path),
                    fields[6].toLong()
                )
            }.getOrNull() ?: return@forEach
            pendingRequests[key(session)] = session
        }
    }

    private fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP
    )
}
