package app.mirro.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mirro", appName)
  }

  @Test
  fun `read string in arabic locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = android.content.res.Configuration(context.resources.configuration)
    config.setLocale(java.util.Locale.forLanguageTag("ar"))
    val localizedContext = context.createConfigurationContext(config)
    val appName = localizedContext.getString(R.string.app_name)
    val tagline = localizedContext.getString(R.string.app_tagline)
    val backText = localizedContext.getString(R.string.back)
    assertEquals("Mirro", appName)
    assertEquals("نفس التطبيقات. إمكانيات أكثر.", tagline)
    assertEquals("رجوع", backText)
  }
}
