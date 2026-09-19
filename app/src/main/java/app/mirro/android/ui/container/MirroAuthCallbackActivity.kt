package app.mirro.android.ui.container

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Minimal receiver for the observed ChatGPT custom-scheme callback.
 * The pending-session coordinator performs the actual clone and route validation.
 */
class MirroAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val accepted = ContainerAuthCallbackRouter.dispatch(intent)
        if (!accepted) Log.w(TAG, "Rejected external auth callback")
        finish()
    }

    companion object {
        private const val TAG = "MirroAuthCallback"
    }
}
