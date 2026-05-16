package app.luci.finance

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit test for M2MKeyConfig key-loading logic.
 * Validates the JWKSet contains exactly one key with correct properties.
 * Complements T044's integration assertion. (T059)
 */
class M2MKeyConfigUnitTest {

    private fun generatePublicKeyPem(): String {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encoded = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(kp.public.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
    }

    @Test
    fun `valid PEM produces JWKSet with correct properties`() {
        val pem = generatePublicKeyPem()
        val kid = "test-kid-123"

        val config = app.luci.finance.config.M2MKeyConfig()
        // Use reflection to set the private fields for unit testing
        val publicKeyPemField = config.javaClass.getDeclaredField("publicKeyPem")
        publicKeyPemField.isAccessible = true
        publicKeyPemField.set(config, pem)

        val kidField = config.javaClass.getDeclaredField("kid")
        kidField.isAccessible = true
        kidField.set(config, kid)

        val jwkSet = config.m2mJwkSet()
        assertEquals(1, jwkSet.keys.size, "Expected exactly one key in JWKSet")

        val jwk = jwkSet.keys.first()
        assertEquals("RSA", jwk.keyType.value)
        assertEquals(KeyUse.SIGNATURE, jwk.keyUse)
        assertEquals(JWSAlgorithm.RS256, jwk.algorithm)
        assertEquals(kid, jwk.keyID)
    }

    @Test
    fun `blank PEM throws IllegalStateException`() {
        val config = app.luci.finance.config.M2MKeyConfig()
        val publicKeyPemField = config.javaClass.getDeclaredField("publicKeyPem")
        publicKeyPemField.isAccessible = true
        publicKeyPemField.set(config, "")

        val kidField = config.javaClass.getDeclaredField("kid")
        kidField.isAccessible = true
        kidField.set(config, "test-kid")

        assertThrows<IllegalStateException> {
            config.m2mJwkSet()
        }
    }

    @Test
    fun `malformed PEM throws IllegalStateException`() {
        val config = app.luci.finance.config.M2MKeyConfig()
        val publicKeyPemField = config.javaClass.getDeclaredField("publicKeyPem")
        publicKeyPemField.isAccessible = true
        publicKeyPemField.set(config, "not-a-real-pem")

        val kidField = config.javaClass.getDeclaredField("kid")
        kidField.isAccessible = true
        kidField.set(config, "test-kid")

        assertThrows<IllegalStateException> {
            config.m2mJwkSet()
        }
    }
}
