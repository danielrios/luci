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

/**
 * Integration test for GET /health with Testcontainers.
 * Validates Actuator shape, db=UP, redis=UP, and DOWN propagation within 10s.
 * (FR-025, SC-008, T018)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HealthEndpointIntegrationTest {

    companion object {
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

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.url") {
                "redis://${redis.host}:${redis.firstMappedPort}"
            }
            // M2M JWT config — not relevant for health tests but needed for boot
            registry.add("luci.m2m.public-key-pem") { "" }
            registry.add("luci.m2m.kid") { "test-kid" }
            registry.add("luci.m2m.issuer") { "test-issuer" }
            registry.add("luci.m2m.audience") { "test-audience" }
            registry.add("luci.m2m.jwks-url") { "http://localhost:0/.well-known/jwks.json" }
            // Disable resource server auto-config for health test
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:0/.well-known/jwks.json"
            }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns 200 with db UP and redis UP`() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components.db.status") { value("UP") }
                jsonPath("$.components.redis.status") { value("UP") }
            }
    }

    @Test
    fun `health returns 503 with redis DOWN when container stops`() {
        redis.stop()
        Thread.sleep(2000) // Give Spring time to detect the failure

        mockMvc.get("/health")
            .andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.components.redis.status") { value("DOWN") }
            }
    }
}
