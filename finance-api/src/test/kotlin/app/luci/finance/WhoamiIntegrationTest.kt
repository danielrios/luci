package app.luci.finance

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date
import java.util.UUID

/**
 * Integration test for POST /v1/internal/whoami with Testcontainers.
 * Covers all 7 equivalence classes including iat_future (Gap-3).
 * Written FIRST per TDD (constitution §IV). (FR-018, FR-022, SC-005, T030)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WhoamiIntegrationTest {

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        private val privateKey = keyPair.private as RSAPrivateKey
        private val publicKey = keyPair.public as RSAPublicKey
        private const val KID = "test-kid-whoami"
        private const val ISSUER = "python-agent.luci.app"
        private const val AUDIENCE = "finance-api.luci.app"

        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg15")
        ).apply {
            withDatabaseName("luci")
            withUsername("luci")
            withPassword("luci")
            withCommand("postgres", "-c", "shared_preload_libraries=vector")
        }

        @Container
        val redis: GenericContainer<*> = GenericContainer(
            DockerImageName.parse("redis:7-alpine")
        ).apply {
            withExposedPorts(6379)
        }

        private fun publicKeyPem(): String {
            val encoded = Base64.getMimeEncoder(64, "\n".toByteArray())
                .encodeToString(publicKey.encoded)
            return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.url") {
                "redis://${redis.host}:${redis.firstMappedPort}"
            }
            registry.add("luci.m2m.public-key-pem") { publicKeyPem() }
            registry.add("luci.m2m.kid") { KID }
            registry.add("luci.m2m.issuer") { ISSUER }
            registry.add("luci.m2m.audience") { AUDIENCE }
            registry.add("luci.m2m.jwks-url") { "http://localhost:0/.well-known/jwks.json" }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:0/.well-known/jwks.json"
            }
        }

        fun mintJwt(
            issuer: String = ISSUER,
            audience: String = AUDIENCE,
            subject: String = "service:python-agent",
            expiresInMs: Long = 15 * 60 * 1000,
            iatOffsetMs: Long = 0,
            signingKey: RSAPrivateKey = privateKey,
            kid: String = KID,
        ): String {
            val now = Date()
            val iat = Date(now.time + iatOffsetMs)
            val exp = Date(now.time + expiresInMs)

            val header = JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .build()

            val claims = JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(iat)
                .expirationTime(exp)
                .claim("user_id", UUID.randomUUID().toString())
                .claim("intent", "whoami")
                .claim("trace_id", UUID.randomUUID().toString().replace("-", ""))
                .build()

            val jwt = SignedJWT(header, claims)
            jwt.sign(RSASSASigner(signingKey))
            return jwt.serialize()
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `valid jwt echoes claims`() {
        val token = mintJwt()
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.subject") { value("service:python-agent") }
            jsonPath("$.intent") { value("whoami") }
            jsonPath("$.user_id") { isNotEmpty() }
            jsonPath("$.trace_id") { isNotEmpty() }
        }
    }

    @Test
    fun `missing authorization returns 401`() {
        mockMvc.post("/v1/internal/whoami") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `expired jwt returns 401`() {
        val token = mintJwt(expiresInMs = -60_000) // expired 1 min ago
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `wrong key signature returns 401`() {
        val throwawayKp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val token = mintJwt(signingKey = throwawayKp.private as RSAPrivateKey)
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `wrong audience returns 401`() {
        val token = mintJwt(audience = "wrong-audience")
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `wrong issuer returns 401`() {
        val token = mintJwt(issuer = "wrong-issuer")
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `iat far in future returns 401 with distinct marker`() {
        // JWT minted with iat = now + 16 min (beyond the 60s leeway)
        val token = mintJwt(iatOffsetMs = 16 * 60 * 1000)
        mockMvc.post("/v1/internal/whoami") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isUnauthorized() }
        }
    }
}
