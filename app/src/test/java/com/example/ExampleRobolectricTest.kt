package com.example
 
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    assertEquals("Live Stream Hub", appName)
  }

  @Test
  fun `verify integrity checker in robolectric test`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val integrityResult = com.example.data.AppIntegrityChecker.verifyIntegrity(context, forceKill = false)
    assertNotNull(integrityResult)
  }

  @Test
  fun `verify local key rotation persists key in preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testUrl = "https://example.com/premium/feed1.m3u8"
    
    // Test base encrypt/decrypt with default/unrotated key
    val encryptedOrig = com.example.data.StreamDecryptionUtility.encrypt(testUrl, context)
    assertTrue(encryptedOrig.startsWith("encrypted://"))
    val decryptedOrig = com.example.data.StreamDecryptionUtility.decrypt(encryptedOrig, context)
    assertEquals(testUrl, decryptedOrig)

    // Test dynamic rotation
    val newKey = com.example.data.StreamDecryptionUtility.rotateKeyLocally(context)
    assertNotNull(newKey)
    assertTrue(newKey.isNotEmpty())
    
    // Encrypt with the newly rotated key
    val encryptedNew = com.example.data.StreamDecryptionUtility.encrypt(testUrl, context)
    assertTrue(encryptedNew.startsWith("encrypted://"))
    val decryptedNew = com.example.data.StreamDecryptionUtility.decrypt(encryptedNew, context)
    assertEquals(testUrl, decryptedNew)
  }
}
