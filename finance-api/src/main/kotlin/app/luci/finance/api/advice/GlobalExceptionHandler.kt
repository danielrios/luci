package app.luci.finance.api.advice

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Global exception handler — no raw stack traces in responses.
 * Spring Security's 401 handling is delegated to M2MAuthenticationEntryPoint (T041).
 * (FR-018, constitution anti-vibe: no raw stack-trace returns)
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ProblemDetail {
        val pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred.",
        )
        pd.title = "Internal Server Error"
        return pd
    }
}
