package app.luci.finance.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Propagates request-scope context into the MDC for structured logging.
 * Fields: traceId, service, profile.
 * (R-9, FR-026a)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class LoggingContextFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            // Propagate trace-id from header if present, else generate one
            val traceId = request.getHeader("X-Trace-Id")
                ?: request.getHeader("traceparent")?.split("-")?.getOrNull(1)
                ?: java.util.UUID.randomUUID().toString().replace("-", "")

            MDC.put("traceId", traceId)
            MDC.put("service", "finance-api")
            MDC.put("profile", System.getenv("SPRING_PROFILES_ACTIVE") ?: "unknown")

            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
