package app.luci.finance.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Loads the M2M RSA public key from the environment and constructs an in-memory JWKSet.
 *
 * If LUCI_M2M_PUBLIC_KEY_PEM is absent or unparseable, throws IllegalStateException
 * so Spring's ApplicationContext fails at boot with a clear error. (Gap-2, FR-019, FR-021)
 */
@Configuration
class M2MKeyConfig {

    @Value("\${luci.m2m.public-key-pem:}")
    private lateinit var publicKeyPem: String

    @Value("\${luci.m2m.kid:}")
    private lateinit var kid: String

    @Bean
    fun m2mJwkSet(): JWKSet {
        require(publicKeyPem.isNotBlank()) {
            "LUCI_M2M_PUBLIC_KEY_PEM is not configured. " +
                "Run 'make bootstrap' to generate the M2M keypair."
        }
        require(kid.isNotBlank()) {
            "LUCI_M2M_KID is not configured. " +
                "Run 'make bootstrap' to generate the M2M keypair."
        }

        val rsaPublicKey = parsePublicKey(publicKeyPem)
        val jwk = RSAKey.Builder(rsaPublicKey)
            .keyID(kid)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .build()

        return JWKSet(jwk)
    }

    private fun parsePublicKey(pem: String): RSAPublicKey {
        try {
            val cleaned = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")

            val decoded = Base64.getDecoder().decode(cleaned)
            val keySpec = X509EncodedKeySpec(decoded)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePublic(keySpec) as RSAPublicKey
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to parse LUCI_M2M_PUBLIC_KEY_PEM: ${e.message}",
                e,
            )
        }
    }
}
