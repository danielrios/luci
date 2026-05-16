package app.luci.finance.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Custom AuthenticationEntryPoint for M2M JWT authentication failures.
 * Returns a minimal ProblemDetail-shaped 401 body with no stack trace,
 * no JWT internals, no hint about which validator stage failed.
 * (FR-018, FR-022, Gap-3)
 */
@Component
class M2MAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val body = mapOf(
            "status" to 401,
            "title" to "Unauthorized",
        )

        objectMapper.writeValue(response.outputStream, body)
    }
}
