package app.mirro.android

import android.content.Intent
import android.net.Uri
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
}
