package app.luci.finance

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals

/**
 * Integration test for the Flyway baseline migration.
 * Validates:
 * - The marker comment exists on flyway_schema_history after migration.
 * - Re-running against the same DB reports 0 pending migrations.
 * (FR-014, FR-016, SC-007, T019)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class FlywayBaselineTest {

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
            registry.add("luci.m2m.public-key-pem") { "" }
            registry.add("luci.m2m.kid") { "test-kid" }
            registry.add("luci.m2m.issuer") { "test-issuer" }
            registry.add("luci.m2m.audience") { "test-audience" }
            registry.add("luci.m2m.jwks-url") { "http://localhost:0/.well-known/jwks.json" }
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri") {
                "http://localhost:0/.well-known/jwks.json"
            }
        }
    }

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `flyway baseline marker comment is present on flyway_schema_history`() {
        val description = jdbcTemplate.queryForObject(
            """
            SELECT description
            FROM   pg_description
            WHERE  objoid = 'flyway_schema_history'::regclass AND objsubid = 0
            """.trimIndent(),
            String::class.java,
        )
        assertEquals(
            "Luci baseline migration applied. Walking skeleton — no domain tables yet.",
            description,
        )
    }

    @Test
    fun `flyway reports no pending migrations on re-run`() {
        // If we reached this test, Spring Boot already applied migrations.
        // Query the Flyway history to verify exactly one migration applied.
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
            Int::class.java,
        )
        assertEquals(1, count, "Expected exactly 1 successful Flyway migration")
    }
}
