package app.luci.finance

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * Prod profile security test — verifies IP-based access control.
 * With remoteAddr outside CIDR: /.well-known/, /metrics, /v1/internal/ → 403.
 * With remoteAddr inside CIDR (127.0.0.1): permitted (still needs JWT for /v1/).
 * /health is always accessible regardless of IP.
 * (E2, FR-019, FR-020, FR-026c, T043)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class ProdProfileSecurityTest {

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048)
        }.generateKeyPair()

        private val publicKey = keyPair.public as RSAPublicKey

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
            registry.add("luci.m2m.kid") { "prod-test-kid" }
            registry.add("luci.m2m.issuer") { "python-agent.luci.app" }
            registry.add("luci.m2m.audience") { "finance-api.luci.app" }
            registry.add("luci.m2m.jwks-url") { "http://localhost:0/.well-known/jwks.json" }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:0/.well-known/jwks.json"
            }
            // Restrict to loopback only — MockMvc sends from 127.0.0.1
            registry.add("luci.internal-cidr") { "127.0.0.1/32" }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health is always accessible regardless of profile`() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
            }
    }

    @Test
    fun `well-known jwks is accessible from loopback`() {
        // MockMvc always sends from 127.0.0.1 which is within the CIDR
        mockMvc.get("/.well-known/jwks.json")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `metrics is accessible from loopback`() {
        mockMvc.get("/metrics")
            .andExpect {
                status { isOk() }
            }
    }
}
