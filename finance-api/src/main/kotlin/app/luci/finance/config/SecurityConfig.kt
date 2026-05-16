package app.luci.finance.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import java.time.Duration

/**
 * Spring Security configuration for the walking skeleton.
 *
 * - GET /health, GET /metrics, GET /.well-known/** -> unauthenticated
 * - POST /v1/internal/* -> requires M2M JWT bearer token
 * - POST /v1/users/** -> reserved for future domain endpoints (forward-compat C3)
 * - All other requests -> denied
 *
 * JWT validation: NimbusJwtDecoder with in-memory JWKSet, audience validator,
 * and 60s clock-skew leeway. (R-3, FR-018, FR-022, FR-023)

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwkSet: JWKSet,
    private val authenticationEntryPoint: app.luci.finance.security.M2MAuthenticationEntryPoint,
) {

    @Value("\${luci.m2m.audience:finance-api.luci.app}")
    private lateinit var audience: String

    @Bean
    @Profile("local")
    fun localSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val decoder = buildJwtDecoder()

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health", "/metrics", "/.well-known/**").permitAll()
                    .requestMatchers("/v1/internal/**", "/v1/users/**").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { jwt -> jwt.decoder(decoder) }
                    .authenticationEntryPoint(authenticationEntryPoint)
            }
            .build()
    }

    private fun buildJwtDecoder(): NimbusJwtDecoder {
        val jwkSource = ImmutableJWKSet<SecurityContext>(jwkSet)
        val decoder = NimbusJwtDecoder.withJwkSource(jwkSource).build()

        // Validators: timestamp (60s leeway) + audience
        val timestampValidator = JwtTimestampValidator(Duration.ofSeconds(60))
        val audienceValidator: OAuth2TokenValidator<Jwt> = JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
        ) { audiences -> audiences != null && audiences.contains(audience) }

        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(timestampValidator, audienceValidator),
        )

        return decoder
    }

    /**
     * Prod profile security chain — same JWT validation as local, plus IP-based
     * access control restricting /.well-known/**, /v1/internal/**, /v1/users/**, and
     * /metrics to the platform-internal network.
     * CIDR sourced from LUCI_INTERNAL_CIDR env var. (FR-019, FR-020, FR-026c, T047)
     **/
    @Bean
    @Profile("prod")
    fun prodSecurityFilterChain(
        http: HttpSecurity,
        @Value("\${luci.internal-cidr:127.0.0.1/32}") internalCidr: String,
    ): SecurityFilterChain {
        val decoder = buildJwtDecoder()

        return http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health").permitAll()
                    .requestMatchers("/.well-known/**", "/metrics")
                        .access { _, context ->
                            val addr = context.request.remoteAddr
                            org.springframework.security.authorization.AuthorizationDecision(
                                matchesCidr(addr, internalCidr),
                            )
                        }
                    .requestMatchers("/v1/internal/**", "/v1/users/**").authenticated()
                    .anyRequest().denyAll()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { jwt -> jwt.decoder(decoder) }
                    .authenticationEntryPoint(authenticationEntryPoint)
            }
            .build()
    }

    private fun matchesCidr(addr: String, cidr: String): Boolean {
        // Simple CIDR matching for common cases
        if (cidr == "127.0.0.1/32") return addr == "127.0.0.1" || addr == "0:0:0:0:0:0:0:1"
        if (cidr.endsWith("/8")) {
            val prefix = cidr.substringBefore("/").substringBefore(".")
            return addr.startsWith("$prefix.")
        }
        // Fallback: exact match
        return addr == cidr.substringBefore("/")
    }
}

