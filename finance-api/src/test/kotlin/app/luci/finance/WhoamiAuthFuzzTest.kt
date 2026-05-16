package app.luci.finance

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
import java.util.Random
import java.util.UUID
import java.util.stream.Stream

/**
 * Parametrized fuzz test for M2M JWT auth (Gap-6, SC-005 "100-request synthetic mix").
 * Generates 100 cases distributed across 7 equivalence classes with a fixed seed
 * for reproducibility. Asserts zero false positives and zero false negatives.
 * Total runtime ≤ 30 s under Testcontainers. (T031)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class WhoamiAuthFuzzTest {

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        private val privateKey = keyPair.private as RSAPrivateKey
        private val publicKey = keyPair.public as RSAPublicKey
        private const val KID = "fuzz-test-kid"
        private const val ISSUER = "python-agent.luci.app"
        private const val AUDIENCE = "finance-api.luci.app"
        private const val SEED = 20260515L

        private val throwawayKeyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

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

        /**
         * Equivalence classes:
         * 0 = valid (≈14 cases)
         * 1 = expired
         * 2 = wrong key
         * 3 = wrong audience
         * 4 = wrong issuer
         * 5 = iat future
         * 6 = missing auth (no bearer header)
         */
        data class FuzzCase(
            val index: Int,
            val eqClass: Int,
            val expectedValid: Boolean,
            val token: String?,
            val description: String,
        )

        @JvmStatic
        fun fuzzCases(): Stream<FuzzCase> {
            val rng = Random(SEED)
            val cases = mutableListOf<FuzzCase>()

            for (i in 0 until 100) {
                val eqClass = if (i < 14) 0 else (rng.nextInt(6) + 1)
                val case = when (eqClass) {
                    0 -> FuzzCase(
                        i, 0, true,
                        mintFuzzJwt(),
                        "valid-$i",
                    )
                    1 -> FuzzCase(
                        i, 1, false,
                        mintFuzzJwt(expiresInMs = -120_000),
                        "expired-$i",
                    )
                    2 -> FuzzCase(
                        i, 2, false,
                        mintFuzzJwt(signingKey = throwawayKeyPair.private as RSAPrivateKey),
                        "wrong-key-$i",
                    )
                    3 -> FuzzCase(
                        i, 3, false,
                        mintFuzzJwt(audience = "wrong-aud-${rng.nextInt()}"),
                        "wrong-audience-$i",
                    )
                    4 -> FuzzCase(
                        i, 4, false,
                        mintFuzzJwt(issuer = "wrong-iss-${rng.nextInt()}"),
                        "wrong-issuer-$i",
                    )
                    5 -> FuzzCase(
                        i, 5, false,
                        mintFuzzJwt(iatOffsetMs = 20 * 60 * 1000),
                        "iat-future-$i",
                    )
                    6 -> FuzzCase(
                        i, 6, false,
                        null,
                        "missing-auth-$i",
                    )
                    else -> throw IllegalStateException("unreachable")
                }
                cases.add(case)
            }
            return cases.stream()
        }

        private fun mintFuzzJwt(
            issuer: String = ISSUER,
            audience: String = AUDIENCE,
            expiresInMs: Long = 15 * 60 * 1000,
            iatOffsetMs: Long = 0,
            signingKey: RSAPrivateKey = privateKey,
        ): String {
            val now = Date()
            val iat = Date(now.time + iatOffsetMs)
            val exp = Date(now.time + expiresInMs)

            val header = JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KID)
                .build()

            val claims = JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("service:python-agent")
                .issueTime(iat)
                .expirationTime(exp)
                .claim("user_id", UUID.randomUUID().toString())
                .claim("intent", "fuzz")
                .claim("trace_id", UUID.randomUUID().toString().replace("-", ""))
                .build()

            val jwt = SignedJWT(header, claims)
            jwt.sign(RSASSASigner(signingKey))
            return jwt.serialize()
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fuzzCases")
    fun `fuzz M2M auth across 7 equivalence classes`(case: FuzzCase) {
        val result = if (case.token != null) {
            mockMvc.post("/v1/internal/whoami") {
                header("Authorization", "Bearer ${case.token}")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        } else {
            mockMvc.post("/v1/internal/whoami") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn()
        }

        val status = result.response.status

        if (case.expectedValid) {
            assertTrue(status == 200, "Case ${case.description}: expected 200 but got $status")
        } else {
            assertTrue(status == 401, "Case ${case.description}: expected 401 but got $status")
        }
    }
}
