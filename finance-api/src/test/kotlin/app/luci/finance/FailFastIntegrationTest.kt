package app.luci.finance

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.SpringApplication
import org.springframework.boot.WebApplicationType
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * Fail-fast integration test for prod profile.
 * Validates that missing required env vars cause ApplicationContext to fail at boot.
 * (E3, Gap-2, story 3 AS-4, T044)
 */
@Testcontainers
class FailFastIntegrationTest {

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
    }

    @Test
    fun `prod profile fails at boot when LUCI_M2M_PUBLIC_KEY_PEM is blank`() {
        val app = SpringApplication(FinanceApiApplication::class.java)
        app.webApplicationType = WebApplicationType.SERVLET

        app.setDefaultProperties(
            mapOf(
                "spring.profiles.active" to "prod",
                "spring.datasource.url" to postgres.jdbcUrl,
                "spring.datasource.username" to postgres.username,
                "spring.datasource.password" to postgres.password,
                "spring.data.redis.url" to "redis://${redis.host}:${redis.firstMappedPort}",
                "luci.m2m.public-key-pem" to "",
                "luci.m2m.kid" to "test-kid",
                "luci.m2m.issuer" to "test-issuer",
                "luci.m2m.audience" to "test-audience",
                "luci.m2m.jwks-url" to "http://localhost:0/.well-known/jwks.json",
                "luci.internal-cidr" to "127.0.0.1/32",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri" to
                    "http://localhost:0/.well-known/jwks.json",
            ),
        )

        val ex = assertThrows<Exception> {
            app.run()
        }

        // Root cause should mention LUCI_M2M_PUBLIC_KEY_PEM
        val rootMessage = generateSequence(ex) { it.cause }.last().message ?: ""
        assert(rootMessage.contains("LUCI_M2M_PUBLIC_KEY_PEM") || rootMessage.contains("public")) {
            "Expected boot failure to mention LUCI_M2M_PUBLIC_KEY_PEM, got: $rootMessage"
        }
    }
}
