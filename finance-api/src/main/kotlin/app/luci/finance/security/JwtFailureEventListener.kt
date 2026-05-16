package app.luci.finance.security

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.stereotype.Component

/**
 * Listens for JWT authentication failure events and logs structured markers.
 *
 * The 5 distinct markers (data-model.md §4):
 * - m2m.jwt.missing         — no Authorization header
 * - m2m.jwt.invalid_signature — unknown kid or wrong key
 * - m2m.jwt.expired          — exp + leeway < now
 * - m2m.jwt.audience_mismatch — aud does not match
 * - m2m.jwt.iat_future       — iat > now + leeway (Gap-3, distinct from expired)
 *
 * (FR-018, FR-022, Gap-3, T041)
 */
@Component
class JwtFailureEventListener : ApplicationListener<AbstractAuthenticationFailureEvent> {

    private val logger = LoggerFactory.getLogger(JwtFailureEventListener::class.java)

    override fun onApplicationEvent(event: AbstractAuthenticationFailureEvent) {
        val exception = event.exception
        val marker = classifyFailure(exception)

        logger.warn(
            "M2M JWT authentication failed | event={} | exception_type={} | message={}",
            marker,
            exception.javaClass.simpleName,
            exception.message,
        )
    }

    private fun classifyFailure(exception: AuthenticationException): String {
        if (exception is OAuth2AuthenticationException) {
            val description = exception.error.description?.lowercase() ?: ""
            val errorCode = exception.error.errorCode.lowercase()

            return when {
                // iat in the future (Gap-3 — distinct from expired)
                description.contains("iat") && description.contains("future") -> "m2m.jwt.iat_future"
                description.contains("not before") -> "m2m.jwt.iat_future"

                // Expired token
                description.contains("expired") || errorCode == "invalid_token" &&
                    description.contains("exp") -> "m2m.jwt.expired"

                // Audience mismatch
                description.contains("audience") || description.contains("aud") -> "m2m.jwt.audience_mismatch"

                // Invalid signature / unknown kid
                description.contains("signature") || description.contains("kid") ||
                    description.contains("key") -> "m2m.jwt.invalid_signature"

                // Default for OAuth2 errors we can't classify
                else -> "m2m.jwt.invalid_signature"
            }
        }

        // Non-OAuth2 auth failure — likely missing Authorization header
        return "m2m.jwt.missing"
    }
}
