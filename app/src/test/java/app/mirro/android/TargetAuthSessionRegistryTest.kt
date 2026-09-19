package app.mirro.android

import android.content.Intent
import android.net.Uri
import org.robolectric.RuntimeEnvironment
import app.mirro.android.ui.container.TargetAuthSessionRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TargetAuthSessionRegistryTest {

    private val registry = TargetAuthSessionRegistry()

    @Test
    fun `matching OAuth callback is accepted once`() {
        registry.remember(
            requestCode = 42,
            intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://auth.openai.com/api/accounts/authorize" +
                        "?redirect_uri=com.openai.chatgpt%3A%2F%2Fauth.openai.com%2Fandroid%2Fcallback"
                )
            )
        )

        val callback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("com.openai.chatgpt://auth.openai.com/android/callback?code=redacted")
        )

        assertTrue(registry.consumeActivityResult(42, callback))
        assertFalse("A callback must not be replayable", registry.consumeActivityResult(42, callback))
    }

    @Test
    fun `callback for another host is rejected`() {
        registry.remember(
            requestCode = 7,
            intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://auth.openai.com/authorize" +
                        "?redirect_uri=com.openai.chatgpt%3A%2F%2Fauth.openai.com%2Fandroid%2Fcallback"
                )
            )
        )

        val callback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("com.openai.chatgpt://evil.example/android/callback?code=redacted")
        )

        assertFalse(registry.consumeActivityResult(7, callback))
    }

    @Test
    fun `mismatched callback does not consume the valid pending request`() {
        registry.remember(
            requestCode = 8,
            intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://auth.openai.com/authorize" +
                        "?redirect_uri=com.openai.chatgpt%3A%2F%2Fauth.openai.com%2Fandroid%2Fcallback"
                )
            )
        )

        assertFalse(
            registry.consumeActivityResult(
                8,
                Intent(Intent.ACTION_VIEW, Uri.parse("com.openai.chatgpt://evil.example/android/callback"))
            )
        )
        assertTrue(
            registry.consumeActivityResult(
                8,
                Intent(Intent.ACTION_VIEW, Uri.parse("com.openai.chatgpt://auth.openai.com/android/callback"))
            )
        )
    }

    @Test
    fun `canceled browser request is forwarded without retaining data`() {
        registry.remember(
            requestCode = 9,
            intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://auth.openai.com/authorize" +
                        "?redirect_uri=com.openai.chatgpt%3A%2F%2Fauth.openai.com%2Fandroid%2Fcallback"
                )
            )
        )

        assertTrue(registry.consumeActivityResult(9, null))
        assertFalse(registry.consumeActivityResult(9, null))
    }

    @Test
    fun `wrong clone cannot consume another clone callback`() {
        registry.remember(
            cloneId = "clone-a",
            targetActivityClassName = "TargetAuthActivity",
            requestCode = 12,
            intent = authorizationIntent()
        )

        assertFalse(
            registry.consumeActivityResult(
                cloneId = "clone-b",
                requestCode = 12,
                data = callbackIntent()
            ) != null
        )
        assertTrue(
            registry.consumeActivityResult(
                cloneId = "clone-a",
                requestCode = 12,
                data = callbackIntent()
            ) != null
        )
    }

    @Test
    fun `pending routing metadata survives registry recreation without secrets`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mirro_auth_routing", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        val first = TargetAuthSessionRegistry(context)
        first.remember("clone-persisted", "TargetAuthActivity", 13, authorizationIntent())

        val restored = TargetAuthSessionRegistry(context)
        assertTrue(restored.pendingDiagnostics().single().contains("scheme=com.openai.chatgpt"))
        assertTrue(restored.pendingDiagnostics().single().contains("path=/android/callback"))
        assertFalse(restored.pendingDiagnostics().single().contains("code="))
        assertTrue(restored.consumeNewIntent("clone-persisted", callbackIntent()) != null)
    }

    @Test
    fun `expired callback session is rejected`() {
        var now = 1_000L
        val expiring = TargetAuthSessionRegistry(clock = { now })
        expiring.remember("clone-expiring", "TargetAuthActivity", 14, authorizationIntent())
        now += TargetAuthSessionRegistry.SESSION_TIMEOUT_MS

        assertFalse(expiring.consumeNewIntent(callbackIntent()) != null)
    }

    private fun authorizationIntent() = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            "https://auth.openai.com/authorize" +
                "?redirect_uri=com.openai.chatgpt%3A%2F%2Fauth.openai.com%2Fandroid%2Fcallback"
        )
    )

    private fun callbackIntent() = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("com.openai.chatgpt://auth.openai.com/android/callback?code=redacted")
    )
}
