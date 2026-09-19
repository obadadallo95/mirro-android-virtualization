package app.mirro.android.ui.container

import android.content.Intent
import android.net.Uri

/**
 * Local, in-memory routing state for browser/credential callbacks belonging to one target
 * Activity host. No authorization code, token, cookie, or URL query is retained.
 */
internal class TargetAuthSessionRegistry {

    private data class CallbackIdentity(
        val scheme: String?,
        val host: String?,
        val path: String?
    ) {
        fun matches(uri: Uri): Boolean =
            scheme == uri.scheme &&
                host == uri.host &&
                path == uri.path
    }

    private data class PendingRequest(
        val callback: CallbackIdentity?
    )

    private val pendingRequests = mutableMapOf<Int, PendingRequest>()

    @Synchronized
    fun remember(requestCode: Int, intent: Intent) {
        if (requestCode < 0) return
        pendingRequests[requestCode] = PendingRequest(
            callback = intent.data
                ?.getQueryParameter("redirect_uri")
                ?.let(Uri::parse)
                ?.let { uri -> callbackIdentity(uri) }
        )
    }

    @Synchronized
    fun forget(requestCode: Int) {
        pendingRequests.remove(requestCode)
    }

    /**
     * Returns true only for a request that was initiated by the target Activity host. A null
     * result is a normal user cancellation and is therefore allowed. For OAuth results, a
     * non-null URI must match the redirect identity captured when the browser was launched.
     */
    @Synchronized
    fun consumeActivityResult(requestCode: Int, data: Intent?): Boolean {
        val request = pendingRequests[requestCode] ?: return false
        val callback = request.callback
        val resultUri = data?.data
        if (callback == null || resultUri == null) {
            pendingRequests.remove(requestCode)
            return true
        }
        if (!callback.matches(resultUri)) return false
        pendingRequests.remove(requestCode)
        return true
    }

    /**
     * Consumes a new-intent callback only when it matches one of the pending OAuth redirects.
     */
    @Synchronized
    fun consumeNewIntent(intent: Intent): Boolean {
        val resultUri = intent.data ?: return false
        val matchingRequestCode = pendingRequests.entries.firstOrNull { (_, request) ->
            request.callback?.matches(resultUri) == true
        }?.key ?: return false
        pendingRequests.remove(matchingRequestCode)
        return true
    }

    @Synchronized
    fun clear() {
        pendingRequests.clear()
    }

    private fun callbackIdentity(uri: Uri): CallbackIdentity {
        // Only the non-secret routing identity is retained. The scheme, host, and path must match
        // exactly what the target requested; query parameters are intentionally discarded.
        return CallbackIdentity(
            scheme = uri.scheme,
            host = uri.host,
            path = uri.path
        )
    }
}
